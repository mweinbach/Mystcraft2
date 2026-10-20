/*
 * Extreme Reactors 1.19.2 port - visual effects
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.effects.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import it.zerono.mods.extremereactors.gamecontent.multiblock.reactor.part.ReactorControllerEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import ports.extremereactors.effects.EffectsConfig;

/**
 * Draws, from a reactor controller, the alarm lamps on its face and, from the reactor's first controller only,
 * the Cherenkov glow around the reactor's fuel columns.
 */
final class ReactorControllerRenderer
        implements BlockEntityRenderer<ReactorControllerEntity> {

    ReactorControllerRenderer(final BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(final ReactorControllerEntity controller, final float partialTicks, final PoseStack stack,
                       final MultiBufferSource buffers, final int packedLight, final int packedOverlay) {

        final Level level = controller.getLevel();

        if (null == level || !controller.isMachineAssembled()) {
            return;
        }

        final ReactorEffects effects = controller.evalOnController(MachineEffects::get, null);

        if (null == effects) {
            return;
        }

        final EffectsConfig config = EffectsConfig.CLIENT;
        final BlockPos origin = controller.getBlockPos();
        final float time = level.getGameTime() + partialTicks;

        if (config.isOn(config.alarmLamps)) {

            final int lamp = effects.getLampColor(partialTicks);

            if (-1 != lamp) {
                controller.getOutwardDirection().ifPresent(face ->
                        renderLamps(buffers.getBuffer(GlowRenderType.GLOW), stack.last().pose(), face, lamp));
            }
        }

        if (config.isOn(config.cherenkovGlow) && effects.isInteriorVisible() && effects.isPrimaryController(origin)) {

            final float glow = effects.getGlow(partialTicks) * config.glowStrength.get().floatValue();

            if (glow > 0.005f) {
                renderGlow(buffers.getBuffer(GlowRenderType.GLOW), stack.last().pose(), effects, origin, glow,
                        partialTicks, time);
            }
        }
    }

    @Override
    public boolean shouldRenderOffScreen(final ReactorControllerEntity controller) {
        // the glow fills the whole reactor, which may be on screen while its controller is not
        return true;
    }

    @Override
    public int getViewDistance() {
        return EffectsConfig.CLIENT.range.get();
    }

    //region internals

    private static final float GLOW_RED = 0.15f, GLOW_GREEN = 0.45f, GLOW_BLUE = 1.0f;

    private static void renderGlow(final VertexConsumer out, final Matrix4f matrix, final ReactorEffects effects,
                                   final BlockPos origin, final float glow, final float partialTicks,
                                   final float time) {

        final int length = effects.getRodLength();

        if (length <= 0) {
            return;
        }

        for (final ReactorEffects.Rod rod : effects.getRods()) {

            final float insertion = rod.drive().get(partialTicks);
            final BlockPos position = rod.entity().getBlockPos();
            final float flicker = 0.9f + 0.1f * Mth.sin(time * 0.6f + (position.hashCode() & 0xff));
            final float intensity = glow * (1f - insertion) * flicker;

            if (intensity < 0.005f) {
                continue;
            }

            final Direction inward = rod.outward().getOpposite();
            final BlockPos first = position.relative(inward).subtract(origin);
            final BlockPos last = position.relative(inward, length).subtract(origin);
            final float x0 = Math.min(first.getX(), last.getX()), x1 = Math.max(first.getX(), last.getX()) + 1;
            final float y0 = Math.min(first.getY(), last.getY()), y1 = Math.max(first.getY(), last.getY()) + 1;
            final float z0 = Math.min(first.getZ(), last.getZ()), z1 = Math.max(first.getZ(), last.getZ()) + 1;

            glowBox(out, matrix, x0, y0, z0, x1, y1, z1, 0.02f, 0.3f * intensity);
            glowBox(out, matrix, x0, y0, z0, x1, y1, z1, 0.3f, 0.1f * intensity);
        }
    }

    private static void glowBox(final VertexConsumer out, final Matrix4f matrix,
                                float x0, float y0, float z0, float x1, float y1, float z1,
                                final float outset, final float alpha) {

        x0 -= outset; y0 -= outset; z0 -= outset;
        x1 += outset; y1 += outset; z1 += outset;

        final float a = Math.min(1f, alpha);

        glowQuad(out, matrix, a, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        glowQuad(out, matrix, a, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        glowQuad(out, matrix, a, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
        glowQuad(out, matrix, a, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        glowQuad(out, matrix, a, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        glowQuad(out, matrix, a, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
    }

    private static void glowQuad(final VertexConsumer out, final Matrix4f matrix, final float alpha,
                                 final float ax, final float ay, final float az, final float bx, final float by,
                                 final float bz, final float cx, final float cy, final float cz,
                                 final float dx, final float dy, final float dz) {

        out.vertex(matrix, ax, ay, az).color(GLOW_RED, GLOW_GREEN, GLOW_BLUE, alpha).endVertex();
        out.vertex(matrix, bx, by, bz).color(GLOW_RED, GLOW_GREEN, GLOW_BLUE, alpha).endVertex();
        out.vertex(matrix, cx, cy, cz).color(GLOW_RED, GLOW_GREEN, GLOW_BLUE, alpha).endVertex();
        out.vertex(matrix, dx, dy, dz).color(GLOW_RED, GLOW_GREEN, GLOW_BLUE, alpha).endVertex();
    }

    /**
     * Light the two grey indicator lamps of the controller face, at pixels 4-6 and 7-9 of row 12.
     */
    private static void renderLamps(final VertexConsumer out, final Matrix4f matrix, final Direction face,
                                    final int rgb) {

        final float r = (rgb >> 16 & 0xff) / 255f, g = (rgb >> 8 & 0xff) / 255f, b = (rgb & 0xff) / 255f;

        for (final float left : LAMP_COLUMNS) {

            faceRect(out, matrix, face, left - 1, 11, left + 3, 14, 0.004f, r, g, b, 0.35f);
            faceRect(out, matrix, face, left, 12, left + 2, 13, 0.006f, r, g, b, 1.0f);
        }
    }

    /**
     * Draw a rectangle on a face of the block, in pixels of the face as seen from outside it: u to the right
     * and v down from its top-left corner.
     */
    private static void faceRect(final VertexConsumer out, final Matrix4f matrix, final Direction face,
                                 final float u0, final float v0, final float u1, final float v1, final float lift,
                                 final float r, final float g, final float b, final float alpha) {

        final Vector3f normal = face.step();
        final Vector3f right = rightOf(face).step();
        final Vector3f up = upOf(face).step();
        final float[][] corners = {{u0, v0}, {u0, v1}, {u1, v1}, {u1, v0}};

        for (final float[] corner : corners) {

            final float across = corner[0] / 16f - 0.5f, down = corner[1] / 16f - 0.5f;
            final float x = 0.5f + normal.x() * (0.5f + lift) + right.x() * across - up.x() * down;
            final float y = 0.5f + normal.y() * (0.5f + lift) + right.y() * across - up.y() * down;
            final float z = 0.5f + normal.z() * (0.5f + lift) + right.z() * across - up.z() * down;

            out.vertex(matrix, x, y, z).color(r, g, b, alpha).endVertex();
        }
    }

    private static Direction rightOf(final Direction face) {
        return face.getAxis().isHorizontal() ? face.getCounterClockWise() : Direction.EAST;
    }

    private static Direction upOf(final Direction face) {
        return switch (face) {
            case UP -> Direction.NORTH;
            case DOWN -> Direction.SOUTH;
            default -> Direction.UP;
        };
    }

    private static final float[] LAMP_COLUMNS = {4f, 7f};

    //endregion
}

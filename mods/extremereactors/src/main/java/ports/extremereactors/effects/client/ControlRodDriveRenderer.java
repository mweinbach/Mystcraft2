/*
 * Extreme Reactors 1.19.2 port - visual effects
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.effects.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;
import it.zerono.mods.extremereactors.ExtremeReactors;
import it.zerono.mods.extremereactors.gamecontent.multiblock.reactor.part.ReactorControlRodEntity;
import it.zerono.mods.extremereactors.gamecontent.multiblock.reactor.variant.ReactorVariant;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import ports.extremereactors.effects.EffectsConfig;

/**
 * Draws the drive of a control rod on the outside of the reactor: a housing with a shaft and cap that stand
 * out as far as the rod is withdrawn and sink in as it is inserted.
 */
final class ControlRodDriveRenderer
        implements BlockEntityRenderer<ReactorControlRodEntity> {

    /** Shaft travel, in pixels, between a fully withdrawn and a fully inserted rod. */
    static final float STROKE = 11f;

    ControlRodDriveRenderer(final BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(final ReactorControlRodEntity rod, final float partialTicks, final PoseStack stack,
                       final MultiBufferSource buffers, final int packedLight, final int packedOverlay) {

        final Level level = rod.getLevel();

        if (null == level || !rod.isMachineAssembled()) {
            return;
        }

        final Direction outward = rod.getOutwardDirection().orElse(null);

        if (null == outward) {
            return;
        }

        final BlockPos front = rod.getBlockPos().relative(outward);

        if (level.getBlockState(front).isSolidRender(level, front)) {
            return;
        }

        final float insertion = rod.evalOnController(reactor -> {

            final ReactorEffects effects = MachineEffects.get(reactor);
            final float drawn = null == effects ? -1f : effects.getRodInsertion(rod.getBlockPos(), partialTicks);

            return drawn >= 0f ? drawn : rod.getInsertionPercentage();
        }, rod.getInsertionPercentage());

        final boolean reinforced = rod.getMultiblockVariant().filter(ReactorVariant.Reinforced::equals).isPresent();
        final VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(reinforced ? REINFORCED : BASIC));
        final int light = LevelRenderer.getLightColor(level, front);

        stack.pushPose();
        stack.translate(0.5, 0.5, 0.5);
        stack.mulPose(outward.getRotation());
        stack.translate(-0.5, -0.5, -0.5);

        final PoseStack.Pose pose = stack.last();
        final float shaftTop = 20f + STROKE * (1f - insertion);

        box(out, pose, light, 4, 16, 4, 12, 20, 12, HOUSING_TOP, HOUSING_SIDE, false);

        if (shaftTop > 20.01f) {
            box(out, pose, light, 6, 20, 6, 10, shaftTop, 10, null,
                    new Region(16, 0, 4, shaftTop - 20f), false);
        }

        box(out, pose, light, 5, shaftTop, 5, 11, shaftTop + 2, 11, CAP_TOP, CAP_SIDE, true);
        stack.popPose();
    }

    @Override
    public int getViewDistance() {
        return EffectsConfig.CLIENT.range.get();
    }

    //region internals

    /** A rectangle of the 32x32 drive texture, in pixels. */
    private record Region(float u, float v, float width, float height) {

        float u0() {
            return this.u / 32f;
        }

        float v0() {
            return this.v / 32f;
        }

        float u1() {
            return (this.u + this.width) / 32f;
        }

        float v1() {
            return (this.v + this.height) / 32f;
        }
    }

    /**
     * Draw a box given in pixels of the block, the drive standing on its top face (y = 16) and the top of the box
     * facing out of the reactor.
     */
    private static void box(final VertexConsumer out, final PoseStack.Pose pose, final int light,
                            final float px0, final float py0, final float pz0,
                            final float px1, final float py1, final float pz1,
                            final Region top, final Region side, final boolean drawBottom) {

        final float x0 = px0 / 16f, y0 = py0 / 16f, z0 = pz0 / 16f;
        final float x1 = px1 / 16f, y1 = py1 / 16f, z1 = pz1 / 16f;

        if (null != top) {

            quad(out, pose, light, top, 0, 1, 0, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);

            if (drawBottom) {
                quad(out, pose, light, top, 0, -1, 0, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1);
            }
        }

        quad(out, pose, light, side, 0, 0, -1, x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0);
        quad(out, pose, light, side, 0, 0, 1, x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1);
        quad(out, pose, light, side, -1, 0, 0, x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1);
        quad(out, pose, light, side, 1, 0, 0, x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0);
    }

    /**
     * Corners in order: top-left, bottom-left, bottom-right and top-right of the texture region.
     */
    private static void quad(final VertexConsumer out, final PoseStack.Pose pose, final int light, final Region uv,
                             final float nx, final float ny, final float nz,
                             final float ax, final float ay, final float az, final float bx, final float by, final float bz,
                             final float cx, final float cy, final float cz, final float dx, final float dy, final float dz) {

        final Matrix4f matrix = pose.pose();
        final Matrix3f normal = pose.normal();

        vertex(out, matrix, normal, light, ax, ay, az, uv.u0(), uv.v0(), nx, ny, nz);
        vertex(out, matrix, normal, light, bx, by, bz, uv.u0(), uv.v1(), nx, ny, nz);
        vertex(out, matrix, normal, light, cx, cy, cz, uv.u1(), uv.v1(), nx, ny, nz);
        vertex(out, matrix, normal, light, dx, dy, dz, uv.u1(), uv.v0(), nx, ny, nz);
    }

    private static void vertex(final VertexConsumer out, final Matrix4f matrix, final Matrix3f normal, final int light,
                               final float x, final float y, final float z, final float u, final float v,
                               final float nx, final float ny, final float nz) {
        out.vertex(matrix, x, y, z)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(normal, nx, ny, nz)
                .endVertex();
    }

    private static final ResourceLocation BASIC = ExtremeReactors.newID("textures/effects/control_rod_drive_basic.png");
    private static final ResourceLocation REINFORCED = ExtremeReactors.newID("textures/effects/control_rod_drive_reinforced.png");

    private static final Region HOUSING_TOP = new Region(0, 0, 8, 8);
    private static final Region HOUSING_SIDE = new Region(8, 0, 8, 4);
    private static final Region CAP_TOP = new Region(0, 8, 6, 6);
    private static final Region CAP_SIDE = new Region(8, 8, 6, 2);

    //endregion
}

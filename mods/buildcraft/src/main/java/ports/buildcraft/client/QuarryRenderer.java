package ports.buildcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.builders.QuarryBlockEntity;

/**
 * Upstream {@code RenderQuarry}: four frame beams crossing at the drill column, the vertical frame
 * down to the drill, and the drill bit that bobs as it breaks blocks.
 */
public final class QuarryRenderer implements BlockEntityRenderer<QuarryBlockEntity> {
    public static final ResourceLocation FRAME = BuildCraft.id("block/frame/default");
    public static final ResourceLocation DRILL = BuildCraft.id("block/quarry/drill");
    private static final double FRAME_RADIUS = 4 / 16.0;
    private static final double DRILL_RADIUS = 2 / 16.0;

    @Override
    public void render(QuarryBlockEntity quarry, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        if (!quarry.frameBox.isInitialized() || quarry.getLevel() == null) return;
        Vec3 drill = quarry.drillPosForRender(partialTicks);
        if (drill == null) return;
        BlockPos origin = quarry.getBlockPos();
        BlockPos min = quarry.frameBox.min();
        BlockPos max = quarry.frameBox.max();
        double yOffset = quarry.drillOffsetForRender(partialTicks);
        double top = max.getY() + 0.5;
        double cx = drill.x + 0.5;
        double cz = drill.z + 0.5;
        TextureAtlasSprite frame = RenderUtil.sprite(FRAME);
        TextureAtlasSprite bit = RenderUtil.sprite(DRILL);
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS));
        int beamLight = LevelRenderer.getLightColor(quarry.getLevel(), new BlockPos(cx, top, cz));

        int drillLight = LevelRenderer.getLightColor(quarry.getLevel(), new BlockPos(cx, drill.y + 1, cz));
        double ox = origin.getX(), oy = origin.getY(), oz = origin.getZ();
        double x = cx - ox, z = cz - oz, y = top - oy, dy = drill.y - oy;
        beam(poseStack, consumer, frame, beamLight, FRAME_RADIUS, 4, x, y, min.getZ() - oz + 4 / 16.0, x, y, max.getZ() - oz + 12 / 16.0);
        beam(poseStack, consumer, frame, beamLight, FRAME_RADIUS, 4, min.getX() - ox + 4 / 16.0, y, z, max.getX() - ox + 12 / 16.0, y, z);
        beam(poseStack, consumer, frame, beamLight, FRAME_RADIUS, 4, x, dy + 1 + 4 / 16.0, z, x, y, z);
        beam(poseStack, consumer, bit, drillLight, DRILL_RADIUS, 0, x, dy + yOffset, z, x, dy + 1 + yOffset, z);
    }

    /**
     * An axis-aligned beam between the centres of its end faces, relative to the quarry. The sprite row
     * starting at {@code vMin} tiles once per block along the beam, as upstream laser rows do.
     */
    private static void beam(PoseStack poseStack, VertexConsumer consumer, TextureAtlasSprite sprite, int light, double radius, float vMin,
            double x1, double y1, double z1, double x2, double y2, double z2) {
        double ax = Math.min(x1, x2) - (x1 == x2 ? radius : 0), bx = Math.max(x1, x2) + (x1 == x2 ? radius : 0);
        double ay = Math.min(y1, y2) - (y1 == y2 ? radius : 0), by = Math.max(y1, y2) + (y1 == y2 ? radius : 0);
        double az = Math.min(z1, z2) - (z1 == z2 ? radius : 0), bz = Math.max(z1, z2) + (z1 == z2 ? radius : 0);
        Direction.Axis axis = x1 != x2 ? Direction.Axis.X : y1 != y2 ? Direction.Axis.Y : Direction.Axis.Z;
        double start = axis.choose(ax, ay, az);
        double end = axis.choose(bx, by, bz);
        float thickness = (float) (radius * 32);
        for (double from = start; from < end - 1e-4; from = Math.min(end, Math.floor(from + 1))) {
            double to = Math.min(end, Math.floor(from) + 1);
            double[] lo = {ax, ay, az};
            double[] hi = {bx, by, bz};
            lo[axis.ordinal()] = from;
            hi[axis.ordinal()] = to;
            float length = (float) (to - from) * 16;
            for (Direction face : Direction.values()) {
                float[] uv = face.getAxis() == axis ? new float[] {8 - thickness / 2, vMin, 8 + thickness / 2, vMin + thickness}
                        : new float[] {0, vMin, length, vMin + thickness};
                RenderUtil.face(poseStack, consumer, sprite, face, uv, (float) lo[0] * 16, (float) lo[1] * 16, (float) lo[2] * 16,
                        (float) hi[0] * 16, (float) hi[1] * 16, (float) hi[2] * 16, 0xFFFFFFFF, light);
            }
        }
    }

    @Override
    public boolean shouldRenderOffScreen(QuarryBlockEntity quarry) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}

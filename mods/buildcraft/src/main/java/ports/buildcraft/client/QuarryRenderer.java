package ports.buildcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
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
        RenderUtil.beam(poseStack, consumer, frame, beamLight, FRAME_RADIUS, 4, null, x, y, min.getZ() - oz + 4 / 16.0, x, y, max.getZ() - oz + 12 / 16.0);
        RenderUtil.beam(poseStack, consumer, frame, beamLight, FRAME_RADIUS, 4, null, min.getX() - ox + 4 / 16.0, y, z, max.getX() - ox + 12 / 16.0, y, z);
        RenderUtil.beam(poseStack, consumer, frame, beamLight, FRAME_RADIUS, 4, null, x, dy + 1 + 4 / 16.0, z, x, y, z);
        RenderUtil.beam(poseStack, consumer, bit, drillLight, DRILL_RADIUS, 0, null, x, dy + yOffset, z, x, dy + 1 + yOffset, z);
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

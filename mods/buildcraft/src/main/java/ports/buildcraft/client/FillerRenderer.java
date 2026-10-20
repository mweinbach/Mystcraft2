package ports.buildcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import ports.buildcraft.builders.FillerBlockEntity;

/** Upstream {@code RenderFiller}: the filler's {@link SnapshotBuilderRenderer} work, and write stripes around a marked area. */
public final class FillerRenderer implements BlockEntityRenderer<FillerBlockEntity> {
    @Override
    public void render(FillerBlockEntity filler, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        Level level = filler.getLevel();
        if (level == null) return;
        BlockPos origin = filler.getBlockPos();
        if (filler.getBuilder() != null) SnapshotBuilderRenderer.render(filler.getBuilder(), level, origin, partialTicks, poseStack, buffers);
        if (filler.markerBox) {
            VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS));
            LaserRenderer.box(level, origin, poseStack, consumer, filler.box, LaserRenderer.STRIPES_WRITE, true);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(FillerBlockEntity filler) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}

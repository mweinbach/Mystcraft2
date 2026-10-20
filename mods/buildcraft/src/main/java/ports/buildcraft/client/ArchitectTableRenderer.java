package ports.buildcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.inventory.InventoryMenu;
import ports.buildcraft.builders.ArchitectTableBlockEntity;

/** Upstream {@code RenderArchitectTable}: an area taken from land marks is outlined with read stripes. */
public final class ArchitectTableRenderer implements BlockEntityRenderer<ArchitectTableBlockEntity> {
    @Override
    public void render(ArchitectTableBlockEntity table, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        if (!table.markerBox || table.getLevel() == null || !table.box.isInitialized()) return;
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS));
        LaserRenderer.box(table.getLevel(), table.getBlockPos(), poseStack, consumer, table.box, LaserRenderer.STRIPES_READ, true);
    }

    @Override
    public boolean shouldRenderOffScreen(ArchitectTableBlockEntity table) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}

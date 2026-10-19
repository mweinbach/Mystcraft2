package ports.buildcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.factory.MinerBlockEntity;

/**
 * Upstream {@code RenderTube}: the pump or mining well tube, drawn as a 1/16-scale laser whose middle
 * row is {@code v 0..8} of the tube sprite and whose caps are its lower-left quarter.
 */
public final class MinerRenderer<T extends MinerBlockEntity> implements BlockEntityRenderer<T> {
    public static final ResourceLocation PUMP_TUBE = BuildCraft.id("block/pump/tube");
    public static final ResourceLocation MINING_WELL_TUBE = BuildCraft.id("block/mining_well/tube");
    private static final float[] CAP_UV = {0, 8, 8, 16};

    private final ResourceLocation tube;

    public MinerRenderer(ResourceLocation tube) {
        this.tube = tube;
    }

    @Override
    public void render(T miner, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        if (miner.isComplete() || miner.getLevel() == null) return;
        double length = miner.getLength(partialTicks);
        if (length <= 0) return;
        int tubeLight = LevelRenderer.getLightColor(miner.getLevel(), miner.getBlockPos().below());
        RenderUtil.beam(poseStack, buffers.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS)), RenderUtil.sprite(tube),
                tubeLight, 0.25, 0, CAP_UV, 0.5, 0, 0.5, 0.5, -length, 0.5);
    }

    @Override
    public boolean shouldRenderOffScreen(T miner) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}

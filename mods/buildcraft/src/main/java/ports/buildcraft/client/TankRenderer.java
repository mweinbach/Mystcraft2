package ports.buildcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import ports.buildcraft.factory.TankBlockEntity;
import ports.buildcraft.platform.Platform;

/** Upstream {@code RenderTank}: the fluid fills the glass interior from the bottom, gases from the top. */
public final class TankRenderer implements BlockEntityRenderer<TankBlockEntity> {
    private static final float MIN = 0.13F * 16;
    private static final float MAX = 0.87F * 16;

    @Override
    public void render(TankBlockEntity tank, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        fluid(poseStack, buffers, tank.fluidForRender(), tank.amountForRender(partialTicks), TankBlockEntity.CAPACITY,
                MIN, 0.01F * 16, MIN, MAX, 0.99F * 16, MAX, light);
    }

    /**
     * Upstream {@code FluidRenderer.renderFluid}: fills the box, in pixels, in proportion to the amount,
     * from the bottom for liquids and from the top for gases.
     */
    public static void fluid(PoseStack poseStack, MultiBufferSource buffers, Fluid fluid, float amount, int capacity,
            float x1, float y1, float z1, float x2, float y2, float z2, int light) {
        if (fluid == Fluids.EMPTY || amount <= 0) return;
        float height = Math.min(1, amount / capacity) * (y2 - y1);
        float bottom = Platform.get().isGaseous(fluid) ? y2 - height : y1;
        RenderUtil.box(poseStack, buffers.getBuffer(RenderType.entityTranslucentCull(InventoryMenu.BLOCK_ATLAS)),
                ClientPlatform.get().fluidSprite(fluid), RenderUtil.AUTO, x1, bottom, z1, x2, bottom + height, z2,
                ClientPlatform.get().fluidColour(fluid), light);
    }
}

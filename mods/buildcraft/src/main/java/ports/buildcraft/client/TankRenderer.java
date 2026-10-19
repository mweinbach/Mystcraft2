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
        Fluid fluid = tank.fluidForRender();
        float amount = tank.amountForRender(partialTicks);
        if (fluid == Fluids.EMPTY || amount <= 0) return;
        float height = Math.min(1, amount / TankBlockEntity.CAPACITY) * (0.99F - 0.01F) * 16;
        boolean gas = Platform.get().isGaseous(fluid);
        float y1 = gas ? 0.99F * 16 - height : 0.01F * 16;
        RenderUtil.box(poseStack, buffers.getBuffer(RenderType.entityTranslucentCull(InventoryMenu.BLOCK_ATLAS)),
                ClientPlatform.get().fluidSprite(fluid), RenderUtil.AUTO, MIN, y1, MIN, MAX, y1 + height, MAX,
                ClientPlatform.get().fluidColour(fluid), light);
    }
}

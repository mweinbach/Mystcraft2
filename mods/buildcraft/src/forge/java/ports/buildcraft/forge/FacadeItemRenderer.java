package ports.buildcraft.forge;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.world.item.ItemStack;
import ports.buildcraft.client.FacadeRenderer;

/** Forge draws the facade item through the shared baker; the item model is {@code builtin/entity}. */
final class FacadeItemRenderer extends BlockEntityWithoutLevelRenderer {
    FacadeItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemTransforms.TransformType transform, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        FacadeRenderer.renderItem(stack, poseStack, buffers, light, overlay);
    }
}

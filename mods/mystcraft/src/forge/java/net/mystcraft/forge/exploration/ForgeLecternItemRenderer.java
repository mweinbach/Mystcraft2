package net.mystcraft.forge.exploration;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.world.item.ItemStack;
import net.mystcraft.minecraft.client.model.ClassicLecternItemModel;

final class ForgeLecternItemRenderer extends BlockEntityWithoutLevelRenderer {
    ForgeLecternItemRenderer() { super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels()); }
    @Override public void renderByItem(ItemStack stack, ItemTransforms.TransformType transform, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ClassicLecternItemModel.render(pose, buffers, light, overlay);
    }
}

/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ports.mekanism.lib.client.extensions.common.IClientItemExtensions;

/**
 * Renders built-in item models with the item's own renderer, as Forge does. An item registered with Fabric's
 * {@link BuiltinItemRendererRegistry} is left on the vanilla renderer: Fabric's mixin on that renderer dispatches the
 * registry. The wind generator item uses that registry.
 */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/client/renderer/BlockEntityWithoutLevelRenderer;renderByItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/renderer/block/model/ItemTransforms$TransformType;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"))
    private void mekanism$customRenderer(BlockEntityWithoutLevelRenderer vanilla, ItemStack stack, ItemTransforms.TransformType transformType, PoseStack poseStack,
          MultiBufferSource buffers, int light, int overlay, Operation<Void> original) {
        if (BuiltinItemRendererRegistry.INSTANCE.get(stack.getItem()) != null) {
            original.call(vanilla, stack, transformType, poseStack, buffers, light, overlay);
            return;
        }
        original.call(IClientItemExtensions.of(stack).getCustomRenderer(), stack, transformType, poseStack, buffers, light, overlay);
    }
}

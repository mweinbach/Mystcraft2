/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.mekanism.lib.client.extensions.IForgeElytraLayer;

/**
 * Draws wings for whatever chest item {@link IForgeElytraLayer#shouldRender} accepts, with the texture
 * {@link IForgeElytraLayer#getElytraTexture} gives when the player has no elytra skin of their own.
 */
@Mixin(ElytraLayer.class)
public abstract class ElytraLayerMixin<T extends LivingEntity> implements IForgeElytraLayer<T> {

    @Unique
    private T mekanism$entity;

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
          at = @At("HEAD"))
    private void mekanism$captureEntity(PoseStack poseStack, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount,
          float partialTick, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        mekanism$entity = entity;
    }

    @Redirect(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
          at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z"))
    private boolean mekanism$shouldRender(ItemStack stack, Item elytra) {
        return shouldRender(stack, mekanism$entity);
    }

    @ModifyVariable(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
          at = @At(value = "STORE"), ordinal = 0)
    private ResourceLocation mekanism$elytraTexture(ResourceLocation texture) {
        return texture == ElytraLayer.WINGS_LOCATION ? getElytraTexture(mekanism$entity.getItemBySlot(EquipmentSlot.CHEST), mekanism$entity) : texture;
    }
}

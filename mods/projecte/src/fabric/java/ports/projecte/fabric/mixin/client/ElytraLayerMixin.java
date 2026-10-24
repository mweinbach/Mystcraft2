/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.projecte.lib.client.extensions.IForgeElytraLayer;

/**
 * Draws wings for whatever chest item {@link IForgeElytraLayer#shouldRender} accepts, with the texture
 * {@link IForgeElytraLayer#getElytraTexture} gives when the player has no elytra skin of their own.
 */
@Mixin(ElytraLayer.class)
public abstract class ElytraLayerMixin<T extends LivingEntity> implements IForgeElytraLayer<T> {

    @Unique
    private T projecte$entity;

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
          at = @At("HEAD"))
    private void projecte$captureEntity(PoseStack poseStack, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount,
          float partialTick, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        projecte$entity = entity;
    }

    /** Whether a layer class overrides {@link IForgeElytraLayer#shouldRender}; the default is the vanilla elytra check. */
    @Unique
    private static final Map<Class<?>, Boolean> projecte$customShouldRender = new ConcurrentHashMap<>();

    @Unique
    private static boolean projecte$overridesShouldRender(Class<?> type) {
        try {
            return type.getMethod("shouldRender", ItemStack.class, LivingEntity.class).getDeclaringClass() != IForgeElytraLayer.class;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /**
     * Wrapped, not redirected: Origins' Apoli redirects the same call in Tekxit 4, and two redirects of one instruction
     * crash the game. Layers that keep the default {@code shouldRender} (the same elytra check) run the original call, so
     * other mods' wrappers still apply; layers that override it, such as Mekanism's HDPE elytra, decide alone.
     */
    @WrapOperation(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
          at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z"))
    private boolean projecte$shouldRender(ItemStack stack, Item elytra, Operation<Boolean> original) {
        return projecte$customShouldRender.computeIfAbsent(getClass(), ElytraLayerMixin::projecte$overridesShouldRender) ? shouldRender(stack, projecte$entity) : original.call(stack, elytra);
    }

    @ModifyVariable(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
          at = @At(value = "STORE"), ordinal = 0)
    private ResourceLocation projecte$elytraTexture(ResourceLocation texture) {
        return texture == ElytraLayer.WINGS_LOCATION ? getElytraTexture(projecte$entity.getItemBySlot(EquipmentSlot.CHEST), projecte$entity) : texture;
    }
}

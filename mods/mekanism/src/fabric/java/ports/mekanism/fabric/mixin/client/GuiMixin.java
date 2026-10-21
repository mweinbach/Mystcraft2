/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.mekanism.lib.client.gui.overlay.GuiOverlayManager;
import ports.mekanism.lib.client.gui.overlay.VanillaGuiOverlay;

/**
 * Draws modded HUD overlays at the vanilla anchors Forge orders them against, and lets the crosshair overlay event
 * cancel the crosshair the way Forge's overlay pass does.
 */
@Mixin(Gui.class)
public abstract class GuiMixin {

    @Unique
    private boolean mekanism$crosshairCanceled;

    @Inject(method = "render", at = @At("HEAD"))
    private void mekanism$beforeHud(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        GuiOverlayManager.render(GuiOverlayManager.Hook.BEFORE_ALL, poseStack, partialTick);
    }

    @Inject(method = "render", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
          target = "Lnet/minecraft/client/gui/Gui;renderHotbar(FLcom/mojang/blaze3d/vertex/PoseStack;)V"))
    private void mekanism$afterHotbar(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        GuiOverlayManager.render(GuiOverlayManager.Hook.AFTER_HOTBAR, poseStack, partialTick);
    }

    @Inject(method = "render", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
          target = "Lnet/minecraft/client/gui/Gui;renderPlayerHealth(Lcom/mojang/blaze3d/vertex/PoseStack;)V"))
    private void mekanism$afterHealth(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        GuiOverlayManager.render(GuiOverlayManager.Hook.AFTER_PLAYER_HEALTH, poseStack, partialTick);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void mekanism$afterHud(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        GuiOverlayManager.render(GuiOverlayManager.Hook.AFTER_ALL, poseStack, partialTick);
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void mekanism$crosshairPre(PoseStack poseStack, CallbackInfo ci) {
        this.mekanism$crosshairCanceled = GuiOverlayManager.preVanilla(VanillaGuiOverlay.CROSSHAIR, poseStack);
        if (this.mekanism$crosshairCanceled) {
            ci.cancel();
        }
    }

    @Inject(method = "renderCrosshair", at = @At("RETURN"))
    private void mekanism$crosshairPost(PoseStack poseStack, CallbackInfo ci) {
        if (!this.mekanism$crosshairCanceled) {
            GuiOverlayManager.postVanilla(VanillaGuiOverlay.CROSSHAIR, poseStack);
        }
        this.mekanism$crosshairCanceled = false;
    }
}

/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.projecte.lib.client.gui.overlay.GuiOverlayManager;
import ports.projecte.lib.client.gui.overlay.VanillaGuiOverlay;

/**
 * Draws modded HUD overlays at the vanilla anchors Forge orders them against, and lets the crosshair overlay event
 * cancel the crosshair the way Forge's overlay pass does.
 */
@Mixin(Gui.class)
public abstract class GuiMixin {

    @Unique
    private boolean projecte$crosshairCanceled;

    @Inject(method = "render", at = @At("HEAD"))
    private void projecte$beforeHud(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        GuiOverlayManager.render(GuiOverlayManager.Hook.BEFORE_ALL, poseStack, partialTick);
    }

    @Inject(method = "render", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
          target = "Lnet/minecraft/client/gui/Gui;renderHotbar(FLcom/mojang/blaze3d/vertex/PoseStack;)V"))
    private void projecte$afterHotbar(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        GuiOverlayManager.render(GuiOverlayManager.Hook.AFTER_HOTBAR, poseStack, partialTick);
    }

    @Inject(method = "render", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
          target = "Lnet/minecraft/client/gui/Gui;renderPlayerHealth(Lcom/mojang/blaze3d/vertex/PoseStack;)V"))
    private void projecte$afterHealth(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        GuiOverlayManager.render(GuiOverlayManager.Hook.AFTER_PLAYER_HEALTH, poseStack, partialTick);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void projecte$afterHud(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        GuiOverlayManager.render(GuiOverlayManager.Hook.AFTER_ALL, poseStack, partialTick);
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void projecte$crosshairPre(PoseStack poseStack, CallbackInfo ci) {
        this.projecte$crosshairCanceled = GuiOverlayManager.preVanilla(VanillaGuiOverlay.CROSSHAIR, poseStack);
        if (this.projecte$crosshairCanceled) {
            ci.cancel();
        }
    }

    @Inject(method = "renderCrosshair", at = @At("RETURN"))
    private void projecte$crosshairPost(PoseStack poseStack, CallbackInfo ci) {
        if (!this.projecte$crosshairCanceled) {
            GuiOverlayManager.postVanilla(VanillaGuiOverlay.CROSSHAIR, poseStack);
        }
        this.projecte$crosshairCanceled = false;
    }
}

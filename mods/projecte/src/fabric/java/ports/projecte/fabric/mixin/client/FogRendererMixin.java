/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.projecte.lib.client.event.ViewportEvent;
import ports.projecte.lib.common.MinecraftForge;

/**
 * Posts fog events after vanilla has computed the color and the distances. Changed distances apply only when the
 * distance event is canceled, which is how Forge applies them.
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {

    @Shadow
    private static float fogRed;

    @Shadow
    private static float fogGreen;

    @Shadow
    private static float fogBlue;

    // Powder snow clears the color and then falls through. Forge posts the event only at the final clear. The
    // invocation's floats are already on the stack, so the redirect passes the event colors into clearColor itself.
    @Redirect(method = "setupColor", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;clearColor(FFFF)V", ordinal = 1))
    private static void projecte$fogColor(float red, float green, float blue, float alpha, Camera camera, float partialTick, ClientLevel level,
          int renderDistance, float darkenWorldAmount) {
        ViewportEvent.ComputeFogColor event = new ViewportEvent.ComputeFogColor(camera, partialTick, red, green, blue);
        MinecraftForge.EVENT_BUS.post(event);
        fogRed = event.getRed();
        fogGreen = event.getGreen();
        fogBlue = event.getBlue();
        RenderSystem.clearColor(fogRed, fogGreen, fogBlue, alpha);
    }

    @Inject(method = "setupFog", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
          target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderFogShape(Lcom/mojang/blaze3d/shaders/FogShape;)V"))
    private static void projecte$fogDistance(Camera camera, FogRenderer.FogMode mode, float renderDistance, boolean thickFog, float partialTick,
          CallbackInfo ci, @Local FogRenderer.FogData fogData) {
        FogType type = camera.getFluidInCamera();
        ViewportEvent.RenderFog event = new ViewportEvent.RenderFog(mode, type, camera, partialTick, fogData.start, fogData.end, fogData.shape);
        if (MinecraftForge.EVENT_BUS.post(event)) {
            RenderSystem.setShaderFogStart(event.getNearPlaneDistance());
            RenderSystem.setShaderFogEnd(event.getFarPlaneDistance());
            RenderSystem.setShaderFogShape(event.getFogShape());
        }
    }
}

/*
 * Mekanism 1.19.2 port - Fabric mixins applying the port's Forge API shapes to vanilla classes.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric.mixin.client;

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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.mekanism.lib.client.event.ViewportEvent;
import ports.mekanism.lib.common.MinecraftForge;

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

    // Powder snow clears the color and then falls through. Forge posts the event only at the final clear.
    @Inject(method = "setupColor", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;clearColor(FFFF)V", ordinal = 1))
    private static void mekanism$fogColor(Camera camera, float partialTick, ClientLevel level, int renderDistance, float darkenWorldAmount, CallbackInfo ci) {
        ViewportEvent.ComputeFogColor event = new ViewportEvent.ComputeFogColor(camera, partialTick, fogRed, fogGreen, fogBlue);
        MinecraftForge.EVENT_BUS.post(event);
        fogRed = event.getRed();
        fogGreen = event.getGreen();
        fogBlue = event.getBlue();
    }

    @Inject(method = "setupFog", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
          target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderFogShape(Lcom/mojang/blaze3d/shaders/FogShape;)V"))
    private static void mekanism$fogDistance(Camera camera, FogRenderer.FogMode mode, float renderDistance, boolean thickFog, float partialTick,
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

/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.metalrender.client.MetalTerrainRenderer;

/** Copies the 16x16 lightmap into Metal each time vanilla refreshes it. */
@Mixin(LightTexture.class)
public abstract class LightTextureMixin {

    @Shadow
    @Final
    private NativeImage lightPixels;

    @Inject(method = "updateLightTexture", at = @At("RETURN"))
    private void metalrender$updateLightTexture(final float partialTick, final CallbackInfo ci) {
        MetalTerrainRenderer.onLightmapUpdated(lightPixels);
    }
}

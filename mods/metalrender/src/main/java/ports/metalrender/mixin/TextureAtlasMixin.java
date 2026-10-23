/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.mixin;

import net.minecraft.client.renderer.texture.TextureAtlas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.metalrender.client.MetalTerrainRenderer;

/** A block atlas (re)load invalidates the Metal copy; it is read back before the next frame. */
@Mixin(TextureAtlas.class)
public abstract class TextureAtlasMixin {

    @Inject(method = "reload", at = @At("HEAD"))
    private void metalrender$reload(final TextureAtlas.Preparations preparations, final CallbackInfo ci) {
        if (TextureAtlas.LOCATION_BLOCKS.equals(((TextureAtlas) (Object) this).location())) {
            MetalTerrainRenderer.markAtlasDirty();
        }
    }
}

/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.metalrender.client.MetalTerrainRenderer;

/** Mirrors animated block textures (water, lava, fire, ...) into the Metal atlas. */
@Mixin(TextureAtlasSprite.class)
public abstract class TextureAtlasSpriteMixin {

    @Shadow
    @Final
    private TextureAtlas atlas;
    @Shadow
    @Final
    int width;
    @Shadow
    @Final
    int height;
    @Shadow
    @Final
    private int x;
    @Shadow
    @Final
    private int y;

    @Inject(method = "upload", at = @At("HEAD"))
    private void metalrender$upload(final int frameX, final int frameY, final NativeImage[] frames, final CallbackInfo ci) {
        MetalTerrainRenderer.onSpriteUpload(atlas.location(), x, y, width, height, frameX, frameY, frames);
    }
}

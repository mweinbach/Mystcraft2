/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ports.metalrender.client.MetalTerrainRenderer;
import ports.metalrender.terrain.OpaqueSprites;

/**
 * A block atlas (re)load invalidates the Metal copy, which is read back before the next frame, and
 * rebuilds the map of fully opaque sprites. Vanilla rebuilds every chunk after the atlas loads.
 */
@Mixin(TextureAtlas.class)
public abstract class TextureAtlasMixin {

    @Inject(method = "reload", at = @At("HEAD"))
    private void metalrender$reload(final TextureAtlas.Preparations preparations, final CallbackInfo ci) {
        if (TextureAtlas.LOCATION_BLOCKS.equals(((TextureAtlas) (Object) this).location())) {
            MetalTerrainRenderer.markAtlasDirty();
            OpaqueSprites.setCurrent(opaqueSprites((AtlasPreparationsAccessor) preparations));
        }
    }

    private static OpaqueSprites opaqueSprites(final AtlasPreparationsAccessor preparations) {
        final OpaqueSprites.Builder map = new OpaqueSprites.Builder(preparations.metalrender$width(),
                preparations.metalrender$height());
        for (final TextureAtlasSprite sprite : preparations.metalrender$regions()) {
            final NativeImage[] images = ((SpriteImageAccessor) sprite).metalrender$mainImage();
            map.sprite(sprite.getX(), sprite.getY(), sprite.getWidth(), sprite.getHeight(),
                    images != null && images.length > 0 && fullyOpaque(images[0]));
        }
        return map.build();
    }

    private static boolean fullyOpaque(final NativeImage image) {
        if (image.format() != NativeImage.Format.RGBA) {
            return false;
        }
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getPixelRGBA(x, y) >>> 24) != 0xFF) {
                    return false;
                }
            }
        }
        return true;
    }
}

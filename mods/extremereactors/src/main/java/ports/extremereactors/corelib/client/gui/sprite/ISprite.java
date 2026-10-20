/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.gui.sprite;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * An image that can be drawn by the client: a rectangle of a texture, optionally with another sprite drawn over it.
 */
public interface ISprite {

    /**
     * @return the texture atlas entry backing this sprite, if it is an atlas sprite that is currently stitched
     */
    Optional<TextureAtlasSprite> getAtlasSprite();

    /** The texture to bind to draw this sprite. */
    ResourceLocation getTextureLocation();

    /** Width, in texture pixels. */
    int getWidth();

    /** Height, in texture pixels. */
    int getHeight();

    float getMinU();

    float getMaxU();

    float getMinV();

    float getMaxV();

    /** A sprite to draw over this one, with the same size. */
    Optional<ISprite> getSpriteOverlay();

    /** A copy of this sprite with a different overlay. */
    ISprite copyWith(ISprite overlay);

    /** True for a sprite that draws nothing. */
    default boolean isEmpty() {
        return 0 == this.getWidth() || 0 == this.getHeight();
    }
}

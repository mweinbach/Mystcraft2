/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.gui.sprite;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import java.util.Optional;

/**
 * An image that can be drawn by the client.
 */
public interface ISprite {

    /**
     * @return the texture atlas entry backing this sprite, if it is an atlas sprite that is currently stitched
     */
    Optional<TextureAtlasSprite> getAtlasSprite();
}

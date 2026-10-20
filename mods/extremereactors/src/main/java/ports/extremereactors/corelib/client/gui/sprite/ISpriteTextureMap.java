/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.gui.sprite;

import net.minecraft.resources.ResourceLocation;

/**
 * A texture that sprites are cut from.
 */
public interface ISpriteTextureMap {

    ResourceLocation getTextureLocation();

    /** Width of the texture, in pixels. */
    int getWidth();

    /** Height of the texture, in pixels. */
    int getHeight();

    ISpriteBuilder sprite();
}

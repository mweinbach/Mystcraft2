/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.gui.sprite;

/**
 * Describes a rectangle of a {@link ISpriteTextureMap}. Without {@link #from} the rectangle starts at the texture
 * origin; without {@link #ofSize} it is 16 by 16 pixels.
 */
public interface ISpriteBuilder {

    ISpriteBuilder from(int x, int y);

    ISpriteBuilder ofSize(int width, int height);

    ISpriteBuilder with(ISprite overlay);

    ISprite build();
}

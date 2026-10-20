/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.gui.sprite;

import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * A plain texture file, such as a screen background or an icon sheet.
 */
public class SpriteTextureMap
        implements ISpriteTextureMap {

    public SpriteTextureMap(final ResourceLocation texture, final int width, final int height) {

        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Invalid texture size " + width + "x" + height);
        }

        this._texture = Objects.requireNonNull(texture);
        this._width = width;
        this._height = height;
    }

    //region ISpriteTextureMap

    @Override
    public ResourceLocation getTextureLocation() {
        return this._texture;
    }

    @Override
    public int getWidth() {
        return this._width;
    }

    @Override
    public int getHeight() {
        return this._height;
    }

    @Override
    public ISpriteBuilder sprite() {
        return new Builder();
    }

    //endregion
    //region internals

    private final class Builder
            implements ISpriteBuilder {

        @Override
        public ISpriteBuilder from(final int x, final int y) {

            this._x = x;
            this._y = y;
            return this;
        }

        @Override
        public ISpriteBuilder ofSize(final int width, final int height) {

            this._width = width;
            this._height = height;
            return this;
        }

        @Override
        public ISpriteBuilder with(final ISprite overlay) {

            this._overlay = overlay;
            return this;
        }

        @Override
        public ISprite build() {
            return new Sprite(SpriteTextureMap.this, this._x, this._y, this._width, this._height, this._overlay);
        }

        private int _x;
        private int _y;
        private int _width = 16;
        private int _height = 16;
        @Nullable
        private ISprite _overlay;
    }

    private final ResourceLocation _texture;
    private final int _width;
    private final int _height;

    //endregion
}

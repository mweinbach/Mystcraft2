/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.gui.sprite;

import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A rectangle of a {@link ISpriteTextureMap}.
 */
public final class Sprite
        implements ISprite {

    /** A sprite that draws nothing. */
    public static final ISprite EMPTY = new Sprite(null, 0, 0, 0, 0, null);

    public static final Supplier<ISprite> EMPTY_SUPPLIER = () -> EMPTY;

    Sprite(@Nullable final ISpriteTextureMap map, final int x, final int y, final int width, final int height,
           @Nullable final ISprite overlay) {

        this._map = map;
        this._x = x;
        this._y = y;
        this._width = Math.max(0, width);
        this._height = Math.max(0, height);
        this._overlay = overlay;
    }

    //region ISprite

    @Override
    public Optional<TextureAtlasSprite> getAtlasSprite() {
        return Optional.empty();
    }

    @Override
    public ResourceLocation getTextureLocation() {
        return null != this._map ? this._map.getTextureLocation() : MissingTextureAtlasSprite.getLocation();
    }

    @Override
    public int getWidth() {
        return null != this._map ? this._width : 0;
    }

    @Override
    public int getHeight() {
        return null != this._map ? this._height : 0;
    }

    @Override
    public float getMinU() {
        return null != this._map ? (float) this._x / this._map.getWidth() : 0.0f;
    }

    @Override
    public float getMaxU() {
        return null != this._map ? (float) (this._x + this._width) / this._map.getWidth() : 0.0f;
    }

    @Override
    public float getMinV() {
        return null != this._map ? (float) this._y / this._map.getHeight() : 0.0f;
    }

    @Override
    public float getMaxV() {
        return null != this._map ? (float) (this._y + this._height) / this._map.getHeight() : 0.0f;
    }

    @Override
    public Optional<ISprite> getSpriteOverlay() {
        return Optional.ofNullable(this._overlay);
    }

    @Override
    public ISprite copyWith(final ISprite overlay) {
        return new Sprite(this._map, this._x, this._y, this._width, this._height, overlay);
    }

    //endregion
    //region internals

    @Nullable
    private final ISpriteTextureMap _map;
    private final int _x;
    private final int _y;
    private final int _width;
    private final int _height;
    @Nullable
    private final ISprite _overlay;

    //endregion
}

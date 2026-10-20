/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.compat.jei.drawable;

import com.mojang.blaze3d.vertex.PoseStack;
import mezz.jei.api.gui.drawable.IDrawable;
import ports.extremereactors.corelib.client.gui.Orientation;
import ports.extremereactors.corelib.client.gui.Padding;
import ports.extremereactors.corelib.client.gui.sprite.ISprite;
import ports.extremereactors.corelib.client.render.ModRenderHelper;

import java.util.function.Supplier;

/**
 * A JEI drawable that fills part of its area with a tiled sprite, like the screens' gauge bars. The filled part
 * grows from the side the orientation starts at.
 */
public class ProgressBarDrawable
        implements IDrawable {

    /**
     * @param zLevel the depth the bar is painted at
     * @param padding the space between the drawable's edges and the bar
     */
    public ProgressBarDrawable(final Supplier<ISprite> sprite, final int zLevel, final Padding padding,
                               final int width, final int height, final Orientation orientation) {

        this._sprite = sprite;
        this._zLevel = zLevel;
        this._padding = padding;
        this._width = width;
        this._height = height;
        this._orientation = orientation;
    }

    /**
     * @param max the value of a full bar
     * @param value the value to show, clamped between 0 and {@code max}
     */
    public void setProgress(final double max, final double value) {
        this._progress = max > 0 ? Math.max(0.0, Math.min(1.0, value / max)) : 0.0;
    }

    //region IDrawable

    @Override
    public int getWidth() {
        return this._width;
    }

    @Override
    public int getHeight() {
        return this._height;
    }

    @Override
    public void draw(final PoseStack matrix, final int xOffset, final int yOffset) {

        ModRenderHelper.paintProgressBar(matrix, this._sprite.get(), xOffset + this._padding.left(),
                yOffset + this._padding.top(), this._zLevel, this._width - this._padding.horizontal(),
                this._height - this._padding.vertical(), this._orientation, this._progress);
    }

    //endregion
    //region internals

    private final Supplier<ISprite> _sprite;
    private final int _zLevel;
    private final Padding _padding;
    private final int _width;
    private final int _height;
    private final Orientation _orientation;
    private double _progress;

    //endregion
}

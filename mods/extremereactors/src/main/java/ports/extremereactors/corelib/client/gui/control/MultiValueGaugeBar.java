/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.gui.control;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import ports.extremereactors.corelib.client.gui.ModContainerScreen;
import ports.extremereactors.corelib.client.gui.Orientation;
import ports.extremereactors.corelib.client.gui.sprite.ISprite;
import ports.extremereactors.corelib.client.gui.sprite.Sprite;
import ports.extremereactors.corelib.data.geometry.Rectangle;
import ports.extremereactors.corelib.data.gfx.Colour;
import ports.extremereactors.corelib.item.inventory.container.ModContainer;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * A bar showing several values stacked on top of each other, from the start edge of its orientation, in the order
 * of the keys given to the constructor.
 *
 * @param <K> the key identifying each value
 */
public class MultiValueGaugeBar<K>
        extends AbstractControl {

    @SafeVarargs
    public MultiValueGaugeBar(final ModContainerScreen<? extends ModContainer> gui, final String name,
                              final double maxValue, final K... keys) {

        super(gui, name);
        this._maxValue = maxValue;
        this._keys = ImmutableList.copyOf(keys);
        this._values = new HashMap<>();
        this._sprites = new HashMap<>();
        this._tints = new HashMap<>();
        this._orientation = Orientation.BottomToTop;
    }

    public double getValue(final K key) {
        return this._values.getOrDefault(key, 0.0);
    }

    public void setValue(final K key, final double value) {
        this._values.put(Objects.requireNonNull(key), value);
    }

    public double getMaxValue() {
        return this._maxValue;
    }

    public void setMaxValue(final double maxValue) {
        this._maxValue = maxValue;
    }

    /** How full the bar is with all the values together, from 0 to 1. */
    public double getFillRatio() {

        if (this._maxValue <= 0) {
            return 0.0;
        }

        double total = 0.0;

        for (final K key : this._keys) {
            total += Math.max(0.0, this.getValue(key));
        }

        return Math.max(0.0, Math.min(1.0, total / this._maxValue));
    }

    public void setBarSprite(final K key, final Supplier<ISprite> sprite) {
        this._sprites.put(Objects.requireNonNull(key), Objects.requireNonNull(sprite));
    }

    public void setBarSprite(final K key, final ISprite sprite) {
        this.setBarSprite(key, () -> sprite);
    }

    public void setBarSpriteTint(final K key, final Colour tint) {
        this._tints.put(Objects.requireNonNull(key), Objects.requireNonNull(tint));
    }

    public void setOverlay(@Nullable final ISprite overlay) {
        this._overlay = overlay;
    }

    public Orientation getOrientation() {
        return this._orientation;
    }

    public void setOrientation(final Orientation orientation) {
        this._orientation = Objects.requireNonNull(orientation);
    }

    //region AbstractControl

    @Override
    public void onPaint(final PoseStack matrix, final float partialTicks, final int mouseX, final int mouseY) {

        final Rectangle area = this.getScreenPaddingRect();
        int skip = 0;

        for (final K key : this._keys) {
            skip = this.paintValueRect(matrix, key, area, skip);
        }

        if (null != this._overlay) {
            this.paintSprite(matrix, this._overlay, area);
        }
    }

    //endregion
    //region internals

    /**
     * Paint the part of the bar filled by one value.
     *
     * @param area the whole bar area, in screen coordinates
     * @param skip how many pixels, from the start edge, the values painted before this one already cover
     * @return the pixels covered once this value is painted
     */
    protected int paintValueRect(final PoseStack matrix, final K key, final Rectangle area, final int skip) {

        final int length = this._orientation.isHorizontal() ? area.Width : area.Height;
        final double value = Math.max(0.0, this.getValue(key));
        final int amount = this._maxValue > 0 ? (int) Math.round(Math.min(1.0, value / this._maxValue) * length) : 0;
        final ISprite sprite = this._sprites.getOrDefault(key, Sprite.EMPTY_SUPPLIER).get();

        GaugeBar.paintFill(matrix, this, sprite, this._tints.getOrDefault(key, Colour.WHITE), this._orientation,
                area, skip, amount);
        return Math.min(length, skip + amount);
    }

    private final List<K> _keys;
    private final Map<K, Double> _values;
    private final Map<K, Supplier<ISprite>> _sprites;
    private final Map<K, Colour> _tints;
    private double _maxValue;
    @Nullable
    private ISprite _overlay;
    private Orientation _orientation;

    //endregion
}

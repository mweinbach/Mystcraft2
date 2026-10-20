/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.gui.control;

import com.mojang.blaze3d.vertex.PoseStack;
import ports.extremereactors.corelib.client.gui.ModContainerScreen;
import ports.extremereactors.corelib.client.gui.Orientation;
import ports.extremereactors.corelib.client.gui.sprite.ISprite;
import ports.extremereactors.corelib.client.render.ModRenderHelper;
import ports.extremereactors.corelib.data.geometry.Rectangle;
import ports.extremereactors.corelib.data.gfx.Colour;
import ports.extremereactors.corelib.item.inventory.container.ModContainer;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * A bar that fills, from the start edge of its orientation, in proportion to a value.
 * <p>
 * The filled part reveals the bar sprite as if it covered the whole bar; atlas sprites, as fluid textures, are tiled
 * at their natural size instead. An optional overlay is painted over the whole bar.
 */
public class GaugeBar
        extends AbstractControl {

    public GaugeBar(final ModContainerScreen<? extends ModContainer> gui, final String name, final double maxValue,
                    final ISprite barSprite) {

        super(gui, name);
        this._maxValue = maxValue;
        this._barSprite = Objects.requireNonNull(barSprite);
        this._barTint = Colour.WHITE;
        this._orientation = Orientation.BottomToTop;
    }

    public double getValue() {
        return this._value;
    }

    public void setValue(final double value) {
        this._value = value;
    }

    public double getMaxValue() {
        return this._maxValue;
    }

    public void setMaxValue(final double maxValue) {
        this._maxValue = maxValue;
    }

    /** How full the bar is, from 0 to 1. */
    public double getFillRatio() {
        return this._maxValue > 0 ? Math.max(0.0, Math.min(1.0, this._value / this._maxValue)) : 0.0;
    }

    public void setBarSprite(final ISprite sprite) {
        this._barSprite = Objects.requireNonNull(sprite);
    }

    public void setBarSpriteTint(final Colour tint) {
        this._barTint = Objects.requireNonNull(tint);
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
        final int length = this._orientation.isHorizontal() ? area.Width : area.Height;

        paintFill(matrix, this, this._barSprite, this._barTint, this._orientation, area, 0,
                (int) Math.round(this.getFillRatio() * length));

        if (null != this._overlay) {
            this.paintSprite(matrix, this._overlay, area);
        }
    }

    //endregion
    //region internals

    /**
     * Paint {@code amount} pixels of a bar, starting {@code skip} pixels from the start edge of {@code area}.
     */
    static void paintFill(final PoseStack matrix, final AbstractControl control, final ISprite sprite,
                          final Colour tint, final Orientation orientation, final Rectangle area, final int skip,
                          final int amount) {

        final int length = orientation.isHorizontal() ? area.Width : area.Height;
        final int start = Math.max(0, Math.min(length, skip));
        final int size = Math.max(0, Math.min(length - start, amount));

        if (0 == size || sprite.isEmpty()) {
            return;
        }

        final Rectangle fill = switch (orientation) {
            case LeftToRight -> new Rectangle(area.getX1() + start, area.getY1(), size, area.Height);
            case RightToLeft -> new Rectangle(area.getX2() - start - size, area.getY1(), size, area.Height);
            case TopToBottom -> new Rectangle(area.getX1(), area.getY1() + start, area.Width, size);
            case BottomToTop -> new Rectangle(area.getX1(), area.getY2() - start - size, area.Width, size);
        };
        final int z = control.getZ();

        if (sprite.getAtlasSprite().isPresent()) {

            ModRenderHelper.paintTiledSprite(matrix, sprite, tint,
                    fill.getX1(), fill.getY1(), z, fill.Width, fill.Height,
                    Orientation.RightToLeft == orientation, Orientation.BottomToTop == orientation);
            return;
        }

        // map the sprite on the whole area and paint the part under the fill
        final float uSpan = sprite.getMaxU() - sprite.getMinU();
        final float vSpan = sprite.getMaxV() - sprite.getMinV();
        final float u0 = sprite.getMinU() + uSpan * (fill.getX1() - area.getX1()) / area.Width;
        final float u1 = sprite.getMinU() + uSpan * (fill.getX2() - area.getX1()) / area.Width;
        final float v0 = sprite.getMinV() + vSpan * (fill.getY1() - area.getY1()) / area.Height;
        final float v1 = sprite.getMinV() + vSpan * (fill.getY2() - area.getY1()) / area.Height;

        ModRenderHelper.paintTexturedQuad(matrix,
                sprite.getTextureLocation(), tint, fill.getX1(), fill.getY1(), fill.getX2(), fill.getY2(), z,
                u0, v0, u1, v1);
    }

    private double _value;
    private double _maxValue;
    private ISprite _barSprite;
    private Colour _barTint;
    @Nullable
    private ISprite _overlay;
    private Orientation _orientation;

    //endregion
}

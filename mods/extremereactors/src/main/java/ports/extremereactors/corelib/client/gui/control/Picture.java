/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.gui.control;

import com.mojang.blaze3d.vertex.PoseStack;
import ports.extremereactors.corelib.client.gui.DesiredDimension;
import ports.extremereactors.corelib.client.gui.ModContainerScreen;
import ports.extremereactors.corelib.client.gui.sprite.ISprite;
import ports.extremereactors.corelib.item.inventory.container.ModContainer;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * A sprite scaled to the area inside the padding. Without a desired size, a picture is as large as its sprite.
 */
public class Picture
        extends AbstractControl {

    public Picture(final ModContainerScreen<? extends ModContainer> gui, final String name,
                   final Supplier<ISprite> sprite) {

        super(gui, name);
        this._sprite = Objects.requireNonNull(sprite);
    }

    public Picture(final ModContainerScreen<? extends ModContainer> gui, final String name, final ISprite sprite) {
        this(gui, name, () -> sprite);
    }

    public Picture(final ModContainerScreen<? extends ModContainer> gui, final String name, final ISprite sprite,
                   final int width, final int height) {

        this(gui, name, sprite);
        this.setDesiredDimension(width, height);
    }

    public void setSprite(final ISprite sprite) {
        this._sprite = () -> sprite;
    }

    public void setSprite(final Supplier<ISprite> sprite) {
        this._sprite = Objects.requireNonNull(sprite);
    }

    //region AbstractControl

    @Override
    protected int getDefaultDimension(final DesiredDimension dimension) {

        final ISprite sprite = this._sprite.get();

        return DesiredDimension.Width == dimension ? sprite.getWidth() + this.getPadding().horizontal() :
                sprite.getHeight() + this.getPadding().vertical();
    }

    @Override
    public void onPaint(final PoseStack matrix, final float partialTicks, final int mouseX, final int mouseY) {
        this.paintSprite(matrix, this._sprite.get(), this.getScreenPaddingRect());
    }

    //endregion
    //region internals

    private Supplier<ISprite> _sprite;

    //endregion
}

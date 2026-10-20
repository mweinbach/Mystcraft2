/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.gui.control;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.Component;
import ports.extremereactors.corelib.client.gui.DesiredDimension;
import ports.extremereactors.corelib.client.gui.ModContainerScreen;
import ports.extremereactors.corelib.client.gui.layout.HorizontalAlignment;
import ports.extremereactors.corelib.client.gui.layout.VerticalAlignment;
import ports.extremereactors.corelib.data.geometry.Rectangle;
import ports.extremereactors.corelib.data.gfx.Colour;
import ports.extremereactors.corelib.item.inventory.container.ModContainer;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * A line of text, painted without a shadow in the theme text colour unless another colour is set.
 * An auto-sized label is as large as its text.
 */
public class Label
        extends AbstractControl {

    public Label(final ModContainerScreen<? extends ModContainer> gui, final String name, final String text) {
        this(gui, name, Component.literal(text));
    }

    public Label(final ModContainerScreen<? extends ModContainer> gui, final String name, final Component text) {

        super(gui, name);
        this._text = Objects.requireNonNull(text);
        this._autoSize = true;
        this._horizontalAlignment = HorizontalAlignment.Left;
        this._verticalAlignment = VerticalAlignment.Top;
    }

    public Component getText() {
        return this._text;
    }

    /** Set the text; with arguments the text is a {@link String#format} pattern. */
    public void setText(final String text, final Object... arguments) {
        this.setText(Component.literal(0 == arguments.length ? text : String.format(text, arguments)));
    }

    public void setText(final Component text) {
        this._text = Objects.requireNonNull(text);
    }

    public void setColor(@Nullable final Colour colour) {
        this._colour = colour;
    }

    public void setAutoSize(final boolean autoSize) {
        this._autoSize = autoSize;
    }

    public void setHorizontalAlignment(final HorizontalAlignment alignment) {
        this._horizontalAlignment = alignment;
    }

    public void setVerticalAlignment(final VerticalAlignment alignment) {
        this._verticalAlignment = alignment;
    }

    //region AbstractControl

    @Override
    protected int getDefaultDimension(final DesiredDimension dimension) {

        if (!this._autoSize) {
            return 0;
        }

        return DesiredDimension.Width == dimension ?
                this.getFont().width(this._text) + this.getPadding().horizontal() :
                this.getFont().lineHeight + this.getPadding().vertical();
    }

    @Override
    public void onPaint(final PoseStack matrix, final float partialTicks, final int mouseX, final int mouseY) {

        final Rectangle area = this.getScreenPaddingRect();
        final int textWidth = this.getFont().width(this._text);
        final int x = this._horizontalAlignment.align(area.getX1(), area.Width, textWidth);
        final int y = this._verticalAlignment.align(area.getY1(), area.Height, this.getFont().lineHeight);
        final Colour colour = null != this._colour ? this._colour :
                this.isEnabled() ? this.getTheme().TEXT_ENABLED_COLOR : this.getTheme().TEXT_DISABLED_COLOR;

        this.getFont().draw(matrix, this._text, x, y, colour.toARGB());
    }

    //endregion
    //region internals

    private Component _text;
    @Nullable
    private Colour _colour;
    private boolean _autoSize;
    private HorizontalAlignment _horizontalAlignment;
    private VerticalAlignment _verticalAlignment;

    //endregion
}

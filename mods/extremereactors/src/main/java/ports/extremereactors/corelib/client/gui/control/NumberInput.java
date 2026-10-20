/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.gui.control;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import ports.extremereactors.corelib.client.gui.DesiredDimension;
import ports.extremereactors.corelib.client.gui.ModContainerScreen;
import ports.extremereactors.corelib.client.gui.Theme;
import ports.extremereactors.corelib.client.gui.layout.HorizontalAlignment;
import ports.extremereactors.corelib.client.render.ModRenderHelper;
import ports.extremereactors.corelib.data.geometry.Rectangle;
import ports.extremereactors.corelib.event.Event;
import ports.extremereactors.corelib.event.IEvent;
import ports.extremereactors.corelib.item.inventory.container.ModContainer;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.IntSupplier;

/**
 * A field holding a number between a minimum and a maximum.
 * <p>
 * The mouse wheel and the up and down arrow keys change the value by a step (a larger one while shift is held) and
 * raise {@link #Changed} at once. Typed digits are only committed, and {@link #Changed} raised, when Enter is pressed
 * or the field loses the focus; Escape drops them.
 *
 * @param <T> the number type
 */
public abstract class NumberInput<T extends Number>
        extends AbstractControl {

    public final IEvent<BiConsumer<NumberInput<T>, T>> Changed;

    public T getValue() {
        return this._value;
    }

    public void setDisplaySuffix(final String suffix) {
        this._suffix = Objects.requireNonNull(suffix);
    }

    public void setHorizontalAlignment(final HorizontalAlignment alignment) {
        this._alignment = Objects.requireNonNull(alignment);
    }

    /** An integer field. */
    public static class IntNumberInput
            extends NumberInput<Integer>
            implements IntSupplier {

        public IntNumberInput(final ModContainerScreen<? extends ModContainer> gui, final String name,
                              final int min, final int max, final int value) {

            super(gui, name, Math.max(min, Math.min(max, value)));
            this._min = min;
            this._max = Math.max(min, max);
            this._step = 1;
            this._shiftStep = 1;
        }

        /** Set the value shown, without raising {@link #Changed}. */
        public void setValue(final int value) {
            this.updateValue(this.clamp(value));
        }

        public void setStep(final int step, final int shiftStep) {

            this._step = step;
            this._shiftStep = shiftStep;
        }

        //region IntSupplier

        @Override
        public int getAsInt() {
            return this.getValue();
        }

        //endregion
        //region NumberInput

        @Override
        protected Integer stepped(final Integer value, final int direction, final boolean largeStep) {
            return this.clamp((long) value + (long) direction * (largeStep ? this._shiftStep : this._step));
        }

        @Nullable
        @Override
        protected Integer parse(final String text) {

            try {
                return this.clamp(Long.parseLong(text));
            } catch (NumberFormatException ex) {
                return null;
            }
        }

        @Override
        protected boolean acceptsMinus() {
            return this._min < 0;
        }

        //endregion
        //region internals

        private int clamp(final long value) {
            return (int) Math.max(this._min, Math.min(this._max, value));
        }

        private final int _min;
        private final int _max;
        private int _step;
        private int _shiftStep;

        //endregion
    }

    //region AbstractControl

    @Override
    public boolean canFocus() {
        return this.isEnabled();
    }

    @Override
    public void onKillFocus() {

        super.onKillFocus();
        this.commitEdit();
    }

    @Override
    public boolean onMouseClicked(final double mouseX, final double mouseY, final int button) {
        return this.isEnabled();
    }

    @Override
    public boolean onMouseScrolled(final double mouseX, final double mouseY, final double delta) {

        if (!this.isEnabled() || 0 == delta) {
            return false;
        }

        this.step(delta > 0 ? 1 : -1);
        return true;
    }

    @Override
    public boolean onKeyPressed(final int keyCode, final int scanCode, final int modifiers) {

        if (!this.hasFocus()) {
            return false;
        }

        switch (keyCode) {

            case InputConstants.KEY_ESCAPE:
                if (null == this._edit) {
                    return false;
                }

                this._edit = null;
                return true;

            case InputConstants.KEY_RETURN:
            case InputConstants.KEY_NUMPADENTER:
                this.commitEdit();
                break;

            case InputConstants.KEY_UP:
                this.step(1);
                break;

            case InputConstants.KEY_DOWN:
                this.step(-1);
                break;

            case InputConstants.KEY_BACKSPACE:
                final String text = this.getEditText();

                if (!text.isEmpty()) {
                    this.setEdit(text.substring(0, text.length() - 1));
                }
                break;

            default:
                break;
        }

        // a focused field takes every key, so typing never triggers the screen key bindings
        return true;
    }

    @Override
    public boolean onCharTyped(final char character, final int modifiers) {

        if (!this.hasFocus()) {
            return false;
        }

        final String text = this.getEditText();

        if (Character.isDigit(character) || ('-' == character && text.isEmpty() && this.acceptsMinus())) {
            if (text.length() < MAX_DIGITS) {
                this.setEdit(text + character);
            }
        }

        return true;
    }

    @Override
    protected int getDefaultDimension(final DesiredDimension dimension) {
        return DesiredDimension.Width == dimension ? 0 : this.getFont().lineHeight + this.getPadding().vertical();
    }

    @Override
    public void onPaintBackground(final PoseStack matrix, final float partialTicks, final int mouseX,
                                  final int mouseY) {

        if (this.hasBackground()) {

            super.onPaintBackground(matrix, partialTicks, mouseX, mouseY);
            return;
        }

        final Rectangle bounds = this.getScreenBounds();
        final Theme theme = this.getTheme();

        ModRenderHelper.paint3DBox(matrix, bounds.getX1(), bounds.getY1(), bounds.getX2(), bounds.getY2(),
                this.getZ(), theme.TEXTFIELD_NORMAL_3D_BORDER_DARK, theme.TEXTFIELD_NORMAL_3D_BORDER_LIGHT,
                theme.TEXTFIELD_NORMAL_3D_GRADIENT_LIGHT, theme.TEXTFIELD_NORMAL_3D_GRADIENT_DARK);
    }

    @Override
    public void onPaint(final PoseStack matrix, final float partialTicks, final int mouseX, final int mouseY) {

        final Rectangle area = this.getScreenPaddingRect();
        final Font font = this.getFont();
        final Theme theme = this.getTheme();
        final String text = this.getEditText();
        final String display = text + this._suffix;
        final int x = this._alignment.align(area.getX1(), area.Width, font.width(display));
        final int y = area.getY1() + Math.max(0, (area.Height - font.lineHeight) / 2) + 1;
        final int colour = (this.isEnabled() ? theme.TEXT_ENABLED_COLOR : theme.TEXT_DISABLED_COLOR).toARGB();

        font.draw(matrix, display, x, y, colour);

        if (this.hasFocus() && Util.getMillis() / 500 % 2 == 0) {

            final int caretX = x + font.width(text);

            ModRenderHelper.paintSolidRect(matrix, caretX, y - 1, caretX + 1, y + font.lineHeight - 1,
                    this.getZ() + 1, theme.TEXTFIELD_CARET);
        }
    }

    //endregion
    //region internals

    protected NumberInput(final ModContainerScreen<? extends ModContainer> gui, final String name, final T value) {

        super(gui, name);
        this.Changed = new Event<>();
        this._value = Objects.requireNonNull(value);
        this._suffix = "";
        this._alignment = HorizontalAlignment.Left;
        this.setPadding(3);
    }

    /** The value moved by one step up (direction 1) or down (direction -1), within the limits. */
    protected abstract T stepped(T value, int direction, boolean largeStep);

    /** The typed text as a value within the limits, or null if it is not a number. */
    @Nullable
    protected abstract T parse(String text);

    protected abstract boolean acceptsMinus();

    protected String format(final T value) {
        return value.toString();
    }

    /** Show a new value; a pending edit is kept, so that a data update does not overwrite what is being typed. */
    protected void updateValue(final T value) {
        this._value = Objects.requireNonNull(value);
    }

    private void step(final int direction) {

        final T base = null != this._edit ? Objects.requireNonNullElse(this.parse(this._edit), this._value) :
                this._value;

        this._edit = null;
        this.change(this.stepped(base, direction, Screen.hasShiftDown()));
    }

    private void commitEdit() {

        if (null == this._edit) {
            return;
        }

        final T parsed = this.parse(this._edit);

        this._edit = null;

        if (null != parsed) {
            this.change(parsed);
        }
    }

    private void change(final T value) {

        if (value.equals(this._value)) {
            return;
        }

        this._value = value;
        this.Changed.raise(handler -> handler.accept(this, value));
    }

    private String getEditText() {
        return null != this._edit ? this._edit : this.format(this._value);
    }

    private void setEdit(final String text) {
        this._edit = text;
    }

    private static final int MAX_DIGITS = 11;

    private T _value;
    @Nullable
    private String _edit;
    private String _suffix;
    private HorizontalAlignment _alignment;

    //endregion
}

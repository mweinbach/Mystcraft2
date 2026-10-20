/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.gui.control;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import ports.extremereactors.corelib.client.gui.DesiredDimension;
import ports.extremereactors.corelib.client.gui.ModContainerScreen;
import ports.extremereactors.corelib.client.gui.Theme;
import ports.extremereactors.corelib.client.gui.layout.HorizontalAlignment;
import ports.extremereactors.corelib.client.render.ModRenderHelper;
import ports.extremereactors.corelib.data.geometry.Rectangle;
import ports.extremereactors.corelib.item.inventory.container.ModContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A single line text field.
 * <p>
 * Typed characters are checked against the filter; after every edit the constraints, in the order they were added,
 * may replace the whole text. The optional suffix is painted after the text but is not part of it.
 */
public class TextInput
        extends AbstractControl {

    public TextInput(final ModContainerScreen<? extends ModContainer> gui, final String name) {
        this(gui, name, "");
    }

    public TextInput(final ModContainerScreen<? extends ModContainer> gui, final String name, final String text) {

        super(gui, name);
        this._filter = c -> true;
        this._constraints = new ArrayList<>();
        this._maxLength = DEFAULT_MAX_LENGTH;
        this._suffix = "";
        this._alignment = HorizontalAlignment.Left;
        this.setPadding(3);
        this.setText(text);
    }

    public String getText() {
        return this._text;
    }

    public void setText(final String text) {

        this._text = this.applyConstraints(clip(Objects.requireNonNull(text), this._maxLength));
        this._caret = this._text.length();
    }

    /** The text as an integer, or 0 if it is not one. */
    public int intValue() {

        try {
            return Integer.parseInt(this._text.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    public void setFilter(final Predicate<Character> filter) {
        this._filter = Objects.requireNonNull(filter);
    }

    /**
     * Add a constraint: it receives the edited text and returns the text to use instead, if the edited one is not
     * acceptable.
     */
    public void addConstraint(final Function<String, Optional<String>> constraint) {

        this._constraints.add(Objects.requireNonNull(constraint));
        this._text = this.applyConstraints(this._text);
        this._caret = Math.min(this._caret, this._text.length());
    }

    public void setMaxLength(final int maxLength) {

        this._maxLength = Math.max(1, maxLength);
        this._text = clip(this._text, this._maxLength);
        this._caret = Math.min(this._caret, this._text.length());
    }

    public void setDisplaySuffix(final String suffix) {
        this._suffix = Objects.requireNonNull(suffix);
    }

    public void setHorizontalAlignment(final HorizontalAlignment alignment) {
        this._alignment = Objects.requireNonNull(alignment);
    }

    //region AbstractControl

    @Override
    public boolean canFocus() {
        return this.isEnabled();
    }

    @Override
    public void onSetFocus() {

        super.onSetFocus();
        this._focusTime = Util.getMillis();
    }

    @Override
    public boolean onMouseClicked(final double mouseX, final double mouseY, final int button) {

        if (!this.isEnabled()) {
            return false;
        }

        final Rectangle area = this.getScreenPaddingRect();
        final Font font = this.getFont();
        final int textX = this.getTextX(area);
        final int offset = (int) mouseX - textX;

        this._caret = offset <= 0 ? 0 : font.plainSubstrByWidth(this._text, offset).length();
        this._focusTime = Util.getMillis();
        return true;
    }

    @Override
    public boolean onKeyPressed(final int keyCode, final int scanCode, final int modifiers) {

        if (!this.hasFocus()) {
            return false;
        }

        switch (keyCode) {

            case InputConstants.KEY_ESCAPE:
                return false;

            case InputConstants.KEY_BACKSPACE:
                if (this._caret > 0) {
                    this.edit(this._text.substring(0, this._caret - 1) + this._text.substring(this._caret),
                            this._caret - 1);
                }
                break;

            case InputConstants.KEY_DELETE:
                if (this._caret < this._text.length()) {
                    this.edit(this._text.substring(0, this._caret) + this._text.substring(this._caret + 1),
                            this._caret);
                }
                break;

            case InputConstants.KEY_LEFT:
                this._caret = Math.max(0, this._caret - 1);
                break;

            case InputConstants.KEY_RIGHT:
                this._caret = Math.min(this._text.length(), this._caret + 1);
                break;

            case InputConstants.KEY_HOME:
                this._caret = 0;
                break;

            case InputConstants.KEY_END:
                this._caret = this._text.length();
                break;

            default:
                if (Screen.isPaste(keyCode)) {
                    this.insert(Minecraft.getInstance().keyboardHandler.getClipboard());
                } else if (Screen.isCopy(keyCode)) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(this._text);
                }
                break;
        }

        this._focusTime = Util.getMillis();
        // a focused field takes every key, so typing never triggers the screen key bindings
        return true;
    }

    @Override
    public boolean onCharTyped(final char character, final int modifiers) {

        if (!this.hasFocus()) {
            return false;
        }

        this.insert(String.valueOf(character));
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
        final int x = this.getTextX(area);
        final int y = area.getY1() + Math.max(0, (area.Height - font.lineHeight) / 2) + 1;
        final int colour = (this.isEnabled() ? theme.TEXT_ENABLED_COLOR : theme.TEXT_DISABLED_COLOR).toARGB();

        font.draw(matrix, this.getDisplayText(), x, y, colour);

        if (this.hasFocus() && (Util.getMillis() - this._focusTime) / 500 % 2 == 0) {

            final int caretX = x + font.width(this._text.substring(0, this._caret));

            ModRenderHelper.paintSolidRect(matrix, caretX, y - 1, caretX + 1, y + font.lineHeight - 1,
                    this.getZ() + 1, theme.TEXTFIELD_CARET);
        }
    }

    //endregion
    //region internals

    private String getDisplayText() {
        return this._text + this._suffix;
    }

    private int getTextX(final Rectangle area) {
        return this._alignment.align(area.getX1(), area.Width, this.getFont().width(this.getDisplayText()));
    }

    private void insert(final String inserted) {

        final StringBuilder accepted = new StringBuilder();

        for (final char c : inserted.toCharArray()) {
            if (c >= ' ' && c != 127 && this._filter.test(c)) {
                accepted.append(c);
            }
        }

        final int room = this._maxLength - this._text.length();

        if (accepted.isEmpty() || room <= 0) {
            return;
        }

        final String added = clip(accepted.toString(), room);

        this.edit(this._text.substring(0, this._caret) + added + this._text.substring(this._caret),
                this._caret + added.length());
    }

    private void edit(final String text, final int caret) {

        final String constrained = this.applyConstraints(text);

        this._caret = constrained.equals(text) ? caret : constrained.length();
        this._text = constrained;
        this._focusTime = Util.getMillis();
    }

    private String applyConstraints(final String text) {

        String result = text;

        for (final Function<String, Optional<String>> constraint : this._constraints) {
            result = constraint.apply(result).orElse(result);
        }

        return clip(result, this._maxLength);
    }

    private static String clip(final String text, final int maxLength) {
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }

    private static final int DEFAULT_MAX_LENGTH = 32;

    private final List<Function<String, Optional<String>>> _constraints;
    private Predicate<Character> _filter;
    private String _text;
    private int _caret;
    private int _maxLength;
    private String _suffix;
    private HorizontalAlignment _alignment;
    private long _focusTime;

    //endregion
}

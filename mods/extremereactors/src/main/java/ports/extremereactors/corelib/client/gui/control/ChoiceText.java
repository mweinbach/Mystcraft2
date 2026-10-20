/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.gui.control;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import ports.extremereactors.corelib.client.gui.DesiredDimension;
import ports.extremereactors.corelib.client.gui.ModContainerScreen;
import ports.extremereactors.corelib.client.gui.Theme;
import ports.extremereactors.corelib.client.render.ModRenderHelper;
import ports.extremereactors.corelib.data.geometry.Rectangle;
import ports.extremereactors.corelib.data.gfx.Colour;
import ports.extremereactors.corelib.event.Event;
import ports.extremereactors.corelib.event.IEvent;
import ports.extremereactors.corelib.item.inventory.container.ModContainer;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * A field showing one of a fixed list of choices. A left click selects the next choice, a right click the previous
 * one; {@link #Changed} is raised whenever the selection changes.
 *
 * @param <T> the value identifying each choice
 */
public class ChoiceText<T>
        extends AbstractControl {

    public final IEvent<BiConsumer<ChoiceText<T>, T>> Changed;

    public ChoiceText(final ModContainerScreen<? extends ModContainer> gui, final String name,
                      final List<T> validIndices) {

        super(gui, name);
        this.Changed = new Event<>();
        this._indices = ImmutableList.copyOf(validIndices);
        this._texts = new HashMap<>();
        this._selected = this._indices.isEmpty() ? null : this._indices.get(0);
    }

    public void addText(final T index, final Component text) {
        this._texts.put(Objects.requireNonNull(index), Objects.requireNonNull(text));
    }

    public List<T> getValidIndices() {
        return this._indices;
    }

    @Nullable
    public T getSelectedIndex() {
        return this._selected;
    }

    public void setSelectedIndex(final T index) {

        if (!this._indices.contains(index) || index.equals(this._selected)) {
            return;
        }

        this._selected = index;
        this.Changed.raise(handler -> handler.accept(this, index));
    }

    //region AbstractControl

    @Override
    public boolean onMouseClicked(final double mouseX, final double mouseY, final int button) {

        if (!this.isEnabled() || this._indices.size() < 2 || button > 1) {
            return false;
        }

        final int current = Math.max(0, this._indices.indexOf(this._selected));
        final int size = this._indices.size();
        final int next = 0 == button ? (current + 1) % size : (current + size - 1) % size;

        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        this.setSelectedIndex(this._indices.get(next));
        return true;
    }

    @Override
    protected int getDefaultDimension(final DesiredDimension dimension) {

        if (DesiredDimension.Height == dimension) {
            return this.getFont().lineHeight + 7 + this.getPadding().vertical();
        }

        int width = 0;

        for (final Component text : this._texts.values()) {
            width = Math.max(width, this.getFont().width(text));
        }

        return width + TEXT_MARGIN + this.getFont().lineHeight + 7 + this.getPadding().horizontal();
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
        final int z = this.getZ();
        final int x1 = bounds.getX1();
        final int y1 = bounds.getY1();
        final int x2 = bounds.getX2();
        final int y2 = bounds.getY2();

        ModRenderHelper.paintFrame(matrix, x1, y1, x2, y2, z, theme.DARK_OUTLINE_COLOR);
        ModRenderHelper.paintSolidRect(matrix, x1 + 1, y1 + 1, x2 - 1, y2 - 1, z, theme.FLAT_BACKGROUND_COLOR);

        // the cycle marker: a small raised square with a diagonal line, at the right end
        final int size = bounds.Height - 6;
        final int mx2 = x2 - 3;
        final int mx1 = mx2 - size;
        final int my1 = y1 + 3;
        final int my2 = my1 + size;

        if (size > 2) {

            ModRenderHelper.paint3DBox(matrix, mx1, my1, mx2, my2, z,
                    theme.BUTTON_NORMAL_3D_BORDER_LIGHT, theme.BUTTON_NORMAL_3D_BORDER_DARK,
                    theme.BUTTON_NORMAL_3D_GRADIENT_LIGHT, theme.BUTTON_NORMAL_3D_GRADIENT_DARK);

            for (int i = 2; i < size - 2; ++i) {
                ModRenderHelper.paintSolidRect(matrix, mx2 - 1 - i, my1 + i, mx2 - i, my1 + i + 1, z, Colour.WHITE);
            }
        }
    }

    @Override
    public void onPaint(final PoseStack matrix, final float partialTicks, final int mouseX, final int mouseY) {

        if (null == this._selected) {
            return;
        }

        final Component text = this._texts.get(this._selected);

        if (null == text) {
            return;
        }

        final Rectangle bounds = this.getScreenPaddingRect();
        final Font font = this.getFont();
        final Theme theme = this.getTheme();
        final int y = bounds.getY1() + (bounds.Height - font.lineHeight) / 2 + 1;

        font.draw(matrix, text, bounds.getX1() + TEXT_MARGIN, y,
                (this.isEnabled() ? theme.TEXT_ENABLED_COLOR : theme.TEXT_DISABLED_COLOR).toARGB());
    }

    //endregion
    //region internals

    private static final int TEXT_MARGIN = 4;

    private final List<T> _indices;
    private final Map<T, Component> _texts;
    @Nullable
    private T _selected;

    //endregion
}

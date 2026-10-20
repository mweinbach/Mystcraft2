/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.gui.control;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import ports.extremereactors.corelib.client.gui.ButtonState;
import ports.extremereactors.corelib.client.gui.DesiredDimension;
import ports.extremereactors.corelib.client.gui.ModContainerScreen;
import ports.extremereactors.corelib.client.gui.Theme;
import ports.extremereactors.corelib.client.gui.sprite.ISprite;
import ports.extremereactors.corelib.client.render.ModRenderHelper;
import ports.extremereactors.corelib.data.geometry.Rectangle;
import ports.extremereactors.corelib.data.gfx.Colour;
import ports.extremereactors.corelib.item.inventory.container.ModContainer;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * A clickable control showing a text, an icon for its current {@link ButtonState}, or both.
 * <p>
 * Without a background sprite the button is painted as a raised box in the theme colours, sunken while active.
 */
public abstract class AbstractButtonControl
        extends AbstractControl {

    public Component getText() {
        return this._text;
    }

    public void setText(final Component text) {
        this._text = Objects.requireNonNull(text);
    }

    /** Use the sprite as the icon for each of the given states. */
    public void setIconForState(final ISprite sprite, final ButtonState... states) {

        for (final ButtonState state : states) {
            this._icons.put(state, sprite);
        }
    }

    /** Let translucent icons and backgrounds blend with what is under them. */
    public void enablePaintBlending(final boolean blend) {
        this._blend = blend;
    }

    /** The state the button would be painted in, for the given mouse position. */
    public ButtonState getState(final int mouseX, final int mouseY) {
        return ButtonState.of(this.isActiveState(), this.isEnabled() && this.isMouseOver(mouseX, mouseY),
                !this.isEnabled());
    }

    //region AbstractControl

    @Override
    protected int getDefaultDimension(final DesiredDimension dimension) {

        if (DesiredDimension.Width == dimension) {
            return this.getFont().width(this._text) + 8 + this.getPadding().horizontal();
        }

        return this.getFont().lineHeight + 7 + this.getPadding().vertical();
    }

    @Override
    public void onPaintBackground(final PoseStack matrix, final float partialTicks, final int mouseX,
                                  final int mouseY) {

        final ISprite background = this.getBackgroundSprite();

        if (null != background) {

            this.paintBlended(() -> this.paintSprite(matrix, background, this.getScreenBounds()));
            return;
        }

        final Rectangle bounds = this.getScreenBounds();
        final Theme theme = this.getTheme();
        final ButtonState state = this.getState(mouseX, mouseY);
        final int z = this.getZ();
        final int x1 = bounds.getX1();
        final int y1 = bounds.getY1();
        final int x2 = bounds.getX2();
        final int y2 = bounds.getY2();

        ModRenderHelper.paintFrame(matrix, x1, y1, x2, y2, z, theme.DARK_OUTLINE_COLOR);

        if (state.isDisabled()) {
            ModRenderHelper.paint3DBox(matrix, x1 + 1, y1 + 1, x2 - 1, y2 - 1, z,
                    theme.BUTTON_DISABLED_3D_BORDER_LIGHT, theme.BUTTON_DISABLED_3D_BORDER_DARK,
                    theme.BUTTON_DISABLED_3D_GRADIENT_LIGHT, theme.BUTTON_DISABLED_3D_GRADIENT_DARK);
        } else if (state.isActive()) {
            ModRenderHelper.paint3DBox(matrix, x1 + 1, y1 + 1, x2 - 1, y2 - 1, z,
                    theme.BUTTON_ACTIVE_3D_BORDER_DARK, theme.BUTTON_ACTIVE_3D_BORDER_LIGHT,
                    theme.BUTTON_ACTIVE_3D_GRADIENT_LIGHT, theme.BUTTON_ACTIVE_3D_GRADIENT_DARK);
        } else if (state.isHighlighted()) {
            ModRenderHelper.paint3DBox(matrix, x1 + 1, y1 + 1, x2 - 1, y2 - 1, z,
                    theme.BUTTON_HIGHLIGHTED_3D_BORDER_LIGHT, theme.BUTTON_HIGHLIGHTED_3D_BORDER_DARK,
                    theme.BUTTON_HIGHLIGHTED_3D_GRADIENT_LIGHT, theme.BUTTON_HIGHLIGHTED_3D_GRADIENT_DARK);
        } else {
            ModRenderHelper.paint3DBox(matrix, x1 + 1, y1 + 1, x2 - 1, y2 - 1, z,
                    theme.BUTTON_NORMAL_3D_BORDER_LIGHT, theme.BUTTON_NORMAL_3D_BORDER_DARK,
                    theme.BUTTON_NORMAL_3D_GRADIENT_LIGHT, theme.BUTTON_NORMAL_3D_GRADIENT_DARK);
        }
    }

    @Override
    public void onPaint(final PoseStack matrix, final float partialTicks, final int mouseX, final int mouseY) {

        final ButtonState state = this.getState(mouseX, mouseY);
        final ISprite icon = this.getIcon(state);

        if (null != icon) {
            this.paintBlended(() -> this.paintSprite(matrix, icon, this.getScreenPaddingRect()));
        }

        if (!this._text.getString().isEmpty()) {

            final Rectangle bounds = this.getScreenBounds();
            final int width = this.getFont().width(this._text);
            final int x = bounds.getX1() + (bounds.Width - width) / 2;
            final int y = bounds.getY1() + (bounds.Height - this.getFont().lineHeight) / 2 + 1;
            final Colour colour = state.isDisabled() ? this.getTheme().TEXT_DISABLED_COLOR :
                    this.getTheme().TEXT_ENABLED_COLOR;

            this.getFont().draw(matrix, this._text, x, y, colour.toARGB());
        }
    }

    @Override
    public boolean onMouseClicked(final double mouseX, final double mouseY, final int button) {

        if (!this.isEnabled()) {
            return false;
        }

        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        this.onClick(button);
        return true;
    }

    //endregion
    //region internals

    protected AbstractButtonControl(final ModContainerScreen<? extends ModContainer> gui, final String name,
                                    final Component text) {

        super(gui, name);
        this._text = Objects.requireNonNull(text);
        this._icons = new EnumMap<>(ButtonState.class);
    }

    /** True if the button is pressed in; only switches can be. */
    protected abstract boolean isActiveState();

    protected abstract void onClick(int mouseButton);

    /** The icon for a state; states without one fall back to the state without highlight, then to the default. */
    @Nullable
    protected ISprite getIcon(final ButtonState state) {

        ISprite icon = this._icons.get(state);

        if (null == icon) {
            icon = this._icons.get(state.getBase());
        }

        if (null == icon) {
            icon = this._icons.get(ButtonState.Default);
        }

        return icon;
    }

    private void paintBlended(final Runnable painter) {

        if (!this._blend) {

            painter.run();
            return;
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        painter.run();
        RenderSystem.disableBlend();
    }

    private final Map<ButtonState, ISprite> _icons;
    private Component _text;
    private boolean _blend;

    //endregion
}

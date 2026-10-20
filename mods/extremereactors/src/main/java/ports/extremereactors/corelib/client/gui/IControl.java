/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.Component;
import ports.extremereactors.corelib.client.gui.sprite.ISprite;
import ports.extremereactors.corelib.data.geometry.Point;
import ports.extremereactors.corelib.data.geometry.Rectangle;
import ports.extremereactors.corelib.data.gfx.Colour;
import ports.extremereactors.corelib.item.inventory.container.ModContainer;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * An element of a {@link ModContainerScreen}.
 * <p>
 * A control's bounds are relative to its parent. Painting and mouse coordinates are screen coordinates; use
 * {@link #controlToScreen} and {@link #screenToControl} to convert. A container's layout engine sizes and places its
 * children from their desired dimensions and layout hints.
 */
public interface IControl {

    String getName();

    ModContainerScreen<? extends ModContainer> getGui();

    Optional<IControlContainer> getParent();

    void setParent(@Nullable IControlContainer parent);

    //region geometry and layout

    /** The position and size of this control, relative to its parent. */
    Rectangle getBounds();

    void setBounds(Rectangle bounds);

    /** The area inside the padding, in control coordinates. */
    default Rectangle getPaddingRect() {

        final Rectangle bounds = this.getBounds();
        final Padding padding = this.getPadding();

        return new Rectangle(padding.left(), padding.top(), bounds.Width - padding.horizontal(),
                bounds.Height - padding.vertical());
    }

    Point controlToScreen(int x, int y);

    Point screenToControl(int x, int y);

    /** True if the screen point is inside this control. */
    default boolean hitTest(final int screenX, final int screenY) {

        final Point local = this.screenToControl(screenX, screenY);
        final Rectangle bounds = this.getBounds();

        return local.X >= 0 && local.Y >= 0 && local.X < bounds.Width && local.Y < bounds.Height;
    }

    /**
     * The size the control would like to have. Without an explicit value this is the control's natural size, or 0
     * when it has none and takes whatever room the layout gives it.
     */
    int getDesiredDimension(DesiredDimension dimension);

    /** Set the desired size; a value of 0 or less clears it. */
    void setDesiredDimension(DesiredDimension dimension, int value);

    default void setDesiredDimension(final int width, final int height) {

        this.setDesiredDimension(DesiredDimension.Width, width);
        this.setDesiredDimension(DesiredDimension.Height, height);
    }

    @Nullable
    Object getLayoutEngineHint();

    void setLayoutEngineHint(@Nullable Object hint);

    Padding getPadding();

    void setPadding(Padding padding);

    default void setPadding(final int padding) {
        this.setPadding(Padding.get(padding));
    }

    //endregion
    //region state

    boolean isVisible();

    void setVisible(boolean visible);

    boolean isEnabled();

    void setEnabled(boolean enabled);

    /** True if this control and all of its parents are visible. */
    default boolean isShowing() {
        return this.isVisible() && this.getParent().map(IControl::isShowing).orElse(true);
    }

    void setTag(@Nullable Object tag);

    <T> Optional<T> getTag();

    void setBackground(ISprite sprite);

    void setBackground(Colour colour);

    //endregion
    //region tooltips

    /**
     * The tooltip lines. A line may contain {@code @N} placeholders that are replaced by the N-th object of
     * {@link #getTooltipsObjects()}.
     */
    List<Component> getTooltips();

    List<Object> getTooltipsObjects();

    void setTooltips(Component... lines);

    void setTooltips(List<Component> lines);

    void setTooltips(List<Component> lines, List<Object> objects);

    /** Show the tooltips of another control, as they are at the time they are shown. */
    void useTooltipsFrom(@Nullable IControl other);

    //endregion
    //region painting

    void onPaintBackground(PoseStack matrix, float partialTicks, int mouseX, int mouseY);

    void onPaint(PoseStack matrix, float partialTicks, int mouseX, int mouseY);

    //endregion
    //region input

    /** The deepest visible control at the screen point, this one included. */
    Optional<IControl> findControl(int screenX, int screenY);

    boolean onMouseClicked(double mouseX, double mouseY, int button);

    boolean onMouseReleased(double mouseX, double mouseY, int button);

    boolean onMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY);

    boolean onMouseScrolled(double mouseX, double mouseY, double delta);

    boolean onKeyPressed(int keyCode, int scanCode, int modifiers);

    boolean onCharTyped(char character, int modifiers);

    /** True if clicking this control gives it the keyboard focus. */
    boolean canFocus();

    void onSetFocus();

    void onKillFocus();

    boolean hasFocus();

    //endregion
}

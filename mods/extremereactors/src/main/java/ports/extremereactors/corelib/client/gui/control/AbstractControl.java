/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.gui.control;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import ports.extremereactors.corelib.client.gui.DesiredDimension;
import ports.extremereactors.corelib.client.gui.IControl;
import ports.extremereactors.corelib.client.gui.IControlContainer;
import ports.extremereactors.corelib.client.gui.ModContainerScreen;
import ports.extremereactors.corelib.client.gui.Padding;
import ports.extremereactors.corelib.client.gui.Theme;
import ports.extremereactors.corelib.client.gui.sprite.ISprite;
import ports.extremereactors.corelib.client.render.ModRenderHelper;
import ports.extremereactors.corelib.data.geometry.Point;
import ports.extremereactors.corelib.data.geometry.Rectangle;
import ports.extremereactors.corelib.data.gfx.Colour;
import ports.extremereactors.corelib.item.inventory.container.ModContainer;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The common state and behaviour of controls: geometry, visibility, tooltips and an optional background.
 */
public abstract class AbstractControl
        implements IControl {

    //region IControl

    @Override
    public String getName() {
        return this._name;
    }

    @Override
    public ModContainerScreen<? extends ModContainer> getGui() {
        return this._gui;
    }

    @Override
    public Optional<IControlContainer> getParent() {
        return Optional.ofNullable(this._parent);
    }

    @Override
    public void setParent(@Nullable final IControlContainer parent) {
        this._parent = parent;
    }

    @Override
    public Rectangle getBounds() {
        return this._bounds;
    }

    @Override
    public void setBounds(final Rectangle bounds) {
        this._bounds = Objects.requireNonNull(bounds);
    }

    @Override
    public Point controlToScreen(final int x, final int y) {

        final int parentX = this._bounds.getX1() + x;
        final int parentY = this._bounds.getY1() + y;

        if (null != this._parent) {
            return this._parent.controlToScreen(parentX, parentY);
        }

        return new Point(this._gui.getGuiLeft() + parentX, this._gui.getGuiTop() + parentY);
    }

    @Override
    public Point screenToControl(final int x, final int y) {

        final Point origin = this.controlToScreen(0, 0);

        return new Point(x - origin.X, y - origin.Y);
    }

    @Override
    public int getDesiredDimension(final DesiredDimension dimension) {

        final int value = DesiredDimension.Width == dimension ? this._desiredWidth : this._desiredHeight;

        return value > 0 ? value : this.getDefaultDimension(dimension);
    }

    @Override
    public void setDesiredDimension(final DesiredDimension dimension, final int value) {

        if (DesiredDimension.Width == dimension) {
            this._desiredWidth = Math.max(0, value);
        } else {
            this._desiredHeight = Math.max(0, value);
        }
    }

    @Nullable
    @Override
    public Object getLayoutEngineHint() {
        return this._layoutHint;
    }

    @Override
    public void setLayoutEngineHint(@Nullable final Object hint) {
        this._layoutHint = hint;
    }

    @Override
    public Padding getPadding() {
        return this._padding;
    }

    @Override
    public void setPadding(final Padding padding) {
        this._padding = Objects.requireNonNull(padding);
    }

    @Override
    public boolean isVisible() {
        return this._visible;
    }

    @Override
    public void setVisible(final boolean visible) {
        this._visible = visible;
    }

    @Override
    public boolean isEnabled() {
        return this._enabled;
    }

    @Override
    public void setEnabled(final boolean enabled) {
        this._enabled = enabled;
    }

    @Override
    public void setTag(@Nullable final Object tag) {
        this._tag = tag;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Optional<T> getTag() {
        return Optional.ofNullable((T) this._tag);
    }

    @Override
    public void setBackground(final ISprite sprite) {

        this._backgroundSprite = sprite;
        this._backgroundColour = null;
    }

    @Override
    public void setBackground(final Colour colour) {

        this._backgroundColour = colour;
        this._backgroundSprite = null;
    }

    @Override
    public List<Component> getTooltips() {
        return null != this._tooltipsSource ? this._tooltipsSource.getTooltips() : this._tooltips;
    }

    @Override
    public List<Object> getTooltipsObjects() {
        return null != this._tooltipsSource ? this._tooltipsSource.getTooltipsObjects() : this._tooltipsObjects;
    }

    @Override
    public void setTooltips(final Component... lines) {
        this.setTooltips(ImmutableList.copyOf(lines), ImmutableList.of());
    }

    @Override
    public void setTooltips(final List<Component> lines) {
        this.setTooltips(lines, ImmutableList.of());
    }

    @Override
    public void setTooltips(final List<Component> lines, final List<Object> objects) {

        this._tooltips = ImmutableList.copyOf(lines);
        this._tooltipsObjects = ImmutableList.copyOf(objects);
        this._tooltipsSource = null;
    }

    @Override
    public void useTooltipsFrom(@Nullable final IControl other) {
        this._tooltipsSource = other == this ? null : other;
    }

    @Override
    public void onPaintBackground(final PoseStack matrix, final float partialTicks, final int mouseX,
                                  final int mouseY) {

        if (null != this._backgroundSprite) {
            this.paintSprite(matrix, this._backgroundSprite, this.getScreenBounds());
        } else if (null != this._backgroundColour) {
            this.paintSolidRect(matrix, this.getScreenBounds(), this._backgroundColour);
        }
    }

    @Override
    public void onPaint(final PoseStack matrix, final float partialTicks, final int mouseX, final int mouseY) {
    }

    @Override
    public Optional<IControl> findControl(final int screenX, final int screenY) {
        return this.isVisible() && this.hitTest(screenX, screenY) ? Optional.of(this) : Optional.empty();
    }

    @Override
    public boolean onMouseClicked(final double mouseX, final double mouseY, final int button) {
        return false;
    }

    @Override
    public boolean onMouseReleased(final double mouseX, final double mouseY, final int button) {
        return false;
    }

    @Override
    public boolean onMouseDragged(final double mouseX, final double mouseY, final int button, final double dragX,
                                  final double dragY) {
        return false;
    }

    @Override
    public boolean onMouseScrolled(final double mouseX, final double mouseY, final double delta) {
        return false;
    }

    @Override
    public boolean onKeyPressed(final int keyCode, final int scanCode, final int modifiers) {
        return false;
    }

    @Override
    public boolean onCharTyped(final char character, final int modifiers) {
        return false;
    }

    @Override
    public boolean canFocus() {
        return false;
    }

    @Override
    public void onSetFocus() {
        this._focused = true;
    }

    @Override
    public void onKillFocus() {
        this._focused = false;
    }

    @Override
    public boolean hasFocus() {
        return this._focused;
    }

    //endregion
    //region internals

    protected AbstractControl(final ModContainerScreen<? extends ModContainer> gui, final String name) {

        this._gui = Objects.requireNonNull(gui);
        this._name = Objects.requireNonNull(name);
        this._bounds = Rectangle.ZERO;
        this._padding = Padding.ZERO;
        this._visible = true;
        this._enabled = true;
        this._tooltips = ImmutableList.of();
        this._tooltipsObjects = ImmutableList.of();
    }

    /** The natural size of the control, used when no desired dimension is set; 0 if it has none. */
    protected int getDefaultDimension(final DesiredDimension dimension) {
        return 0;
    }

    protected Theme getTheme() {
        return this._gui.getTheme();
    }

    protected Font getFont() {
        return this._gui.getFont();
    }

    protected int getZ() {
        return (int) this._gui.getZLevel();
    }

    protected boolean hasBackground() {
        return null != this._backgroundSprite || null != this._backgroundColour;
    }

    @Nullable
    protected ISprite getBackgroundSprite() {
        return this._backgroundSprite;
    }

    /** The bounds of this control in screen coordinates. */
    protected Rectangle getScreenBounds() {

        final Point origin = this.controlToScreen(0, 0);

        return new Rectangle(origin.X, origin.Y, this._bounds.Width, this._bounds.Height);
    }

    /** The area inside the padding, in screen coordinates. */
    protected Rectangle getScreenPaddingRect() {
        return this.getScreenBounds().inset(this._padding.left(), this._padding.right(), this._padding.top(),
                this._padding.bottom());
    }

    protected boolean isMouseOver(final int mouseX, final int mouseY) {
        return this.isVisible() && this.getScreenBounds().contains(mouseX, mouseY);
    }

    protected void paintSprite(final PoseStack matrix, final ISprite sprite, final Rectangle area) {
        ModRenderHelper.paintSprite(matrix, sprite, area.getX1(), area.getY1(), this.getZ(), area.Width, area.Height);
    }

    protected void paintSolidRect(final PoseStack matrix, final Rectangle area, final Colour colour) {
        ModRenderHelper.paintSolidRect(matrix, area.getX1(), area.getY1(), area.getX2(), area.getY2(), this.getZ(),
                colour);
    }

    private final ModContainerScreen<? extends ModContainer> _gui;
    private final String _name;
    @Nullable
    private IControlContainer _parent;
    private Rectangle _bounds;
    private int _desiredWidth;
    private int _desiredHeight;
    @Nullable
    private Object _layoutHint;
    private Padding _padding;
    private boolean _visible;
    private boolean _enabled;
    private boolean _focused;
    @Nullable
    private Object _tag;
    @Nullable
    private ISprite _backgroundSprite;
    @Nullable
    private Colour _backgroundColour;
    private List<Component> _tooltips;
    private List<Object> _tooltipsObjects;
    @Nullable
    private IControl _tooltipsSource;

    //endregion
}

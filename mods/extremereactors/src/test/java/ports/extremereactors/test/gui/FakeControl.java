/*
 * Extreme Reactors 1.19.2 port - tests
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.test.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.Component;
import ports.extremereactors.corelib.client.gui.DesiredDimension;
import ports.extremereactors.corelib.client.gui.IControl;
import ports.extremereactors.corelib.client.gui.IControlContainer;
import ports.extremereactors.corelib.client.gui.ModContainerScreen;
import ports.extremereactors.corelib.client.gui.Padding;
import ports.extremereactors.corelib.client.gui.layout.ILayoutEngine;
import ports.extremereactors.corelib.client.gui.sprite.ISprite;
import ports.extremereactors.corelib.data.geometry.Point;
import ports.extremereactors.corelib.data.geometry.Rectangle;
import ports.extremereactors.corelib.data.gfx.Colour;
import ports.extremereactors.corelib.item.inventory.container.ModContainer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A control without a screen, for exercising layout engines: it only keeps geometry, hints and children.
 */
final class FakeControl
        implements IControlContainer {

    static FakeControl sized(final int width, final int height) {

        final FakeControl control = new FakeControl();

        control.setDesiredDimension(width, height);
        return control;
    }

    static FakeControl container(final int width, final int height, final ILayoutEngine engine,
                                 final IControl... children) {

        final FakeControl container = new FakeControl();

        container.setBounds(new Rectangle(0, 0, width, height));
        container.setLayoutEngine(engine);
        container.addControl(children);
        return container;
    }

    FakeControl hint(final Object hint) {

        this.setLayoutEngineHint(hint);
        return this;
    }

    //region IControl

    @Override
    public String getName() {
        return "fake";
    }

    @Override
    public ModContainerScreen<? extends ModContainer> getGui() {
        throw new UnsupportedOperationException();
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
        this._bounds = bounds;
    }

    @Override
    public Point controlToScreen(final int x, final int y) {
        return new Point(x + this._bounds.getX1(), y + this._bounds.getY1());
    }

    @Override
    public Point screenToControl(final int x, final int y) {
        return new Point(x - this._bounds.getX1(), y - this._bounds.getY1());
    }

    @Override
    public int getDesiredDimension(final DesiredDimension dimension) {
        return this._desired.getOrDefault(dimension, 0);
    }

    @Override
    public void setDesiredDimension(final DesiredDimension dimension, final int value) {
        this._desired.put(dimension, Math.max(0, value));
    }

    @Nullable
    @Override
    public Object getLayoutEngineHint() {
        return this._hint;
    }

    @Override
    public void setLayoutEngineHint(@Nullable final Object hint) {
        this._hint = hint;
    }

    @Override
    public Padding getPadding() {
        return this._padding;
    }

    @Override
    public void setPadding(final Padding padding) {
        this._padding = padding;
    }

    @Override
    public boolean isVisible() {
        return true;
    }

    @Override
    public void setVisible(final boolean visible) {
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void setEnabled(final boolean enabled) {
    }

    @Override
    public void setTag(@Nullable final Object tag) {
    }

    @Override
    public <T> Optional<T> getTag() {
        return Optional.empty();
    }

    @Override
    public void setBackground(final ISprite sprite) {
    }

    @Override
    public void setBackground(final Colour colour) {
    }

    @Override
    public List<Component> getTooltips() {
        return List.of();
    }

    @Override
    public List<Object> getTooltipsObjects() {
        return List.of();
    }

    @Override
    public void setTooltips(final Component... lines) {
    }

    @Override
    public void setTooltips(final List<Component> lines) {
    }

    @Override
    public void setTooltips(final List<Component> lines, final List<Object> objects) {
    }

    @Override
    public void useTooltipsFrom(@Nullable final IControl other) {
    }

    @Override
    public void onPaintBackground(final PoseStack matrix, final float partialTicks, final int mouseX,
                                  final int mouseY) {
    }

    @Override
    public void onPaint(final PoseStack matrix, final float partialTicks, final int mouseX, final int mouseY) {
    }

    @Override
    public Optional<IControl> findControl(final int screenX, final int screenY) {
        return Optional.empty();
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
    }

    @Override
    public void onKillFocus() {
    }

    @Override
    public boolean hasFocus() {
        return false;
    }

    //endregion
    //region IControlContainer

    @Override
    public void addControl(final IControl... controls) {

        for (final IControl control : controls) {

            this._children.add(control);
            control.setParent(this);
        }
    }

    @Override
    public void removeControl(final IControl control) {
        this._children.remove(control);
    }

    @Override
    public void removeControls() {
        this._children.clear();
    }

    @Override
    public List<IControl> getControls() {
        return this._children;
    }

    @Override
    public ILayoutEngine getLayoutEngine() {
        return this._engine;
    }

    @Override
    public void setLayoutEngine(final ILayoutEngine engine) {
        this._engine = engine;
    }

    @Override
    public void layout() {
        this._engine.layout(this);
    }

    @Override
    public void validate(final Consumer<Component> errorReport) {
    }

    //endregion

    private final Map<DesiredDimension, Integer> _desired = new EnumMap<>(DesiredDimension.class);
    private final List<IControl> _children = new ArrayList<>();
    private Rectangle _bounds = Rectangle.ZERO;
    private Padding _padding = Padding.ZERO;
    private ILayoutEngine _engine;
    private IControlContainer _parent;
    private Object _hint;
}

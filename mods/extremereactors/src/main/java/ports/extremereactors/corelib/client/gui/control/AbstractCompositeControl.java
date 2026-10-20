/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.gui.control;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.Component;
import ports.extremereactors.corelib.client.gui.IControl;
import ports.extremereactors.corelib.client.gui.IControlContainer;
import ports.extremereactors.corelib.client.gui.ModContainerScreen;
import ports.extremereactors.corelib.client.gui.layout.FixedLayoutEngine;
import ports.extremereactors.corelib.client.gui.layout.ILayoutEngine;
import ports.extremereactors.corelib.item.inventory.container.ModContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A control made of child controls.
 * <p>
 * The same control may be added to several containers when only one of them is shown at a time, as the panels of a
 * {@link PanelGroup}: laying a container out makes it the parent of its active children.
 */
public abstract class AbstractCompositeControl
        extends AbstractControl
        implements IControlContainer {

    //region IControlContainer

    @Override
    public void addControl(final IControl... controls) {

        for (final IControl control : controls) {

            this._children.add(Objects.requireNonNull(control));
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
        return ImmutableList.copyOf(this._children);
    }

    @Override
    public ILayoutEngine getLayoutEngine() {
        return this._layoutEngine;
    }

    @Override
    public void setLayoutEngine(final ILayoutEngine engine) {
        this._layoutEngine = Objects.requireNonNull(engine);
    }

    @Override
    public void layout() {

        final List<IControl> active = this.getActiveControls();

        for (final IControl control : active) {
            control.setParent(this);
        }

        this._layoutEngine.layout(this);

        for (final IControl control : active) {

            if (control instanceof IControlContainer container) {
                container.layout();
            }
        }
    }

    @Override
    public void validate(final Consumer<Component> errorReport) {

        for (final IControl control : this.getActiveControls()) {

            if (control.isVisible() && control instanceof IControlContainer container) {
                container.validate(errorReport);
            }
        }
    }

    //endregion
    //region IControl

    @Override
    public void onPaintBackground(final PoseStack matrix, final float partialTicks, final int mouseX,
                                  final int mouseY) {

        this.paintOwnBackground(matrix, partialTicks, mouseX, mouseY);
        this.paintChildrenBackground(matrix, partialTicks, mouseX, mouseY);
    }

    @Override
    public void onPaint(final PoseStack matrix, final float partialTicks, final int mouseX, final int mouseY) {

        for (final IControl control : this.getActiveControls()) {

            if (control.isVisible()) {
                control.onPaint(matrix, partialTicks, mouseX, mouseY);
            }
        }
    }

    @Override
    public Optional<IControl> findControl(final int screenX, final int screenY) {

        if (!this.isVisible() || !this.hitTest(screenX, screenY)) {
            return Optional.empty();
        }

        final List<IControl> active = this.getActiveControls();

        // later children are painted over earlier ones
        for (int i = active.size() - 1; i >= 0; --i) {

            final Optional<IControl> found = active.get(i).findControl(screenX, screenY);

            if (found.isPresent()) {
                return found;
            }
        }

        return Optional.of(this);
    }

    //endregion
    //region internals

    protected AbstractCompositeControl(final ModContainerScreen<? extends ModContainer> gui, final String name) {

        super(gui, name);
        this._children = new ArrayList<>();
        this._layoutEngine = new FixedLayoutEngine();
    }

    protected void paintOwnBackground(final PoseStack matrix, final float partialTicks, final int mouseX,
                                      final int mouseY) {
        super.onPaintBackground(matrix, partialTicks, mouseX, mouseY);
    }

    protected void paintChildrenBackground(final PoseStack matrix, final float partialTicks, final int mouseX,
                                           final int mouseY) {

        for (final IControl control : this.getActiveControls()) {

            if (control.isVisible()) {
                control.onPaintBackground(matrix, partialTicks, mouseX, mouseY);
            }
        }
    }

    private final List<IControl> _children;
    private ILayoutEngine _layoutEngine;

    //endregion
}

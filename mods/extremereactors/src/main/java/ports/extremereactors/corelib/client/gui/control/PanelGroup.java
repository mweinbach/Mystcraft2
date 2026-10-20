/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.gui.control;

import com.google.common.collect.ImmutableList;
import ports.extremereactors.corelib.client.gui.IControl;
import ports.extremereactors.corelib.client.gui.ModContainerScreen;
import ports.extremereactors.corelib.data.geometry.Rectangle;
import ports.extremereactors.corelib.item.inventory.container.ModContainer;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A stack of panels, one for each index, of which at most one is shown, filling the whole group.
 * Only the shown panel is painted, receives input and is validated.
 *
 * @param <T> the value identifying each panel
 */
public class PanelGroup<T>
        extends AbstractCompositeControl {

    public PanelGroup(final ModContainerScreen<? extends ModContainer> gui, final String name, final T[] indices) {
        this(gui, name, ImmutableList.copyOf(indices));
    }

    public PanelGroup(final ModContainerScreen<? extends ModContainer> gui, final String name,
                      final List<T> indices) {

        super(gui, name);
        this._indices = ImmutableList.copyOf(indices);
        this._panels = new HashMap<>();
    }

    public void setPanel(final T index, final Panel panel) {

        if (!this._indices.contains(index)) {
            throw new IllegalArgumentException("Invalid panel index: " + index);
        }

        final Panel previous = this._panels.put(index, Objects.requireNonNull(panel));

        if (null != previous) {
            this.removeControl(previous);
        }

        this.addControl(panel);

        if (index.equals(this._active)) {
            this.layoutActivePanel();
        }
    }

    public void setActivePanel(final T index) {

        if (!this._indices.contains(index)) {
            return;
        }

        this._active = index;
        this.layoutActivePanel();
    }

    public void clearActivePanel() {
        this._active = null;
    }

    public Optional<T> getActivePanelIndex() {
        return Optional.ofNullable(this._active);
    }

    //region IControlContainer

    @Override
    public List<IControl> getActiveControls() {

        final Panel panel = null != this._active ? this._panels.get(this._active) : null;

        return null != panel ? ImmutableList.of(panel) : ImmutableList.of();
    }

    @Override
    public void layout() {
        this.layoutActivePanel();
    }

    //endregion
    //region internals

    private void layoutActivePanel() {

        final Panel panel = null != this._active ? this._panels.get(this._active) : null;

        if (null == panel) {
            return;
        }

        final Rectangle area = this.getPaddingRect();

        panel.setParent(this);
        panel.setBounds(new Rectangle(area.getX1(), area.getY1(), area.Width, area.Height));
        panel.layout();
    }

    private final List<T> _indices;
    private final Map<T, Panel> _panels;
    @Nullable
    private T _active;

    //endregion
}

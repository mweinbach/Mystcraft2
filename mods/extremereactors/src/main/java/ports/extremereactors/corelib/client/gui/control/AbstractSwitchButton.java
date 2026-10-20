/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.gui.control;

import net.minecraft.network.chat.Component;
import ports.extremereactors.corelib.client.gui.ModContainerScreen;
import ports.extremereactors.corelib.event.Event;
import ports.extremereactors.corelib.event.IEvent;
import ports.extremereactors.corelib.item.inventory.container.ModContainer;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * A button that stays pressed in (active) until it is clicked again.
 * <p>
 * Switches that share a group name in the same screen behave as radio buttons: activating one deactivates the
 * others, and clicking the active one does nothing. {@link #Activated} and {@link #Deactivated} are raised whenever
 * the state changes, whether by a click or by {@link #setActive}.
 *
 * @param <B> the concrete switch, as passed to the event handlers
 */
public abstract class AbstractSwitchButton<B extends AbstractSwitchButton<B>>
        extends AbstractButtonControl {

    public final IEvent<Consumer<B>> Activated;
    public final IEvent<Consumer<B>> Deactivated;

    public boolean getActive() {
        return this._active;
    }

    public void setActive(final boolean active) {

        if (active == this._active) {
            return;
        }

        this._active = active;

        if (active && null != this._group) {
            for (final AbstractSwitchButton<?> other : this.getGui().getSwitchGroup(this._group)) {

                if (other != this) {
                    other.setActive(false);
                }
            }
        }

        (active ? this.Activated : this.Deactivated).raise(handler -> handler.accept(this.self()));
    }

    //region AbstractButtonControl

    @Override
    protected boolean isActiveState() {
        return this._active;
    }

    @Override
    protected void onClick(final int mouseButton) {

        if (null == this._group || !this._active) {
            this.setActive(!this._active);
        }
    }

    //endregion
    //region internals

    protected AbstractSwitchButton(final ModContainerScreen<? extends ModContainer> gui, final String name,
                                   final Component text, final boolean active, @Nullable final String group) {

        super(gui, name, text);
        this.Activated = new Event<>();
        this.Deactivated = new Event<>();
        this._active = active;
        this._group = group;

        if (null != group) {
            gui.addToSwitchGroup(group, this);
        }
    }

    @SuppressWarnings("unchecked")
    private B self() {
        return (B) this;
    }

    private boolean _active;
    @Nullable
    private final String _group;

    //endregion
}

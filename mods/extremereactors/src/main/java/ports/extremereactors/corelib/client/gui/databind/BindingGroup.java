/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.gui.databind;

import java.util.ArrayList;
import java.util.List;

/**
 * Bindings updated together, in the order they were added.
 */
public class BindingGroup
        implements IBinding {

    public BindingGroup() {
        this._bindings = new ArrayList<>();
    }

    public void addBinding(final IBinding binding) {
        this._bindings.add(binding);
    }

    public void clear() {
        this._bindings.clear();
    }

    //region IBinding

    @Override
    public void update() {

        for (final IBinding binding : this._bindings) {
            binding.update();
        }
    }

    //endregion
    //region internals

    private final List<IBinding> _bindings;

    //endregion
}

/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.model.data;

import java.util.function.Predicate;

public class NamedModelProperty<T> extends ModelProperty<T> {

    public NamedModelProperty(final String name) {
        this._name = name;
    }

    public NamedModelProperty(final String name, final Predicate<T> validator) {

        super(validator);
        this._name = name;
    }

    public String getName() {
        return this._name;
    }

    @Override
    public String toString() {
        return this._name;
    }

    private final String _name;
}

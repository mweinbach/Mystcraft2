/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.model.data;

import java.util.function.Predicate;

/**
 * A typed key into {@link ModelData}. Keys compare by identity.
 */
public class ModelProperty<T> implements Predicate<T> {

    public ModelProperty() {
        this(value -> true);
    }

    public ModelProperty(final Predicate<T> validator) {
        this._validator = validator;
    }

    @Override
    public boolean test(final T value) {
        return this._validator.test(value);
    }

    private final Predicate<T> _validator;
}

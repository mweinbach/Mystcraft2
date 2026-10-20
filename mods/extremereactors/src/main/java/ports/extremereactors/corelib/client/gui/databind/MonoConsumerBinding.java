/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.gui.databind;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Passes a changed value to one consumer.
 */
public class MonoConsumerBinding<Source, Value>
        extends AbstractBinding<Source, Value> {

    public MonoConsumerBinding(final Source source, final Function<Source, Value> getter,
                               final Consumer<Value> consumer) {

        super(source, getter);
        this._consumer = Objects.requireNonNull(consumer);
    }

    //region internals

    @Override
    protected void dispatch(final Value value) {
        this._consumer.accept(value);
    }

    private final Consumer<Value> _consumer;

    //endregion
}

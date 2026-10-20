/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.gui.databind;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Passes a changed value to several consumers, in order.
 */
public class MultiConsumerBinding<Source, Value>
        extends AbstractBinding<Source, Value> {

    @SafeVarargs
    public MultiConsumerBinding(final Source source, final Function<Source, Value> getter,
                                final Consumer<Value>... consumers) {

        super(source, getter);
        this._consumers = List.of(consumers);
    }

    //region internals

    @Override
    protected void dispatch(final Value value) {

        for (final Consumer<Value> consumer : this._consumers) {
            consumer.accept(value);
        }
    }

    private final List<Consumer<Value>> _consumers;

    //endregion
}

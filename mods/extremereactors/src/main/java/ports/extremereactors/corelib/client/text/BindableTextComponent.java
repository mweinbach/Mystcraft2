/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.text;

import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A text that is rebuilt from the last value it was given. Use it as a data binding consumer and as a tooltip
 * object.
 */
public class BindableTextComponent<T>
        implements Consumer<T>, Supplier<Component> {

    public BindableTextComponent(final Function<T, Component> builder) {

        this._builder = Objects.requireNonNull(builder);
        this._text = Component.empty();
    }

    //region Consumer<T>

    @Override
    public void accept(final T value) {
        this._text = this._builder.apply(value);
    }

    //endregion
    //region Supplier<Component>

    @Override
    public Component get() {
        return this._text;
    }

    //endregion
    //region internals

    private final Function<T, Component> _builder;
    private Component _text;

    //endregion
}

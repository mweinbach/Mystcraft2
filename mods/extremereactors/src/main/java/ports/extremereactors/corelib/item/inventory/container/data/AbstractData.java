/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.item.inventory.container.data;

import net.minecraft.network.FriendlyByteBuf;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Base for the container data types. The consumer is also called on the server whenever the value changes, so a
 * container can keep the same fields up to date on both sides.
 */
public abstract class AbstractData<T>
        implements IContainerData {

    protected AbstractData(final Supplier<T> source, final Consumer<T> consumer) {

        this._source = Objects.requireNonNull(source);
        this._consumer = Objects.requireNonNull(consumer);
    }

    //region IContainerData

    @Override
    public boolean updateFromSource() {

        final T value = this._source.get();

        if (this._sent && this.isSame(this._last, value)) {
            return false;
        }

        this._sent = true;
        this._last = this.copy(value);
        this._consumer.accept(this.copy(value));
        return true;
    }

    @Override
    public void encode(final FriendlyByteBuf buffer) {
        this.write(buffer, this._last);
    }

    @Override
    public void decode(final FriendlyByteBuf buffer) {
        this._consumer.accept(this.read(buffer));
    }

    //endregion
    //region internals

    protected abstract void write(FriendlyByteBuf buffer, T value);

    protected abstract T read(FriendlyByteBuf buffer);

    protected boolean isSame(final T previous, final T current) {
        return Objects.equals(previous, current);
    }

    protected T copy(final T value) {
        return value;
    }

    private final Supplier<T> _source;
    private final Consumer<T> _consumer;
    private boolean _sent;
    private T _last;

    //endregion
}

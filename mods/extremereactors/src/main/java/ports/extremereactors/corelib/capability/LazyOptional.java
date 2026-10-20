/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.capability;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A lazily resolved handler that its owner can invalidate (for example when the block entity is removed).
 * Holders must not keep the resolved value past invalidation; listeners are told when that happens.
 */
public final class LazyOptional<T> {

    public static <T> LazyOptional<T> of(final Supplier<T> supplier) {
        return new LazyOptional<>(Objects.requireNonNull(supplier));
    }

    @SuppressWarnings("unchecked")
    public static <T> LazyOptional<T> empty() {
        return (LazyOptional<T>) EMPTY;
    }

    @SuppressWarnings("unchecked")
    public <X> LazyOptional<X> cast() {
        return (LazyOptional<X>) this;
    }

    public boolean isPresent() {
        return null != this.getValue();
    }

    public void ifPresent(final Consumer<? super T> consumer) {

        final T value = this.getValue();

        if (null != value) {
            consumer.accept(value);
        }
    }

    /**
     * Map the value now; the result is a resolved snapshot that is not invalidated with this instance.
     */
    public <U> LazyOptional<U> map(final Function<? super T, ? extends U> mapper) {

        final T value = this.getValue();

        if (null == value) {
            return empty();
        }

        final U mapped = mapper.apply(value);

        return null == mapped ? empty() : LazyOptional.of(() -> mapped);
    }

    /**
     * Map lazily; the result is invalidated together with this instance.
     */
    public <U> LazyOptional<U> lazyMap(final Function<? super T, ? extends U> mapper) {

        if (!this.isPresent()) {
            return empty();
        }

        final LazyOptional<U> mapped = LazyOptional.of(() -> {
            final T value = this.getValue();
            return null == value ? null : mapper.apply(value);
        });

        this.addListener(self -> mapped.invalidate());
        return mapped;
    }

    public Optional<T> filter(final Predicate<? super T> predicate) {
        return this.resolve().filter(predicate);
    }

    public Optional<T> resolve() {
        return Optional.ofNullable(this.getValue());
    }

    public T orElse(final T other) {

        final T value = this.getValue();

        return null != value ? value : other;
    }

    public T orElseGet(final Supplier<? extends T> other) {

        final T value = this.getValue();

        return null != value ? value : other.get();
    }

    public <X extends Throwable> T orElseThrow(final Supplier<? extends X> exceptionSupplier) throws X {

        final T value = this.getValue();

        if (null == value) {
            throw exceptionSupplier.get();
        }

        return value;
    }

    public void addListener(final Consumer<LazyOptional<T>> listener) {

        if (this.isPresent()) {
            this._listeners.add(listener);
        } else {
            listener.accept(this);
        }
    }

    public void invalidate() {

        if (this._valid && this != EMPTY) {

            this._valid = false;
            this._resolved = null;

            final List<Consumer<LazyOptional<T>>> listeners = new ArrayList<>(this._listeners);

            this._listeners.clear();
            listeners.forEach(listener -> listener.accept(this));
        }
    }

    private T getValue() {

        if (!this._valid || null == this._supplier) {
            return null;
        }

        if (null == this._resolved) {
            this._resolved = this._supplier.get();
        }

        return this._resolved;
    }

    private LazyOptional(final Supplier<T> supplier) {

        this._supplier = supplier;
        this._valid = true;
        this._listeners = new ArrayList<>(1);
    }

    private static final LazyOptional<Object> EMPTY = new LazyOptional<>(null);

    private final Supplier<T> _supplier;
    private final List<Consumer<LazyOptional<T>>> _listeners;
    private T _resolved;
    private boolean _valid;
}

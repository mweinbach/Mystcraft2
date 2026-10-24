/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.common.util;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A capability instance that is resolved on first use and can be invalidated by its owner, which tells every
 * listener that the instance must no longer be used.
 */
public final class LazyOptional<T> {

    private static final LazyOptional<Void> EMPTY = new LazyOptional<>(null);

    @Nullable
    private final NonNullSupplier<T> supplier;
    private final Object lock = new Object();
    @Nullable
    private volatile T resolved;
    private volatile boolean isValid = true;
    @Nullable
    private Set<NonNullConsumer<LazyOptional<T>>> listeners;

    private LazyOptional(@Nullable NonNullSupplier<T> supplier) {
        this.supplier = supplier;
    }

    public static <T> LazyOptional<T> of(@Nullable NonNullSupplier<T> instanceSupplier) {
        return instanceSupplier == null ? empty() : new LazyOptional<>(instanceSupplier);
    }

    @SuppressWarnings("unchecked")
    public static <T> LazyOptional<T> empty() {
        return (LazyOptional<T>) EMPTY;
    }

    @SuppressWarnings("unchecked")
    public <X> LazyOptional<X> cast() {
        return (LazyOptional<X>) this;
    }

    @Nullable
    private T getValue() {
        if (!isValid || supplier == null) {
            return null;
        }
        T value = resolved;
        if (value == null) {
            synchronized (lock) {
                value = resolved;
                if (value == null) {
                    value = supplier.get();
                    if (value == null) {
                        throw new IllegalStateException("LazyOptional supplier returned null");
                    }
                    resolved = value;
                }
            }
        }
        return value;
    }

    private T getValueUnsafe() {
        T value = getValue();
        if (value == null) {
            throw new IllegalStateException("LazyOptional is empty or was invalidated");
        }
        return value;
    }

    public boolean isPresent() {
        return supplier != null && isValid;
    }

    public void ifPresent(NonNullConsumer<? super T> consumer) {
        T value = getValue();
        if (value != null) {
            consumer.accept(value);
        }
    }

    public <U> LazyOptional<U> lazyMap(NonNullFunction<? super T, ? extends U> mapper) {
        return isPresent() ? of(() -> mapper.apply(getValueUnsafe())) : empty();
    }

    public <U> Optional<U> map(NonNullFunction<? super T, ? extends U> mapper) {
        return isPresent() ? Optional.of(mapper.apply(getValueUnsafe())) : Optional.empty();
    }

    public Optional<T> filter(NonNullPredicate<? super T> predicate) {
        T value = getValue();
        return value != null && predicate.test(value) ? Optional.of(value) : Optional.empty();
    }

    public Optional<T> resolve() {
        return isPresent() ? Optional.of(getValueUnsafe()) : Optional.empty();
    }

    public T orElse(T other) {
        T value = getValue();
        return value != null ? value : other;
    }

    public T orElseGet(NonNullSupplier<? extends T> other) {
        T value = getValue();
        return value != null ? value : other.get();
    }

    public T orElseThrow() {
        T value = getValue();
        if (value == null) {
            throw new NoSuchElementException("No value present");
        }
        return value;
    }

    public <X extends Throwable> T orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
        T value = getValue();
        if (value == null) {
            throw exceptionSupplier.get();
        }
        return value;
    }

    public void addListener(@NotNull NonNullConsumer<LazyOptional<T>> listener) {
        if (isPresent()) {
            synchronized (lock) {
                if (listeners == null) {
                    listeners = new java.util.LinkedHashSet<>();
                }
                listeners.add(listener);
            }
        } else {
            listener.accept(this);
        }
    }

    public void removeListener(@NotNull NonNullConsumer<LazyOptional<T>> listener) {
        synchronized (lock) {
            if (listeners != null) {
                listeners.remove(listener);
            }
        }
    }

    public void invalidate() {
        if (isValid) {
            Set<NonNullConsumer<LazyOptional<T>>> toNotify;
            synchronized (lock) {
                if (!isValid) {
                    return;
                }
                isValid = false;
                toNotify = listeners;
                listeners = null;
            }
            if (toNotify != null) {
                for (NonNullConsumer<LazyOptional<T>> listener : toNotify) {
                    listener.accept(this);
                }
            }
        }
    }
}

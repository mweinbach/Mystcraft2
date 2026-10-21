/*
 * Mekanism 1.19.2 port - loader-neutral mirror of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.common.util;

import java.util.function.Supplier;

/**
 * A supplier that computes its value once, on first use.
 */
public interface Lazy<T> extends Supplier<T> {

    static <T> Lazy<T> of(Supplier<T> supplier) {
        return new Fast<>(supplier);
    }

    static <T> Lazy<T> concurrentOf(Supplier<T> supplier) {
        return new Concurrent<>(supplier);
    }

    final class Fast<T> implements Lazy<T> {

        private Supplier<T> supplier;
        private T instance;

        private Fast(Supplier<T> supplier) {
            this.supplier = supplier;
        }

        @Override
        public T get() {
            if (supplier != null) {
                instance = supplier.get();
                supplier = null;
            }
            return instance;
        }
    }

    final class Concurrent<T> implements Lazy<T> {

        private volatile Supplier<T> supplier;
        private volatile T instance;

        private Concurrent(Supplier<T> supplier) {
            this.supplier = supplier;
        }

        @Override
        public T get() {
            if (supplier != null) {
                synchronized (this) {
                    if (supplier != null) {
                        instance = supplier.get();
                        supplier = null;
                    }
                }
            }
            return instance;
        }
    }
}

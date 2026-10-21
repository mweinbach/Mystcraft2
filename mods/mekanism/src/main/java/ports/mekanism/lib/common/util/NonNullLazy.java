/*
 * Mekanism 1.19.2 port - loader-neutral mirror of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.common.util;

import org.jetbrains.annotations.NotNull;

public interface NonNullLazy<T> extends NonNullSupplier<T> {

    static <T> NonNullLazy<T> of(@NotNull NonNullSupplier<T> supplier) {
        Lazy<T> lazy = Lazy.of(supplier::get);
        return lazy::get;
    }
}

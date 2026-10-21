/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.common.extensions;

import java.util.function.Supplier;
import net.minecraft.core.Holder;

/**
 * Forge makes every {@link Holder} a {@link Supplier} of its value; on Fabric this is injected into the holder
 * implementations.
 */
public interface IForgeHolder<T> extends Supplier<T> {

    @Override
    @SuppressWarnings("unchecked")
    default T get() {
        return ((Holder<T>) this).value();
    }
}

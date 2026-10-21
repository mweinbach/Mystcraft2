/*
 * Mekanism 1.19.2 port - loader-neutral mirror of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.common.capabilities;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ports.mekanism.lib.common.util.LazyOptional;

/**
 * A capability key, identified by the name of its interface. Obtain one with {@link CapabilityManager#get}.
 */
public final class Capability<T> {

    private final String name;

    Capability(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public boolean isRegistered() {
        return true;
    }

    public <R> LazyOptional<R> orEmpty(Capability<R> toCheck, LazyOptional<T> instance) {
        return this == toCheck ? instance.cast() : LazyOptional.empty();
    }

    @NotNull
    public <R> LazyOptional<R> orEmpty(Capability<R> toCheck, @Nullable T instance) {
        return this == toCheck && instance != null ? LazyOptional.of(() -> instance).cast() : LazyOptional.empty();
    }

    @Override
    public String toString() {
        return "Capability[" + name + "]";
    }
}

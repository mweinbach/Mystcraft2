/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.capability;

/**
 * Identifies a kind of handler a block entity can expose. There is exactly one instance per handler type, so
 * capabilities can be compared by identity.
 */
public final class Capability<T> {

    Capability(final String name) {
        this._name = name;
    }

    public String getName() {
        return this._name;
    }

    /**
     * Adapt a handler of this capability's type to the requested (equal) type parameter.
     */
    public <R> LazyOptional<R> orEmpty(final Capability<R> requested, final LazyOptional<T> instance) {
        return this == requested ? instance.cast() : LazyOptional.empty();
    }

    @Override
    public String toString() {
        return "Capability[" + this._name + "]";
    }

    private final String _name;
}

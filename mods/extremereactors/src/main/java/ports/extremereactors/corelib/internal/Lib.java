/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.internal;

import ports.extremereactors.multiblock.IMultiblockRegistry;
import ports.extremereactors.multiblock.registry.MultiblockRegistry;

public final class Lib {

    /**
     * The single registry serves server and client levels alike: it keeps one world registry per level and is
     * driven by the platform's per-level tick and unload hooks on both sides.
     */
    @SuppressWarnings("rawtypes")
    public static IMultiblockRegistry createMultiblockRegistry() {
        return new MultiblockRegistry();
    }

    private Lib() {
    }
}

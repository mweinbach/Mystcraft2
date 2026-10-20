/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.capability;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class CapabilityManager {

    @SuppressWarnings("unchecked")
    public static <T> Capability<T> get(final CapabilityToken<T> token) {
        return (Capability<T>) CAPABILITIES.computeIfAbsent(token.getTypeName(), Capability::new);
    }

    @SuppressWarnings("unchecked")
    public static <T> Capability<T> get(final Class<T> type) {
        return (Capability<T>) CAPABILITIES.computeIfAbsent(type.getName(), Capability::new);
    }

    private static final ConcurrentMap<String, Capability<?>> CAPABILITIES = new ConcurrentHashMap<>();

    private CapabilityManager() {
    }
}

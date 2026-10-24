/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.common.capabilities;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One {@link Capability} per interface name, created on first request.
 */
public final class CapabilityManager {

    private static final Map<String, Capability<?>> CAPABILITIES = new ConcurrentHashMap<>();

    private CapabilityManager() {
    }

    public static <T> Capability<T> get(CapabilityToken<T> type) {
        return get(type.getType());
    }

    @SuppressWarnings("unchecked")
    public static <T> Capability<T> get(String typeName) {
        return (Capability<T>) CAPABILITIES.computeIfAbsent(typeName, Capability::new);
    }

    public static <T> Capability<T> get(Class<T> type) {
        return get(type.getName());
    }
}

/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.server.permission.nodes;

import java.util.function.Function;

public record PermissionDynamicContextKey<T>(Class<T> typeToken, String name, Function<T, String> serializer) {

    public PermissionDynamicContext<T> createContext(T value) {
        return new PermissionDynamicContext<>(this, value);
    }
}

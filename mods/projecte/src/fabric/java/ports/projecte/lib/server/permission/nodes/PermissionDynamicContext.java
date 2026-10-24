/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.server.permission.nodes;

public class PermissionDynamicContext<T> {

    private final PermissionDynamicContextKey<T> dynamic;
    private final T value;

    public PermissionDynamicContext(PermissionDynamicContextKey<T> dynamic, T value) {
        this.dynamic = dynamic;
        this.value = value;
    }

    public PermissionDynamicContextKey<T> getDynamic() {
        return dynamic;
    }

    public T getValue() {
        return value;
    }

    public String getSerializedValue() {
        return dynamic.serializer().apply(value);
    }
}

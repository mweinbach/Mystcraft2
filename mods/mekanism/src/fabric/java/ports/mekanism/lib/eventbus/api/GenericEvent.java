/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.eventbus.api;

import java.lang.reflect.Type;

/**
 * An event about objects of one type. Listeners registered for the event receive it for every type; they check
 * the object themselves.
 */
public class GenericEvent<T> extends Event {

    private final Class<T> type;

    public GenericEvent(Class<T> type) {
        this.type = type;
    }

    public Type getGenericType() {
        return type;
    }
}

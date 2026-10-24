/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.eventbus.api;

import java.util.function.Consumer;

public interface IEventBus {

    void register(Object target);

    <T extends Event> void addListener(EventListener<T> consumer);

    <T extends Event> void addListener(EventPriority priority, EventListener<T> consumer);

    <T extends Event> void addListener(EventPriority priority, boolean receiveCancelled, EventListener<T> consumer);

    <T extends Event> void addListener(EventPriority priority, boolean receiveCancelled, Class<T> eventType, Consumer<T> consumer);

    /**
     * Fires the event; returns whether it was canceled.
     */
    boolean post(Event event);
}

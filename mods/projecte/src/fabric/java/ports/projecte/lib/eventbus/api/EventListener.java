/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.eventbus.api;

import java.io.Serializable;
import java.util.function.Consumer;

/**
 * An event listener. It is serializable only so {@link IEventBus#addListener(EventListener)} can read the event
 * type of a lambda or method reference; listeners are never serialized.
 */
@FunctionalInterface
public interface EventListener<T> extends Consumer<T>, Serializable {
}

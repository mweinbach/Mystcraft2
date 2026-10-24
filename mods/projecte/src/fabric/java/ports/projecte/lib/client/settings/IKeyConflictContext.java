/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.client.settings;

/**
 * Where a key mapping is active. Mappings bound to the same key only conflict when their contexts conflict.
 */
public interface IKeyConflictContext {

    boolean isActive();

    boolean conflicts(IKeyConflictContext other);
}

/*
 * ProjectE 1.19.2 port - Fabric implementation of the Forge API shapes ProjectE uses.
 * Copyright (c) 2026 ProjectE 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.common.capabilities;

import ports.projecte.lib.eventbus.api.Event;

/**
 * Capabilities in the port are keyed by interface name and exist as soon as they are requested, so registering one
 * only records nothing.
 */
public class RegisterCapabilitiesEvent extends Event {

    public <T> void register(Class<T> type) {
        CapabilityManager.get(type);
    }
}

/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import ports.projecte.lib.common.capabilities.ICapabilityProvider;
import ports.projecte.lib.eventbus.api.GenericEvent;

/**
 * Lets listeners attach capability providers to a newly created object. The port fires it for entities.
 */
public class AttachCapabilitiesEvent<T> extends GenericEvent<T> {

    private final T obj;
    private final Map<ResourceLocation, ICapabilityProvider> caps = new LinkedHashMap<>();
    private final List<Runnable> listeners = new ArrayList<>();

    public AttachCapabilitiesEvent(Class<T> type, T obj) {
        super(type);
        this.obj = obj;
    }

    public T getObject() {
        return obj;
    }

    public void addCapability(ResourceLocation key, ICapabilityProvider cap) {
        if (caps.putIfAbsent(key, cap) != null) {
            throw new IllegalStateException("Duplicate Capability Key: " + key + " " + cap);
        }
    }

    public Map<ResourceLocation, ICapabilityProvider> getCapabilities() {
        return Collections.unmodifiableMap(caps);
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public List<Runnable> getListeners() {
        return Collections.unmodifiableList(listeners);
    }
}

/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.registries;

import java.util.function.Function;
import java.util.function.Supplier;
import ports.projecte.lib.eventbus.api.Event;

/**
 * Fired on the mod bus before any {@link RegisterEvent}, to create custom registries.
 */
public class NewRegistryEvent extends Event {

    private final Function<RegistryBuilder<?>, IForgeRegistry<?>> factory;

    public NewRegistryEvent(Function<RegistryBuilder<?>, IForgeRegistry<?>> factory) {
        this.factory = factory;
    }

    @SuppressWarnings("unchecked")
    public <V> Supplier<IForgeRegistry<V>> create(RegistryBuilder<V> builder) {
        IForgeRegistry<V> registry = (IForgeRegistry<V>) factory.apply(builder);
        RegistryManager.ACTIVE.add(registry);
        return () -> registry;
    }
}

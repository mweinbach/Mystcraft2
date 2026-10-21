/*
 * Mekanism 1.19.2 port - loader-neutral mirror of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.registries;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Every registry by name: custom registries as their backend created them, and vanilla registries as views over
 * {@link Registry#REGISTRY}.
 */
public final class RegistryManager {

    public static final RegistryManager ACTIVE = new RegistryManager();

    private final Map<ResourceLocation, IForgeRegistry<?>> registries = new ConcurrentHashMap<>();

    private RegistryManager() {
    }

    public void add(IForgeRegistry<?> registry) {
        registries.put(registry.getRegistryName(), registry);
    }

    @Nullable
    public <V> IForgeRegistry<V> getRegistry(ResourceKey<? extends Registry<V>> key) {
        return getRegistry(key.location());
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public <V> IForgeRegistry<V> getRegistry(ResourceLocation key) {
        IForgeRegistry<?> registry = registries.get(key);
        if (registry == null) {
            Registry<?> vanilla = Registry.REGISTRY.get(key);
            if (vanilla != null) {
                registry = registries.computeIfAbsent(key, k -> new VanillaForgeRegistry<>(vanilla));
            }
        }
        return (IForgeRegistry<V>) registry;
    }
}

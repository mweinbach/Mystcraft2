/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.registries;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ports.projecte.lib.registries.tags.ITagManager;

/**
 * An in-memory registry without holders or tags, for registries that exist only on one loader (such as Forge's
 * fluid types on Fabric) and for registries the port keeps to itself.
 */
public class SimpleForgeRegistry<V> implements IForgeRegistry<V> {

    private final ResourceKey<Registry<V>> key;
    private final BiMap<ResourceLocation, V> entries = HashBiMap.create();
    @Nullable
    private final ResourceLocation defaultKey;
    @Nullable
    private final BiConsumer<ResourceLocation, V> onRegister;

    public SimpleForgeRegistry(ResourceKey<Registry<V>> key, @Nullable ResourceLocation defaultKey, @Nullable BiConsumer<ResourceLocation, V> onRegister) {
        this.key = key;
        this.defaultKey = defaultKey;
        this.onRegister = onRegister;
    }

    public synchronized void register(ResourceLocation name, V value) {
        if (entries.containsKey(name)) {
            throw new IllegalStateException("Duplicate registration " + name + " in " + key.location());
        }
        entries.put(name, value);
        if (onRegister != null) {
            onRegister.accept(name, value);
        }
    }

    @Override
    public ResourceKey<Registry<V>> getRegistryKey() {
        return key;
    }

    @Override
    public boolean containsKey(ResourceLocation name) {
        return entries.containsKey(name);
    }

    @Override
    public boolean containsValue(V value) {
        return entries.containsValue(value);
    }

    @Override
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    @Nullable
    @Override
    public V getValue(ResourceLocation name) {
        V value = entries.get(name);
        return value == null && defaultKey != null ? entries.get(defaultKey) : value;
    }

    @Nullable
    @Override
    public ResourceLocation getKey(V value) {
        ResourceLocation name = entries.inverse().get(value);
        return name == null ? defaultKey : name;
    }

    @Nullable
    @Override
    public ResourceLocation getDefaultKey() {
        return defaultKey;
    }

    @NotNull
    @Override
    public Optional<ResourceKey<V>> getResourceKey(V value) {
        return Optional.ofNullable(entries.inverse().get(value)).map(name -> ResourceKey.create(key, name));
    }

    @NotNull
    @Override
    public Set<ResourceLocation> getKeys() {
        return Set.copyOf(entries.keySet());
    }

    @NotNull
    @Override
    public Collection<V> getValues() {
        return List.copyOf(entries.values());
    }

    @NotNull
    @Override
    public Set<Map.Entry<ResourceKey<V>, V>> getEntries() {
        return entries.entrySet().stream()
              .map(entry -> new AbstractMap.SimpleImmutableEntry<>(ResourceKey.create(key, entry.getKey()), entry.getValue()))
              .collect(Collectors.toUnmodifiableSet());
    }

    @Nullable
    @Override
    public ITagManager<V> tags() {
        return null;
    }

    @NotNull
    @Override
    public Optional<Holder<V>> getHolder(ResourceKey<V> resourceKey) {
        return Optional.ofNullable(entries.get(resourceKey.location())).map(Holder::direct);
    }

    @NotNull
    @Override
    public Optional<Holder<V>> getHolder(ResourceLocation location) {
        return Optional.ofNullable(entries.get(location)).map(Holder::direct);
    }

    @NotNull
    @Override
    public Optional<Holder<V>> getHolder(V value) {
        return entries.containsValue(value) ? Optional.of(Holder.direct(value)) : Optional.empty();
    }

    @NotNull
    @Override
    public Holder.Reference<V> getDelegateOrThrow(V value) {
        throw new UnsupportedOperationException(key.location() + " has no holders");
    }

    @NotNull
    @Override
    public Iterator<V> iterator() {
        return getValues().iterator();
    }
}

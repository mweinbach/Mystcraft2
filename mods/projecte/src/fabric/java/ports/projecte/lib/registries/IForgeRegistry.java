/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.registries;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ports.projecte.lib.registries.tags.ITagManager;

/**
 * A registry as Mekanism reads it. Vanilla registries and every Fabric registry are views over the vanilla
 * {@link Registry}; Forge-only custom registries are views over Forge's registry.
 */
public interface IForgeRegistry<V> extends Iterable<V> {

    ResourceKey<Registry<V>> getRegistryKey();

    default ResourceLocation getRegistryName() {
        return getRegistryKey().location();
    }

    boolean containsKey(ResourceLocation key);

    boolean containsValue(V value);

    boolean isEmpty();

    @Nullable
    V getValue(ResourceLocation key);

    @Nullable
    ResourceLocation getKey(V value);

    @Nullable
    ResourceLocation getDefaultKey();

    @NotNull
    Optional<ResourceKey<V>> getResourceKey(V value);

    @NotNull
    Set<ResourceLocation> getKeys();

    @NotNull
    Collection<V> getValues();

    @NotNull
    Set<Map.Entry<ResourceKey<V>, V>> getEntries();

    @Nullable
    ITagManager<V> tags();

    @NotNull
    Optional<Holder<V>> getHolder(ResourceKey<V> key);

    @NotNull
    Optional<Holder<V>> getHolder(ResourceLocation location);

    @NotNull
    Optional<Holder<V>> getHolder(V value);

    @NotNull
    Holder.Reference<V> getDelegateOrThrow(V value);
}

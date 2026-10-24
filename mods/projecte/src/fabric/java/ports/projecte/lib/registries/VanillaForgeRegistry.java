/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.registries;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ports.projecte.lib.registries.tags.IReverseTag;
import ports.projecte.lib.registries.tags.ITag;
import ports.projecte.lib.registries.tags.ITagManager;

/**
 * A view of a vanilla {@link Registry}, including its tags.
 */
public class VanillaForgeRegistry<V> implements IForgeRegistry<V> {

    private final Registry<V> registry;
    private final TagManager tags = new TagManager();

    @SuppressWarnings("unchecked")
    public VanillaForgeRegistry(Registry<?> registry) {
        this.registry = (Registry<V>) registry;
    }

    public Registry<V> vanilla() {
        return registry;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ResourceKey<Registry<V>> getRegistryKey() {
        return (ResourceKey<Registry<V>>) registry.key();
    }

    @Override
    public boolean containsKey(ResourceLocation key) {
        return registry.containsKey(key);
    }

    @Override
    public boolean containsValue(V value) {
        return registry.getResourceKey(value).isPresent();
    }

    @Override
    public boolean isEmpty() {
        return registry.keySet().isEmpty();
    }

    @Nullable
    @Override
    public V getValue(ResourceLocation key) {
        return registry.get(key);
    }

    @Nullable
    @Override
    public ResourceLocation getKey(V value) {
        return registry.getKey(value);
    }

    @Nullable
    @Override
    public ResourceLocation getDefaultKey() {
        return registry instanceof DefaultedRegistry<V> defaulted ? defaulted.getDefaultKey() : null;
    }

    @NotNull
    @Override
    public Optional<ResourceKey<V>> getResourceKey(V value) {
        return registry.getResourceKey(value);
    }

    @NotNull
    @Override
    public Set<ResourceLocation> getKeys() {
        return registry.keySet();
    }

    @NotNull
    @Override
    public Collection<V> getValues() {
        return registry.stream().toList();
    }

    @NotNull
    @Override
    public Set<Map.Entry<ResourceKey<V>, V>> getEntries() {
        return registry.entrySet();
    }

    @Override
    public ITagManager<V> tags() {
        return tags;
    }

    @NotNull
    @Override
    @SuppressWarnings("unchecked")
    public Optional<Holder<V>> getHolder(ResourceKey<V> key) {
        return (Optional) registry.getHolder(key);
    }

    @NotNull
    @Override
    public Optional<Holder<V>> getHolder(ResourceLocation location) {
        return getHolder(ResourceKey.create(getRegistryKey(), location));
    }

    @NotNull
    @Override
    public Optional<Holder<V>> getHolder(V value) {
        return registry.getResourceKey(value).flatMap(this::getHolder);
    }

    @NotNull
    @Override
    @SuppressWarnings("unchecked")
    public Holder.Reference<V> getDelegateOrThrow(V value) {
        //Registered values are always held by references
        return (Holder.Reference<V>) registry.getHolderOrThrow(registry.getResourceKey(value).orElseThrow(() -> new IllegalArgumentException("Unregistered " + value)));
    }

    @NotNull
    @Override
    public Iterator<V> iterator() {
        return registry.iterator();
    }

    private final class TagManager implements ITagManager<V> {

        @NotNull
        @Override
        public ITag<V> getTag(@NotNull TagKey<V> name) {
            return new Tag(name);
        }

        @NotNull
        @Override
        public Optional<IReverseTag<V>> getReverseTag(@NotNull V value) {
            return getHolder(value).map(holder -> new IReverseTag<V>() {
                @NotNull
                @Override
                public Stream<TagKey<V>> getTagKeys() {
                    return holder.tags();
                }

                @Override
                public boolean containsTag(@NotNull TagKey<V> key) {
                    return holder.is(key);
                }
            });
        }

        @Override
        public boolean isKnownTagName(@NotNull TagKey<V> name) {
            return registry.getTag(name).isPresent();
        }

        @NotNull
        @Override
        public Stream<ITag<V>> stream() {
            return registry.getTagNames().map(this::getTag);
        }

        @NotNull
        @Override
        public Iterator<ITag<V>> iterator() {
            return stream().iterator();
        }

        @NotNull
        @Override
        public Stream<TagKey<V>> getTagNames() {
            return registry.getTagNames();
        }

        @NotNull
        @Override
        public TagKey<V> createTagKey(@NotNull ResourceLocation location) {
            return TagKey.create(getRegistryKey(), location);
        }

        @NotNull
        @Override
        public TagKey<V> createOptionalTagKey(@NotNull ResourceLocation location, @NotNull Set<? extends Supplier<V>> defaults) {
            return createTagKey(location);
        }

        @Override
        public void addOptionalTagDefaults(@NotNull TagKey<V> name, @NotNull Set<? extends Supplier<V>> defaults) {
        }
    }

    private final class Tag implements ITag<V> {

        private final TagKey<V> key;

        private Tag(TagKey<V> key) {
            this.key = key;
        }

        private Optional<HolderSet.Named<V>> holders() {
            return registry.getTag(key);
        }

        @NotNull
        @Override
        public TagKey<V> getKey() {
            return key;
        }

        @Override
        public boolean isBound() {
            return holders().isPresent();
        }

        @Override
        public boolean isEmpty() {
            return holders().map(set -> set.size() == 0).orElse(true);
        }

        @Override
        public int size() {
            return holders().map(HolderSet::size).orElse(0);
        }

        @Override
        public boolean contains(@NotNull V value) {
            return getHolder(value).map(holder -> holder.is(key)).orElse(false);
        }

        @NotNull
        @Override
        public Stream<V> stream() {
            return holders().map(set -> set.stream().map(Holder::value)).orElseGet(Stream::empty);
        }

        @NotNull
        @Override
        public Optional<V> getRandomElement(@NotNull RandomSource random) {
            return holders().flatMap(set -> set.getRandomElement(random)).map(Holder::value);
        }

        @NotNull
        @Override
        public Iterator<V> iterator() {
            return stream().iterator();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof VanillaForgeRegistry<?>.Tag other && key.equals(other.key);
        }

        @Override
        public int hashCode() {
            return key.hashCode();
        }

        @Override
        public String toString() {
            return "Tag[" + key.location() + "]";
        }
    }
}

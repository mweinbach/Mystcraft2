/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.tag;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Tag lookups for one registry. The lookups reflect the tags loaded by the current server, or received from it
 * on the client.
 */
public final class TagsHelper<T> {

    public static final TagsHelper<Block> BLOCKS = new TagsHelper<>(Registry.BLOCK);
    public static final TagsHelper<Item> ITEMS = new TagsHelper<>(Registry.ITEM);
    public static final TagsHelper<Fluid> FLUIDS = new TagsHelper<>(Registry.FLUID);

    public static final TagKey<Item> TAG_WRENCH = ITEMS.createKey("forge:tools/wrench");

    public TagKey<T> createKey(final String id) {
        return TagKey.create(this._registry.key(), new ResourceLocation(id));
    }

    public TagKey<T> createKey(final ResourceLocation id) {
        return TagKey.create(this._registry.key(), id);
    }

    public TagKey<T> createKey(final String name, final Function<String, ResourceLocation> idFactory) {
        return TagKey.create(this._registry.key(), idFactory.apply(name));
    }

    /**
     * @return the tags the given object belongs to
     */
    public List<TagKey<T>> getTags(final T object) {
        return this._registry.getResourceKey(object)
                .flatMap(this._registry::getHolder)
                .map(holder -> holder.tags().toList())
                .orElseGet(List::of);
    }

    /**
     * @return the objects in the given tag, in the tag order
     */
    public List<T> getObjects(final TagKey<T> tag) {
        return this._registry.getTag(tag)
                .map(set -> set.stream().map(Holder::value).toList())
                .orElseGet(List::of);
    }

    public Optional<T> getFirstObject(final TagKey<T> tag) {
        return this._registry.getTag(tag)
                .flatMap(set -> set.stream().findFirst())
                .map(Holder::value);
    }

    public boolean isIn(final TagKey<T> tag, final T object) {
        return this._registry.getResourceKey(object)
                .flatMap(this._registry::getHolder)
                .filter(holder -> holder.is(tag))
                .isPresent();
    }

    //region internals

    private TagsHelper(final Registry<T> registry) {
        this._registry = registry;
    }

    private final Registry<T> _registry;

    //endregion
}

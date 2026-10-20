/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.tag;

import com.google.common.collect.Lists;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * An ordered set of tags of one registry, kept in the order they were added.
 */
public final class TagList<T> {

    public static TagList<Block> blocks() {
        return new TagList<>(TagsHelper.BLOCKS);
    }

    public static TagList<Item> items() {
        return new TagList<>(TagsHelper.ITEMS);
    }

    public static TagList<Fluid> fluids() {
        return new TagList<>(TagsHelper.FLUIDS);
    }

    public void addTag(final TagKey<T> tag) {

        synchronized (this._tags) {

            if (!this._tags.contains(tag)) {
                this._tags.add(tag);
            }
        }
    }

    public void removeTag(final TagKey<T> tag) {

        synchronized (this._tags) {
            this._tags.remove(tag);
        }
    }

    /**
     * @return true if any tag in the list passes the test
     */
    public boolean match(final Predicate<TagKey<T>> test) {
        return this.snapshot().stream().anyMatch(test);
    }

    /**
     * @return the first tag in the list that passes the test
     */
    public Optional<TagKey<T>> findFirst(final Predicate<TagKey<T>> test) {
        return this.snapshot().stream().filter(test).findFirst();
    }

    public Stream<TagKey<T>> stream() {
        return this.snapshot().stream();
    }

    /**
     * @return the first object in the given tag
     */
    public Optional<T> first(final TagKey<T> tag) {
        return this._helper.getFirstObject(tag);
    }

    /**
     * Run the action on each object in the given tag.
     */
    public void forEach(final TagKey<T> tag, final Consumer<T> action) {
        this._helper.getObjects(tag).forEach(action);
    }

    //region internals

    private TagList(final TagsHelper<T> helper) {

        this._helper = helper;
        this._tags = Lists.newArrayList();
    }

    private List<TagKey<T>> snapshot() {

        synchronized (this._tags) {
            return List.copyOf(this._tags);
        }
    }

    private final TagsHelper<T> _helper;
    private final List<TagKey<T>> _tags;

    //endregion
}

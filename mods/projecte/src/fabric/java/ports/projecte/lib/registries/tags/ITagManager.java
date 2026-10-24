/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.registries.tags;

import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import org.jetbrains.annotations.NotNull;

public interface ITagManager<V> extends Iterable<ITag<V>> {

    @NotNull
    ITag<V> getTag(@NotNull TagKey<V> name);

    @NotNull
    Optional<IReverseTag<V>> getReverseTag(@NotNull V value);

    boolean isKnownTagName(@NotNull TagKey<V> name);

    @NotNull
    Stream<ITag<V>> stream();

    @NotNull
    Stream<TagKey<V>> getTagNames();

    @NotNull
    TagKey<V> createTagKey(@NotNull ResourceLocation location);

    @NotNull
    TagKey<V> createOptionalTagKey(@NotNull ResourceLocation location, @NotNull Set<? extends Supplier<V>> defaults);

    void addOptionalTagDefaults(@NotNull TagKey<V> name, @NotNull Set<? extends Supplier<V>> defaults);
}

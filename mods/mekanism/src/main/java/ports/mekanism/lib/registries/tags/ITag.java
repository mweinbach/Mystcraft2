/*
 * Mekanism 1.19.2 port - loader-neutral mirror of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.registries.tags;

import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.NotNull;

public interface ITag<V> extends Iterable<V> {

    @NotNull
    TagKey<V> getKey();

    boolean isBound();

    boolean isEmpty();

    int size();

    boolean contains(@NotNull V value);

    @NotNull
    Stream<V> stream();

    @NotNull
    Optional<V> getRandomElement(@NotNull RandomSource random);
}

/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.registries.tags;

import java.util.stream.Stream;
import net.minecraft.tags.TagKey;
import org.jetbrains.annotations.NotNull;

public interface IReverseTag<V> {

    @NotNull
    Stream<TagKey<V>> getTagKeys();

    boolean containsTag(@NotNull TagKey<V> key);
}

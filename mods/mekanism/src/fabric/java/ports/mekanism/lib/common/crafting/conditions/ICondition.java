/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.common.crafting.conditions;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

/**
 * A load condition in a data file's {@code conditions} array.
 */
public interface ICondition {

    ResourceLocation getID();

    boolean test(IContext context);

    interface IContext {

        IContext EMPTY = new IContext() {
            @Override
            public <T> Map<ResourceLocation, Collection<Holder<T>>> getAllTags(ResourceKey<? extends Registry<T>> registry) {
                return Collections.emptyMap();
            }
        };

        default <T> Collection<Holder<T>> getTag(TagKey<T> key) {
            return getAllTags(key.registry()).getOrDefault(key.location(), Collections.emptySet());
        }

        <T> Map<ResourceLocation, Collection<Holder<T>>> getAllTags(ResourceKey<? extends Registry<T>> registry);
    }
}

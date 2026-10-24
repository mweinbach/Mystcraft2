/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.patched;

import com.google.gson.JsonObject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Forge's custom item predicates: {@code ItemPredicate.register}, read by a mixin on {@code ItemPredicate.fromJson}
 * for objects whose {@code type} names a registered predicate.
 */
public final class ForgeItemPredicates {

    private static final Map<ResourceLocation, Function<JsonObject, ItemPredicate>> PREDICATES = new ConcurrentHashMap<>();

    private ForgeItemPredicates() {
    }

    public static void register(ResourceLocation name, Function<JsonObject, ItemPredicate> deserializer) {
        PREDICATES.put(name, deserializer);
    }

    @Nullable
    public static Function<JsonObject, ItemPredicate> get(ResourceLocation name) {
        return PREDICATES.get(name);
    }
}

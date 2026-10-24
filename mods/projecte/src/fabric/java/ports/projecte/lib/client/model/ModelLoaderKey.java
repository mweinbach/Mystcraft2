/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.client.model;

import com.google.gson.JsonObject;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

/**
 * The {@code loader} key of a model JSON, as the port's model reader sees it.
 * <p>
 * The reader runs on every model of every mod, not only Mekanism's. Forge fails a model whose loader is unknown, but
 * on Fabric other libraries read the same key with their own loaders: in Tekxit 4, Porting Lib loads
 * {@code forge:item_layers} and {@code forge:bucket}, and AE2, CTM, Twilight Forest, Mantle and Snowy Spirit register
 * theirs. Failing those models broke more than a hundred of them, so a loader the port does not know is left to
 * whoever does.
 */
public final class ModelLoaderKey {

    private static final Set<ResourceLocation> UNKNOWN = ConcurrentHashMap.newKeySet();

    private ModelLoaderKey() {
    }

    /**
     * @param registry  the port's loaders by name
     * @param onUnknown told once per name that is not in {@code registry}
     * @return the port's loader named by {@code json}, or null if it names none or one the port does not know
     */
    @Nullable
    public static <L> L resolve(JsonObject json, Function<ResourceLocation, L> registry, Consumer<ResourceLocation> onUnknown) {
        if (!json.has("loader")) {
            return null;
        }
        ResourceLocation name = new ResourceLocation(GsonHelper.getAsString(json, "loader"));
        L loader = registry.apply(name);
        if (loader == null && UNKNOWN.add(name)) {
            onUnknown.accept(name);
        }
        return loader;
    }
}

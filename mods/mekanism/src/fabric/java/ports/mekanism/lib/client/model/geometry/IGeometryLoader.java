/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.client.model.geometry;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/**
 * Reads the geometry of a block model JSON whose {@code loader} names this loader.
 */
public interface IGeometryLoader<T extends IUnbakedGeometry<T>> extends ResourceManagerReloadListener {

    T read(JsonObject jsonObject, JsonDeserializationContext deserializationContext) throws JsonParseException;

    @Override
    default void onResourceManagerReload(ResourceManager resourceManager) {
    }
}

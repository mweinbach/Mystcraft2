/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.client.model.geometry;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * The geometry loaders registered through {@code ModelEvent.RegisterGeometryLoaders}, by the name model JSON uses in
 * its {@code loader} field.
 */
public final class GeometryLoaderManager {

    private static final Map<ResourceLocation, IGeometryLoader<?>> LOADERS = new HashMap<>();

    private GeometryLoaderManager() {
    }

    public static synchronized void register(ResourceLocation name, IGeometryLoader<?> loader) {
        if (LOADERS.putIfAbsent(name, loader) != null) {
            throw new IllegalStateException("Geometry loader already registered with name " + name);
        }
    }

    @Nullable
    public static synchronized IGeometryLoader<?> get(ResourceLocation name) {
        return LOADERS.get(name);
    }

    public static synchronized Iterable<IGeometryLoader<?>> all() {
        return Map.copyOf(LOADERS).values();
    }
}

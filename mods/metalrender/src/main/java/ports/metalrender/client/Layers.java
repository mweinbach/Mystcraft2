/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.client;

import javax.annotation.Nullable;
import net.minecraft.client.renderer.RenderType;
import ports.metalrender.terrain.TerrainLayer;

/** Maps vanilla chunk {@link RenderType}s to {@link TerrainLayer}s. */
public final class Layers {

    private Layers() {
    }

    /** The Metal layer for a chunk render type, or null for layers vanilla keeps (translucent, tripwire). */
    @Nullable
    public static TerrainLayer of(final RenderType type) {
        if (type == RenderType.solid()) {
            return TerrainLayer.SOLID;
        }
        if (type == RenderType.cutoutMipped()) {
            return TerrainLayer.CUTOUT_MIPPED;
        }
        if (type == RenderType.cutout()) {
            return TerrainLayer.CUTOUT;
        }
        return null;
    }

    public static RenderType renderType(final TerrainLayer layer) {
        return switch (layer) {
            case SOLID -> RenderType.solid();
            case CUTOUT_MIPPED -> RenderType.cutoutMipped();
            case CUTOUT -> RenderType.cutout();
        };
    }
}

/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.client;

import javax.annotation.Nullable;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import ports.metalrender.terrain.TerrainLayer;

/**
 * Fields mixed into {@code VertexBuffer}: a chunk layer buffer knows which section and layer it
 * belongs to, so {@code uploadChunkLayer} can route its data to Metal instead.
 */
public interface VertexBufferExt {

    void metalrender$tag(ChunkRenderDispatcher.RenderChunk chunk, TerrainLayer layer);

    @Nullable
    ChunkRenderDispatcher.RenderChunk metalrender$chunk();

    @Nullable
    TerrainLayer metalrender$layer();
}

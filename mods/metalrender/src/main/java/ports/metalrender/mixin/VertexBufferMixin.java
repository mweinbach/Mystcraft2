/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.mixin;

import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import ports.metalrender.client.VertexBufferExt;
import ports.metalrender.terrain.TerrainLayer;

/** Lets a chunk layer VertexBuffer remember its section and layer. */
@Mixin(VertexBuffer.class)
public abstract class VertexBufferMixin implements VertexBufferExt {

    @Unique
    private ChunkRenderDispatcher.RenderChunk metalrender$chunk;
    @Unique
    private TerrainLayer metalrender$layer;

    @Override
    public void metalrender$tag(final ChunkRenderDispatcher.RenderChunk chunk, final TerrainLayer layer) {
        this.metalrender$chunk = chunk;
        this.metalrender$layer = layer;
    }

    @Override
    public ChunkRenderDispatcher.RenderChunk metalrender$chunk() {
        return metalrender$chunk;
    }

    @Override
    public TerrainLayer metalrender$layer() {
        return metalrender$layer;
    }
}

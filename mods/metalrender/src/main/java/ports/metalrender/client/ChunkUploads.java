/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.core.BlockPos;
import ports.metalrender.MetalRenderMod;
import ports.metalrender.terrain.PackedLayer;
import ports.metalrender.terrain.TerrainLayer;
import ports.metalrender.terrain.OpaqueSprites;
import ports.metalrender.terrain.TerrainVertexPacker;

/**
 * Replacement for {@code ChunkRenderDispatcher.uploadChunkLayer}. Runs on the chunk worker thread:
 * converts the layer to Metal vertices, releases vanilla's buffer, and schedules the copy into the
 * Metal arena on vanilla's own render-thread upload queue.
 */
public final class ChunkUploads {

    private static boolean warnedFormat;

    private ChunkUploads() {
    }

    /** Returns null to let vanilla upload the buffer itself. */
    @Nullable
    public static CompletableFuture<Void> submit(final BufferBuilder.RenderedBuffer buffer, final VertexBuffer target,
                                                 final Executor renderThread) {
        if (!MetalRenderClient.isActive()) {
            return null;
        }
        final VertexBufferExt tag = (VertexBufferExt) target;
        final ChunkRenderDispatcher.RenderChunk chunk = tag.metalrender$chunk();
        final TerrainLayer layer = tag.metalrender$layer();
        if (chunk == null || layer == null) {
            return null;
        }
        final BufferBuilder.DrawState state = buffer.drawState();
        if (state.indexOnly()) {
            return null; // a re-sort; vanilla only does those for translucent layers
        }
        if (state.mode() != VertexFormat.Mode.QUADS || !state.sequentialIndex()
                || state.format() != DefaultVertexFormat.BLOCK) {
            if (!warnedFormat) {
                warnedFormat = true;
                MetalRenderMod.LOGGER.warn("MetalRender skipped a chunk layer with format {} / mode {}; it will not be drawn",
                        state.format(), state.mode());
            }
            buffer.release();
            return CompletableFuture.completedFuture(null);
        }
        final int epoch = MetalRenderClient.epoch();
        final int generation = ((RenderChunkExt) chunk).metalrender$generation();
        final BlockPos origin = chunk.getOrigin().immutable();
        final PackedLayer packed;
        final long start = System.nanoTime();
        try {
            packed = TerrainVertexPacker.pack(buffer.vertexBuffer(), state.vertexCount(), layer.mode,
                    OpaqueSprites.current());
            PerfStats.PACK_NANOS.add(System.nanoTime() - start);
            PerfStats.PACKED_QUADS.add(packed.quads());
        } catch (final RuntimeException e) {
            MetalRenderMod.LOGGER.error("MetalRender could not convert a {} layer at {}", layer, origin, e);
            return CompletableFuture.completedFuture(null);
        } finally {
            buffer.release();
        }
        return CompletableFuture.runAsync(
                () -> MetalTerrainRenderer.applyUpload(chunk, layer, packed, epoch, generation, origin), renderThread);
    }
}

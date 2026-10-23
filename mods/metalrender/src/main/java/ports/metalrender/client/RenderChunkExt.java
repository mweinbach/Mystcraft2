/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.client;

import javax.annotation.Nullable;
import ports.metalrender.terrain.SectionSlot;

/** Fields mixed into {@code ChunkRenderDispatcher.RenderChunk}. */
public interface RenderChunkExt {

    /** Bumped every time vanilla resets the chunk (moved or released); stale uploads compare it. */
    int metalrender$generation();

    /** True once {@code releaseBuffers()} has run: the chunk will never render again. */
    boolean metalrender$released();

    @Nullable
    SectionSlot metalrender$slot();

    void metalrender$setSlot(@Nullable SectionSlot slot);
}

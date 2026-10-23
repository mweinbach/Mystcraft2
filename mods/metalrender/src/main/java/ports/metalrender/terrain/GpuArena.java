/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.terrain;

import java.nio.ByteBuffer;

/**
 * The three GPU-visible buffers a {@link SectionStore} writes into. In game these are shared-storage
 * MTLBuffers exposed as direct ByteBuffers; tests use heap buffers. Buffers returned by the getters
 * must be little-endian and may change identity after a successful grow call.
 */
public interface GpuArena {

    ByteBuffer vertices();

    /** Grows the vertex buffer to {@code vertexCapacity} vertices, preserving its contents. */
    boolean growVertices(int vertexCapacity);

    ByteBuffer sections();

    boolean growSections(int sectionCapacity);

    ByteBuffer meshlets();

    boolean growMeshlets(int meshletCapacity);

    /** Makes the shared 0,1,2,2,3,0 quad index pattern cover at least {@code quads} quads. */
    void ensurePatternQuads(int quads);
}

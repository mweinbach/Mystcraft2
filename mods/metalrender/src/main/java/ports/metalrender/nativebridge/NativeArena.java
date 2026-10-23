/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.nativebridge;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import ports.metalrender.terrain.GpuArena;

/**
 * {@link GpuArena} over a native context's shared-storage MTLBuffers. JNI direct buffers default to
 * big-endian, so every view is re-ordered to little-endian; views are re-fetched after a resize
 * because the native side replaces the MTLBuffer.
 */
public final class NativeArena implements GpuArena {

    private final long ctx;
    private ByteBuffer vertices;
    private ByteBuffer sections;
    private ByteBuffer meshlets;

    public NativeArena(final long ctx) {
        this.ctx = ctx;
        refresh();
    }

    private void refresh() {
        vertices = MetalNative.getVertexArenaBuffer(ctx).order(ByteOrder.LITTLE_ENDIAN);
        sections = MetalNative.getSectionMetaBuffer(ctx).order(ByteOrder.LITTLE_ENDIAN);
        meshlets = MetalNative.getMeshletMetaBuffer(ctx).order(ByteOrder.LITTLE_ENDIAN);
    }

    @Override
    public ByteBuffer vertices() {
        return vertices;
    }

    @Override
    public boolean growVertices(final int vertexCapacity) {
        final boolean ok = MetalNative.resizeVertexArena(ctx, vertexCapacity);
        refresh();
        return ok;
    }

    @Override
    public ByteBuffer sections() {
        return sections;
    }

    @Override
    public boolean growSections(final int sectionCapacity) {
        final boolean ok = MetalNative.resizeSectionMeta(ctx, sectionCapacity);
        refresh();
        return ok;
    }

    @Override
    public ByteBuffer meshlets() {
        return meshlets;
    }

    @Override
    public boolean growMeshlets(final int meshletCapacity) {
        final boolean ok = MetalNative.resizeMeshletMeta(ctx, meshletCapacity);
        refresh();
        return ok;
    }

    @Override
    public void ensurePatternQuads(final int quads) {
        MetalNative.ensurePatternQuadCapacity(ctx, quads);
    }
}

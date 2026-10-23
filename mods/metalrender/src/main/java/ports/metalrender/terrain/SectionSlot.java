/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.terrain;

import javax.annotation.Nullable;

/**
 * Bookkeeping for one 16x16x16 chunk section resident in the Metal buffers. Owned and mutated only
 * by {@link SectionStore} on the render thread.
 */
public final class SectionSlot {

    private static final int GROUPS = TerrainLayer.Group.values().length;

    final int index;
    final int originX;
    final int originY;
    final int originZ;
    final int[] quads = new int[TerrainLayer.values().length];
    final float[][] bounds = new float[TerrainLayer.values().length][];
    /** First vertex of each group's contiguous range, or -1. */
    final int[] groupStart = {-1, -1};
    /** Quads allocated for each group (sum of its layers). */
    final int[] groupQuads = new int[GROUPS];
    int meshletStart = -1;
    int meshletCount;
    boolean released;
    /** The game object this section mirrors (a vanilla RenderChunk in game). */
    @Nullable
    final Object owner;

    SectionSlot(final int index, final int originX, final int originY, final int originZ, @Nullable final Object owner) {
        this.index = index;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.owner = owner;
    }

    @Nullable
    public Object owner() {
        return owner;
    }

    public int index() {
        return index;
    }

    public int originX() {
        return originX;
    }

    public int originY() {
        return originY;
    }

    public int originZ() {
        return originZ;
    }

    public int quads(final TerrainLayer layer) {
        return quads[layer.ordinal()];
    }

    public int totalQuads() {
        int total = 0;
        for (final int q : groupQuads) {
            total += q;
        }
        return total;
    }

    public boolean isReleased() {
        return released;
    }
}

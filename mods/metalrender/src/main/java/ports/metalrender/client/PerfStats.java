/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.client;

import java.util.concurrent.atomic.LongAdder;

/**
 * Cumulative CPU timings for the benchmark and F3. Readers take differences between two snapshots.
 * Render-thread fields are plain longs; worker-thread fields are {@link LongAdder}s.
 */
public final class PerfStats {

    /** Worker threads: converting vanilla vertices to Metal vertices. */
    public static final LongAdder PACK_NANOS = new LongAdder();
    public static final LongAdder PACKED_QUADS = new LongAdder();

    /** Render thread: applying converted layers to the section store. */
    public static long uploadNanos;
    public static long uploads;

    /** Render thread: all Metal opaque pass work (begin, wait, composite), excluding the overlap. */
    public static long opaqueNanos;
    /** Render thread: preparing, encoding and committing the pass. */
    public static long beginNanos;
    /** Render thread: blocked waiting for the GPU to finish the pass. */
    public static long waitNanos;
    /** Render thread: vanilla work done between committing the pass and waiting for it. */
    public static long overlapNanos;
    public static long opaqueFrames;

    /** Render thread: vanilla solid and cutout {@code renderChunkLayer} calls (when Metal is off). */
    public static long vanillaTerrainNanos;

    /** Render thread: mirroring animated sprite frames into the Metal atlas. */
    public static long spriteUploadNanos;
    public static long spriteUploads;

    private PerfStats() {
    }
}

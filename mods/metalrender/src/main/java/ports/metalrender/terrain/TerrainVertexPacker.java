/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.terrain;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Converts vanilla {@code DefaultVertexFormat.BLOCK} quads (32 bytes per vertex, section-local
 * positions) into the 24-byte {@code MetalTerrainVertex} layout from {@code metal_shared.h}.
 *
 * <p>Runs on chunk worker threads. In cutout-mipped layers, quads whose texture is fully opaque are
 * moved ahead of the rest (solid and cutout layers are depth-tested without blending, so their order
 * does not matter) and get their own meshlets, which the mesh-shader path draws without the alpha
 * test. Each run is cut into 32-quad meshlets, whose bounds are computed here as well, which keeps
 * that work off the render thread.
 */
public final class TerrainVertexPacker {

    /** Vanilla BLOCK format: position 3f, color 4ub, uv0 2f, uv2 2s, normal 3b, padding. */
    public static final int SOURCE_STRIDE = 32;
    public static final int VERTEX_BYTES = 24;
    public static final int QUAD_BYTES = VERTEX_BYTES * 4;
    public static final int MESHLET_QUADS = 32;
    public static final int MESHLET_BYTES = 32;

    /** Meshlet cone cutoff byte that disables normal-cone culling. */
    private static final byte CONE_OFF = -127;

    private TerrainVertexPacker() {
    }

    /**
     * @param source      vertex data starting at index 0 (any byte order; read as little-endian,
     *                    which is the native order on every Mac)
     * @param vertexCount multiple of 4
     * @param mode        MR_MODE_* for every vertex
     */
    public static PackedLayer pack(final ByteBuffer source, final int vertexCount, final int mode) {
        return pack(source, vertexCount, mode, OpaqueSprites.NONE);
    }

    /**
     * @param opaque block atlas map; cutout-mipped quads that only sample fully opaque sprites are
     *               put in meshlets without the alpha test
     */
    public static PackedLayer pack(final ByteBuffer source, final int vertexCount, final int mode,
                                   final OpaqueSprites opaque) {
        if (vertexCount % 4 != 0) {
            throw new IllegalArgumentException("vertex count " + vertexCount + " is not a whole number of quads");
        }
        final int quads = vertexCount / 4;
        final ByteBuffer src = source.duplicate().order(ByteOrder.LITTLE_ENDIAN);

        // Pass 1: which quads need the alpha test. Solid layers never do; cutout layers do unless the
        // layer is mipmapped and the quad only samples fully opaque sprites.
        final boolean cutout = mode != TerrainLayer.SOLID.mode;
        final boolean mipped = mode == TerrainLayer.CUTOUT_MIPPED.mode;
        final boolean[] alphaTest = new boolean[quads];
        int opaqueQuads = 0;
        for (int q = 0; q < quads; q++) {
            alphaTest[q] = cutout && !(mipped && samplesOnlyOpaque(src, q * 4 * SOURCE_STRIDE, opaque));
            if (!alphaTest[q]) {
                opaqueQuads++;
            }
        }
        final int[] counts = {opaqueQuads, quads - opaqueQuads};
        final int[] next = {0, opaqueQuads};
        final int meshletCount = (counts[0] + MESHLET_QUADS - 1) / MESHLET_QUADS
                + (counts[1] + MESHLET_QUADS - 1) / MESHLET_QUADS;

        // Pass 2: convert, writing the opaque quads first and the alpha-tested ones after them.
        final ByteBuffer out = ByteBuffer.allocate(quads * QUAD_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        final float[] bounds = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE,
                -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int q = 0; q < quads; q++) {
            int dst = next[alphaTest[q] ? 1 : 0]++ * QUAD_BYTES;
            for (int v = 0; v < 4; v++) {
                final int s = (q * 4 + v) * SOURCE_STRIDE;
                final float x = src.getFloat(s);
                final float y = src.getFloat(s + 4);
                final float z = src.getFloat(s + 8);
                bounds[0] = Math.min(bounds[0], x);
                bounds[1] = Math.min(bounds[1], y);
                bounds[2] = Math.min(bounds[2], z);
                bounds[3] = Math.max(bounds[3], x);
                bounds[4] = Math.max(bounds[4], y);
                bounds[5] = Math.max(bounds[5], z);
                out.putShort(dst, quantize(x));
                out.putShort(dst + 2, quantize(y));
                out.putShort(dst + 4, quantize(z));
                out.put(dst + 6, light(src.getShort(s + 24)));
                out.put(dst + 7, light(src.getShort(s + 26)));
                out.putInt(dst + 8, src.getInt(s + 12)); // r, g, b, a bytes unchanged
                out.putFloat(dst + 12, src.getFloat(s + 16));
                out.putFloat(dst + 16, src.getFloat(s + 20));
                out.put(dst + 20, src.get(s + 28));
                out.put(dst + 21, src.get(s + 29));
                out.put(dst + 22, src.get(s + 30));
                out.put(dst + 23, (byte) mode);
                dst += VERTEX_BYTES;
            }
        }

        // Pass 3: meshlets, never spanning the two runs.
        final ByteBuffer meshlets = ByteBuffer.allocate(meshletCount * MESHLET_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        int m = 0;
        for (int run = 0, first = 0; run < 2; first += counts[run], run++) {
            for (int q = 0; q < counts[run]; q += MESHLET_QUADS) {
                writeMeshlet(out, meshlets, m++, first + q, Math.min(MESHLET_QUADS, counts[run] - q), run == 1);
            }
        }
        return new PackedLayer(out, quads, quads == 0 ? null : bounds, meshlets, meshletCount);
    }

    /** True when the quad's UV rectangle lies only in fully opaque parts of the atlas. */
    private static boolean samplesOnlyOpaque(final ByteBuffer src, final int s, final OpaqueSprites opaque) {
        float u0 = Float.MAX_VALUE, v0 = Float.MAX_VALUE, u1 = -Float.MAX_VALUE, v1 = -Float.MAX_VALUE;
        for (int v = 0; v < 4; v++) {
            final float u = src.getFloat(s + v * SOURCE_STRIDE + 16);
            final float w = src.getFloat(s + v * SOURCE_STRIDE + 20);
            u0 = Math.min(u0, u);
            v0 = Math.min(v0, w);
            u1 = Math.max(u1, u);
            v1 = Math.max(v1, w);
        }
        return opaque.isOpaque(u0, v0, u1, v1);
    }

    /**
     * Writes a 32-byte {@code MeshletGpuMeta} template. {@code sectionIndex} is left 0 and
     * {@code baseVertex} is relative to the layer; the section store patches both. {@code isCutout}
     * selects the alpha-tested draw.
     */
    private static void writeMeshlet(final ByteBuffer vertices, final ByteBuffer out, final int meshlet,
                                     final int firstQuad, final int quads, final boolean alphaTest) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int v = firstQuad * 4, end = (firstQuad + quads) * 4; v < end; v++) {
            final int at = v * VERTEX_BYTES;
            // The GPU sees the quantized positions, so bound those.
            final float x = dequantize(vertices.getShort(at));
            final float y = dequantize(vertices.getShort(at + 2));
            final float z = dequantize(vertices.getShort(at + 4));
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }
        final float hx = (maxX - minX) * 0.5f;
        final float hy = (maxY - minY) * 0.5f;
        final float hz = (maxZ - minZ) * 0.5f;
        final int b = meshlet * MESHLET_BYTES;
        out.putFloat(b, minX + hx);
        out.putFloat(b + 4, minY + hy);
        out.putFloat(b + 8, minZ + hz);
        out.putFloat(b + 12, (float) Math.sqrt(hx * hx + hy * hy + hz * hz) + 0.01f);
        out.putInt(b + 16, 0);              // section index: patched
        out.putInt(b + 20, firstQuad * 4);  // base vertex: patched to an arena index
        out.putShort(b + 24, (short) quads);
        out.put(b + 26, (byte) (alphaTest ? 1 : 0));
        out.put(b + 27, (byte) 0);          // not LOD
        out.put(b + 28, (byte) 0);          // no normal cone: hardware back-face culling is cheaper
        out.put(b + 29, (byte) 0);
        out.put(b + 30, (byte) 0);
        out.put(b + 31, CONE_OFF);
    }

    /** Section-local coordinate to the shader's int16 (local - 8) * 1024 encoding. */
    public static short quantize(final float local) {
        final long q = Math.round((local - 8.0f) * 1024.0f);
        return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, q));
    }

    public static float dequantize(final short q) {
        return 8.0f + q / 1024.0f;
    }

    /** Vanilla UV2 components are block/sky light * 16 (0..240). */
    private static byte light(final short uv2) {
        return (byte) Math.max(0, Math.min(255, uv2));
    }
}

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
 */
public final class TerrainVertexPacker {

    /** Vanilla BLOCK format: position 3f, color 4ub, uv0 2f, uv2 2s, normal 3b, padding. */
    public static final int SOURCE_STRIDE = 32;
    public static final int VERTEX_BYTES = 24;
    public static final int QUAD_BYTES = VERTEX_BYTES * 4;

    private TerrainVertexPacker() {
    }

    /**
     * @param source      vertex data starting at index 0 (any byte order; read as little-endian,
     *                    which is the native order on every Mac)
     * @param vertexCount multiple of 4
     * @param mode        MR_MODE_* for every vertex
     */
    public static PackedLayer pack(final ByteBuffer source, final int vertexCount, final int mode) {
        if (vertexCount % 4 != 0) {
            throw new IllegalArgumentException("vertex count " + vertexCount + " is not a whole number of quads");
        }
        final int quads = vertexCount / 4;
        final ByteBuffer src = source.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        final ByteBuffer out = ByteBuffer.allocate(quads * QUAD_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        final float[] bounds = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE,
                -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        int dst = 0;
        for (int v = 0; v < vertexCount; v++) {
            final int s = v * SOURCE_STRIDE;
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
        return new PackedLayer(out, quads, quads == 0 ? null : bounds);
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

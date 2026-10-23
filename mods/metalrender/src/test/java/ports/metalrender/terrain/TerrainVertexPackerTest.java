/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.terrain;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

class TerrainVertexPackerTest {

    /** Builds vanilla BLOCK-format quads; quad q has x offset q and colour byte q. */
    static ByteBuffer blockQuads(final int quads) {
        final ByteBuffer b = ByteBuffer.allocate(quads * 4 * 32).order(ByteOrder.LITTLE_ENDIAN);
        final float[][] corners = {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}};
        for (int q = 0; q < quads; q++) {
            for (int v = 0; v < 4; v++) {
                final int at = (q * 4 + v) * 32;
                b.putFloat(at, corners[v][0] + q);
                b.putFloat(at + 4, corners[v][1]);
                b.putFloat(at + 8, corners[v][2]);
                b.put(at + 12, (byte) q).put(at + 13, (byte) 20).put(at + 14, (byte) 30).put(at + 15, (byte) 255);
                b.putFloat(at + 16, 0.25f * v);
                b.putFloat(at + 20, 0.5f);
                b.putShort(at + 24, (short) 240);
                b.putShort(at + 26, (short) 128);
                b.put(at + 28, (byte) 0).put(at + 29, (byte) 127).put(at + 30, (byte) 0);
            }
        }
        return b;
    }

    @Test
    void convertsBlockVerticesToTheMetalLayout() {
        final PackedLayer p = TerrainVertexPacker.pack(blockQuads(1), 4, TerrainLayer.CUTOUT.mode);
        assertEquals(1, p.quads());
        final ByteBuffer d = p.data();
        assertEquals(TerrainVertexPacker.QUAD_BYTES, d.capacity());
        // vertex 2: (1, 1, 1)
        final int v = 2 * 24;
        assertEquals((short) ((1 - 8) * 1024), d.getShort(v));
        assertEquals((short) ((1 - 8) * 1024), d.getShort(v + 2));
        assertEquals((short) ((1 - 8) * 1024), d.getShort(v + 4));
        assertEquals((byte) 240, d.get(v + 6), "block light");
        assertEquals((byte) 128, d.get(v + 7), "sky light");
        assertEquals(0, d.get(v + 8));
        assertEquals(20, d.get(v + 9));
        assertEquals(30, d.get(v + 10));
        assertEquals((byte) 255, d.get(v + 11));
        assertEquals(0.5f, d.getFloat(v + 12));
        assertEquals(0.5f, d.getFloat(v + 16));
        assertEquals(127, d.get(v + 21), "normal y");
        assertEquals(TerrainLayer.CUTOUT.mode, d.get(v + 23));
        assertArrayEquals(new float[] {0, 1, 0, 1, 1, 1}, p.bounds());
    }

    /** One quad from four corners, all other attributes zero. */
    private static void putQuad(final ByteBuffer b, final int quad, final float[][] corners) {
        for (int v = 0; v < 4; v++) {
            final int at = (quad * 4 + v) * 32;
            b.putFloat(at, corners[v][0]).putFloat(at + 4, corners[v][1]).putFloat(at + 8, corners[v][2]);
        }
    }

    @Test
    void cutsRunsIntoMeshletsWithBounds() {
        final ByteBuffer b = ByteBuffer.allocate(3 * 4 * 32).order(ByteOrder.LITTLE_ENDIAN);
        putQuad(b, 0, new float[][] {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}});
        putQuad(b, 1, new float[][] {{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}});
        putQuad(b, 2, new float[][] {{2, 1, 0}, {2, 1, 1}, {3, 1, 1}, {3, 1, 0}});
        final PackedLayer p = TerrainVertexPacker.pack(b, 12, TerrainLayer.CUTOUT.mode);
        assertEquals(1, p.meshletCount());
        final ByteBuffer m = p.meshlets();
        assertEquals(1.5f, m.getFloat(0), 1e-3f, "centre x");
        assertEquals(0.5f, m.getFloat(4), 1e-3f, "centre y");
        assertEquals(0, m.getInt(20), "base vertex, relative to the layer");
        assertEquals(3, m.getShort(24));
        assertEquals(1, m.get(26), "alpha-tested");
        assertEquals(-127, m.get(31), "normal-cone culling off");
        assertEquals(TerrainVertexPacker.quantize(1.0f), p.data().getShort(2), "vanilla quad order kept");
    }

    /** Sets the UV rectangle of a quad (one corner per vertex). */
    private static void putUv(final ByteBuffer b, final int quad, final float u0, final float v0, final float u1,
                              final float v1) {
        final float[][] uv = {{u0, v0}, {u0, v1}, {u1, v1}, {u1, v0}};
        for (int v = 0; v < 4; v++) {
            b.putFloat((quad * 4 + v) * 32 + 16, uv[v][0]).putFloat((quad * 4 + v) * 32 + 20, uv[v][1]);
        }
    }

    @Test
    void skipsTheAlphaTestOnlyForCutoutMippedQuadsOnOpaqueSprites() {
        // 64x32 atlas: an opaque sprite at (0, 0) and a transparent one at (16, 0), both 16x16.
        final OpaqueSprites atlas = new OpaqueSprites.Builder(64, 32)
                .sprite(0, 0, 16, 16, true).sprite(16, 0, 16, 16, false).build();
        final ByteBuffer b = ByteBuffer.allocate(2 * 4 * 32).order(ByteOrder.LITTLE_ENDIAN);
        putQuad(b, 0, new float[][] {{2, 1, 0}, {2, 1, 1}, {3, 1, 1}, {3, 1, 0}}); // up, transparent sprite
        putQuad(b, 1, new float[][] {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}}); // up, opaque sprite
        putUv(b, 0, 0.25f, 0.0f, 0.5f, 0.5f);
        putUv(b, 1, 0.0f, 0.0f, 0.25f, 0.5f);

        final PackedLayer mipped = TerrainVertexPacker.pack(b, 8, TerrainLayer.CUTOUT_MIPPED.mode, atlas);
        assertEquals(2, mipped.meshletCount(), "opaque and alpha-tested quads never share a meshlet");
        assertEquals(0, mipped.meshlets().get(26), "opaque run first, drawn without the alpha test");
        assertEquals(0, mipped.meshlets().getInt(20));
        assertEquals((short) ((0 - 8) * 1024), mipped.data().getShort(0), "the opaque-sprite quad moved first");
        assertEquals(1, mipped.meshlets().get(32 + 26));
        assertEquals(4, mipped.meshlets().getInt(32 + 20));

        final PackedLayer cutout = TerrainVertexPacker.pack(b, 8, TerrainLayer.CUTOUT.mode, atlas);
        assertEquals(1, cutout.meshletCount(), "unmipped cutout keeps its sharp sampling and alpha test");
        assertEquals(1, cutout.meshlets().get(26));

        final PackedLayer solid = TerrainVertexPacker.pack(b, 8, TerrainLayer.SOLID.mode, atlas);
        assertEquals(0, solid.meshlets().get(26));
    }

    @Test
    void opaqueMapIsConservative() {
        final OpaqueSprites atlas = new OpaqueSprites.Builder(64, 32)
                .sprite(0, 0, 16, 16, true)
                .sprite(16, 0, 8, 8, true).sprite(24, 0, 8, 8, false) // share a cell
                .build();
        assertTrue(atlas.isOpaque(0.0f, 0.0f, 0.25f, 0.5f), "whole opaque sprite, edges included");
        assertTrue(atlas.isOpaque(0.0f, 0.0f, 0.25f + 1.0e-7f, 0.5f), "float error at the edge");
        assertFalse(atlas.isOpaque(0.0f, 0.0f, 0.26f, 0.5f), "spills into the next cell");
        assertFalse(atlas.isOpaque(0.25f, 0.0f, 0.375f, 0.25f), "opaque sprite in a cell with a transparent one");
        assertFalse(atlas.isOpaque(0.5f, 0.0f, 0.75f, 0.5f), "no sprite");
        assertFalse(OpaqueSprites.NONE.isOpaque(0.0f, 0.0f, 0.25f, 0.5f));
    }

    @Test
    void quantizesAroundTheSectionCentre() {
        assertEquals(0, TerrainVertexPacker.quantize(8.0f));
        assertEquals(-8192, TerrainVertexPacker.quantize(0.0f));
        assertEquals(8192, TerrainVertexPacker.quantize(16.0f));
        assertEquals(Short.MAX_VALUE, TerrainVertexPacker.quantize(1000.0f));
        assertEquals(16.0f, TerrainVertexPacker.dequantize(TerrainVertexPacker.quantize(16.0f)));
    }
}

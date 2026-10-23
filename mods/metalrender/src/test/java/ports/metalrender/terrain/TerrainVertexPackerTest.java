/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.terrain;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

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

    @Test
    void quantizesAroundTheSectionCentre() {
        assertEquals(0, TerrainVertexPacker.quantize(8.0f));
        assertEquals(-8192, TerrainVertexPacker.quantize(0.0f));
        assertEquals(8192, TerrainVertexPacker.quantize(16.0f));
        assertEquals(Short.MAX_VALUE, TerrainVertexPacker.quantize(1000.0f));
        assertEquals(16.0f, TerrainVertexPacker.dequantize(TerrainVertexPacker.quantize(16.0f)));
    }
}

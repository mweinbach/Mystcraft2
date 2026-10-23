/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class SectionStoreTest {

    private static PackedLayer layer(final int quads, final TerrainLayer layer) {
        return TerrainVertexPacker.pack(TerrainVertexPackerTest.blockQuads(quads), quads * 4, layer.mode);
    }

    private static int meta(final SectionStore.HeapArena arena, final SectionSlot slot, final int offset) {
        return arena.sections().getInt(slot.index() * SectionStore.SECTION_BYTES + offset);
    }

    @Test
    void writesSectionMetaAndMeshlets() {
        final SectionStore.HeapArena arena = new SectionStore.HeapArena(4096, 4, 4);
        final SectionStore store = new SectionStore(arena);
        final SectionSlot slot = store.allocate(32, -64, 16, null);
        assertNotNull(slot);
        assertTrue(store.applyLayer(slot, TerrainLayer.SOLID, layer(40, TerrainLayer.SOLID)));
        assertEquals(32, meta(arena, slot, 0));
        assertEquals(-64, meta(arena, slot, 4));
        assertEquals(16, meta(arena, slot, 8));
        assertEquals(SectionStore.FLAG_ACTIVE, meta(arena, slot, 12));
        assertEquals(40, meta(arena, slot, 52), "solid quad count");
        assertEquals(2, meta(arena, slot, 92), "40 quads -> two meshlets");
        final ByteBuffer meshlets = arena.meshlets();
        final int first = meta(arena, slot, 88) * SectionStore.MESHLET_BYTES;
        assertEquals(32, meshlets.getShort(first + 24));
        assertEquals(8, meshlets.getShort(first + SectionStore.MESHLET_BYTES + 24));
        assertEquals(-127, meshlets.get(first + 31), "normal-cone culling off");
        assertEquals(meta(arena, slot, 48) + 32 * 4, meshlets.getInt(first + SectionStore.MESHLET_BYTES + 20),
                "second meshlet starts 32 quads into the section's range");
        assertEquals(40, arena.patternQuads());
        assertEquals(1.0f, arena.sections().getFloat(slot.index() * 96 + 20), "aabb min y");
        assertEquals(40.0f, arena.sections().getFloat(slot.index() * 96 + 32), "aabb max x");
    }

    @Test
    void keepsSharedRangesContiguousWhenOneLayerChanges() {
        final SectionStore.HeapArena arena = new SectionStore.HeapArena(4096, 4, 64);
        final SectionStore store = new SectionStore(arena);
        final SectionSlot slot = store.allocate(0, 0, 0, null);
        final PackedLayer mipped = layer(2, TerrainLayer.CUTOUT_MIPPED);
        store.applyLayer(slot, TerrainLayer.CUTOUT_MIPPED, new PackedLayer(mipped.data(), mipped.quads(), mipped.bounds(),
                mipped.meshlets(), mipped.meshletCount(), 1));
        store.applyLayer(slot, TerrainLayer.CUTOUT, layer(3, TerrainLayer.CUTOUT));
        assertEquals(5, meta(arena, slot, 60));
        assertEquals(1, meta(arena, slot, 28), "the cutout range starts with one quad that needs no alpha test");
        final int base = meta(arena, slot, 56) * 24;
        assertEquals(TerrainLayer.CUTOUT_MIPPED.mode, arena.vertices().get(base + 23));
        assertEquals(TerrainLayer.CUTOUT.mode, arena.vertices().get(base + 2 * TerrainVertexPacker.QUAD_BYTES + 23));
        store.applyLayer(slot, TerrainLayer.CUTOUT_MIPPED, layer(1, TerrainLayer.CUTOUT_MIPPED));
        assertEquals(4, meta(arena, slot, 60));
        assertEquals(0, meta(arena, slot, 28));
        assertEquals(4 * 4, store.usedVertices(), "the old range was freed");
        store.clearLayer(slot, TerrainLayer.CUTOUT_MIPPED);
        store.clearLayer(slot, TerrainLayer.CUTOUT);
        assertEquals(0, meta(arena, slot, 12), "an empty section is inactive");
        assertEquals(0, store.usedVertices());
        assertEquals(0, store.usedMeshlets());
    }

    @Test
    void growsBuffersAndReusesSlots() {
        final SectionStore.HeapArena arena = new SectionStore.HeapArena(16, 1, 1);
        final SectionStore store = new SectionStore(arena);
        final SectionSlot a = store.allocate(0, 0, 0, null);
        final SectionSlot b = store.allocate(16, 0, 0, null);
        assertEquals(1, b.index());
        assertTrue(store.applyLayer(a, TerrainLayer.SOLID, layer(64, TerrainLayer.SOLID)));
        assertTrue(store.vertexCapacity() >= 256);
        assertEquals(2, store.sectionHighWater());
        store.release(a);
        assertEquals(2, store.sectionHighWater());
        final SectionSlot c = store.allocate(32, 0, 0, null);
        assertEquals(0, c.index(), "freed slots are reused");
        store.release(b);
        store.release(c);
        assertEquals(0, store.sectionHighWater());
        assertEquals(0, store.residentSections());
    }
}

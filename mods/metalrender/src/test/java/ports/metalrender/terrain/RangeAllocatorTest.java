/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RangeAllocatorTest {

    @Test
    void allocatesFirstFitAndCoalescesOnFree() {
        final RangeAllocator a = new RangeAllocator(100);
        final int x = a.allocate(10);
        final int y = a.allocate(20);
        final int z = a.allocate(30);
        assertEquals(0, x);
        assertEquals(10, y);
        assertEquals(30, z);
        assertEquals(60, a.highWater());
        a.free(y, 20);
        assertEquals(10, a.allocate(15), "first fit reuses the hole");
        a.free(10, 15);
        a.free(x, 10);
        a.free(z, 30);
        assertEquals(1, a.freeBlocks(), "everything coalesces back into one block");
        assertEquals(0, a.used());
        assertEquals(0, a.highWater());
    }

    @Test
    void reportsFullAndGrowsTheTail() {
        final RangeAllocator a = new RangeAllocator(16);
        assertEquals(0, a.allocate(12));
        assertEquals(-1, a.allocate(8));
        a.grow(32);
        assertEquals(12, a.allocate(8), "growth merges with the free tail");
        assertEquals(20, a.used());
        assertEquals(32, a.capacity());
    }

    @Test
    void rejectsDoubleFree() {
        final RangeAllocator a = new RangeAllocator(16);
        final int x = a.allocate(4);
        a.free(x, 4);
        assertThrows(IllegalStateException.class, () -> a.free(x, 4));
    }
}

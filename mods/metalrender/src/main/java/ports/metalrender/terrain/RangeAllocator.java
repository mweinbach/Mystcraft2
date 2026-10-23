/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.terrain;

import java.util.Map;
import java.util.TreeMap;

/**
 * First-fit allocator over an integer address space (vertices, meshlets). Free blocks are kept
 * coalesced, so freeing everything always returns a single block covering the whole capacity.
 */
public final class RangeAllocator {

    private final TreeMap<Integer, Integer> free = new TreeMap<>();
    private int capacity;
    private int used;

    public RangeAllocator(final int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity " + capacity);
        }
        this.capacity = capacity;
        if (capacity > 0) {
            free.put(0, capacity);
        }
    }

    /** Returns the start of a block of {@code size} units, or -1 when no free block is large enough. */
    public int allocate(final int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("size " + size);
        }
        for (final Map.Entry<Integer, Integer> block : free.entrySet()) {
            final int start = block.getKey();
            final int length = block.getValue();
            if (length >= size) {
                free.remove(start);
                if (length > size) {
                    free.put(start + size, length - size);
                }
                used += size;
                return start;
            }
        }
        return -1;
    }

    public void free(final int start, final int size) {
        if (size <= 0) {
            return;
        }
        if (start < 0 || start + size > capacity) {
            throw new IllegalArgumentException("block " + start + "+" + size + " outside capacity " + capacity);
        }
        int newStart = start;
        int newSize = size;
        final Map.Entry<Integer, Integer> before = free.floorEntry(start);
        if (before != null) {
            final int beforeEnd = before.getKey() + before.getValue();
            if (beforeEnd > start) {
                throw new IllegalStateException("double free at " + start);
            }
            if (beforeEnd == start) {
                newStart = before.getKey();
                newSize += before.getValue();
                free.remove(before.getKey());
            }
        }
        final Map.Entry<Integer, Integer> after = free.ceilingEntry(start + 1);
        if (after != null) {
            if (after.getKey() < start + size) {
                throw new IllegalStateException("double free at " + start);
            }
            if (after.getKey() == start + size) {
                newSize += after.getValue();
                free.remove(after.getKey());
            }
        }
        free.put(newStart, newSize);
        used -= size;
    }

    /** Extends the address space; the new tail becomes free. */
    public void grow(final int newCapacity) {
        if (newCapacity <= capacity) {
            return;
        }
        final int oldCapacity = capacity;
        capacity = newCapacity;
        used += newCapacity - oldCapacity; // free() subtracts it again
        free(oldCapacity, newCapacity - oldCapacity);
    }

    public int capacity() {
        return capacity;
    }

    public int used() {
        return used;
    }

    public int freeBlocks() {
        return free.size();
    }

    /** One past the highest allocated unit; everything above it is free. */
    public int highWater() {
        final Map.Entry<Integer, Integer> last = free.lastEntry();
        if (last != null && last.getKey() + last.getValue() == capacity) {
            return last.getKey();
        }
        return capacity;
    }
}

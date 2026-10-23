/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender.terrain;

import static ports.metalrender.terrain.TerrainVertexPacker.QUAD_BYTES;
import static ports.metalrender.terrain.TerrainVertexPacker.VERTEX_BYTES;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Owns the GPU layout of every resident chunk section: vertex ranges in the shared arena, the
 * 96-byte {@code SectionGpuMeta} records and the 32-byte {@code MeshletGpuMeta} records. Not thread
 * safe; the game calls it only on the render thread, between (synchronous) Metal passes.
 */
public final class SectionStore {

    public static final int SECTION_BYTES = 96;
    public static final int MESHLET_BYTES = TerrainVertexPacker.MESHLET_BYTES;
    public static final int MESHLET_QUADS = TerrainVertexPacker.MESHLET_QUADS;
    static final int FLAG_ACTIVE = 1;
    private static final int MAX_VERTICES = (Integer.MAX_VALUE / VERTEX_BYTES) & ~3;
    private static final ByteBuffer EMPTY = ByteBuffer.allocate(0);

    private final GpuArena arena;
    private final RangeAllocator vertices;
    private final RangeAllocator meshlets;
    private final BitSet usedSlots = new BitSet();
    private final List<SectionSlot> slots = new ArrayList<>();
    private int sectionCapacity;
    private int patternQuads;

    public SectionStore(final GpuArena arena) {
        this.arena = arena;
        this.vertices = new RangeAllocator(arena.vertices().capacity() / VERTEX_BYTES);
        this.meshlets = new RangeAllocator(arena.meshlets().capacity() / MESHLET_BYTES);
        this.sectionCapacity = arena.sections().capacity() / SECTION_BYTES;
    }

    /** Claims a slot for a section at the given block origin, or returns null if buffers cannot grow. */
    @Nullable
    public SectionSlot allocate(final int originX, final int originY, final int originZ, @Nullable final Object owner) {
        final int index = usedSlots.nextClearBit(0);
        if (index >= sectionCapacity) {
            final int wanted = Math.max(sectionCapacity * 2, index + 1);
            if (!arena.growSections(wanted)) {
                return null;
            }
            sectionCapacity = wanted;
        }
        usedSlots.set(index);
        final SectionSlot slot = new SectionSlot(index, originX, originY, originZ, owner);
        while (slots.size() <= index) {
            slots.add(null);
        }
        slots.set(index, slot);
        writeMeta(slot);
        return slot;
    }

    /**
     * Replaces one layer of a section. Layers that share a GPU range with it (cutout mipped and
     * cutout) are carried over into the new range. Returns false if the arena could not grow.
     */
    public boolean applyLayer(final SectionSlot slot, final TerrainLayer layer, final PackedLayer packed) {
        if (slot.released) {
            return false;
        }
        final TerrainLayer.Group group = layer.group;
        final int g = group.ordinal();
        int total = 0;
        for (final TerrainLayer member : group.layers()) {
            total += member == layer ? packed.quads() : slot.quads[member.ordinal()];
        }
        int newStart = -1;
        if (total > 0) {
            newStart = allocateVertices(total * 4);
            if (newStart < 0) {
                return false;
            }
        }
        final ByteBuffer arenaVertices = arena.vertices();
        int dst = newStart * VERTEX_BYTES;
        int oldCursor = slot.groupStart[g] * VERTEX_BYTES;
        for (final TerrainLayer member : group.layers()) {
            final int oldQuads = slot.quads[member.ordinal()];
            if (member == layer) {
                final int bytes = packed.quads() * QUAD_BYTES;
                if (bytes > 0) {
                    arenaVertices.put(dst, packed.data(), 0, bytes);
                }
                dst += bytes;
            } else if (oldQuads > 0) {
                arenaVertices.put(dst, arenaVertices, oldCursor, oldQuads * QUAD_BYTES);
                dst += oldQuads * QUAD_BYTES;
            }
            oldCursor += oldQuads * QUAD_BYTES;
        }
        if (slot.groupStart[g] >= 0) {
            vertices.free(slot.groupStart[g], slot.groupQuads[g] * 4);
        }
        slot.groupStart[g] = newStart;
        slot.groupQuads[g] = total;
        slot.quads[layer.ordinal()] = packed.quads();
        slot.bounds[layer.ordinal()] = packed.quads() > 0 ? packed.bounds() : null;
        slot.meshletTemplates[layer.ordinal()] = packed.quads() > 0 ? packed.meshlets() : null;
        slot.meshletTemplateCounts[layer.ordinal()] = packed.quads() > 0 ? packed.meshletCount() : 0;
        if (total > patternQuads) {
            arena.ensurePatternQuads(total);
            patternQuads = total;
        }
        rebuildMeshlets(slot);
        writeMeta(slot);
        return true;
    }

    public void clearLayer(final SectionSlot slot, final TerrainLayer layer) {
        if (!slot.released && slot.quads[layer.ordinal()] > 0) {
            applyLayer(slot, layer, new PackedLayer(EMPTY, 0, null, EMPTY, 0));
        }
    }

    public void release(final SectionSlot slot) {
        if (slot.released) {
            return;
        }
        for (int g = 0; g < slot.groupStart.length; g++) {
            if (slot.groupStart[g] >= 0) {
                vertices.free(slot.groupStart[g], slot.groupQuads[g] * 4);
            }
        }
        freeMeshlets(slot);
        final ByteBuffer meta = arena.sections();
        for (int i = 0; i < SECTION_BYTES; i += 8) {
            meta.putLong(slot.index * SECTION_BYTES + i, 0L);
        }
        usedSlots.clear(slot.index);
        slots.set(slot.index, null);
        slot.released = true;
    }

    public void releaseAll() {
        for (final SectionSlot slot : new ArrayList<>(slots)) {
            if (slot != null) {
                release(slot);
            }
        }
    }

    /** Number of section records the GPU must scan (one past the highest used slot). */
    public int sectionHighWater() {
        return usedSlots.length();
    }

    public int meshletHighWater() {
        return meshlets.highWater();
    }

    public int residentSections() {
        return usedSlots.cardinality();
    }

    public int usedVertices() {
        return vertices.used();
    }

    public int vertexCapacity() {
        return vertices.capacity();
    }

    public int usedMeshlets() {
        return meshlets.used();
    }

    /** Copies the packer's meshlet templates for every layer, patched with this slot's ranges. */
    private void rebuildMeshlets(final SectionSlot slot) {
        freeMeshlets(slot);
        int count = 0;
        for (final int c : slot.meshletTemplateCounts) {
            count += c;
        }
        if (count == 0) {
            return;
        }
        final int start = allocateMeshlets(count);
        if (start < 0) {
            return; // the mesh-shader path will skip this section; the other paths still draw it
        }
        slot.meshletStart = start;
        slot.meshletCount = count;
        final ByteBuffer out = arena.meshlets();
        int meshlet = start;
        for (final TerrainLayer.Group group : TerrainLayer.Group.values()) {
            int base = slot.groupStart[group.ordinal()];
            for (final TerrainLayer layer : group.layers()) {
                final int l = layer.ordinal();
                final ByteBuffer templates = slot.meshletTemplates[l];
                for (int i = 0; i < slot.meshletTemplateCounts[l]; i++) {
                    final int b = meshlet * MESHLET_BYTES;
                    out.put(b, templates, i * MESHLET_BYTES, MESHLET_BYTES);
                    out.putInt(b + 16, slot.index);
                    out.putInt(b + 20, base + out.getInt(b + 20));
                    meshlet++;
                }
                base += slot.quads[l] * 4;
            }
        }
    }

    private void freeMeshlets(final SectionSlot slot) {
        if (slot.meshletStart < 0) {
            return;
        }
        final ByteBuffer out = arena.meshlets();
        for (int i = 0; i < slot.meshletCount; i++) {
            final int b = (slot.meshletStart + i) * MESHLET_BYTES;
            for (int k = 0; k < MESHLET_BYTES; k += 8) {
                out.putLong(b + k, 0L); // quadCount 0: the object shader skips the record
            }
        }
        meshlets.free(slot.meshletStart, slot.meshletCount);
        slot.meshletStart = -1;
        slot.meshletCount = 0;
    }

    private void writeMeta(final SectionSlot slot) {
        final ByteBuffer meta = arena.sections();
        final int b = slot.index * SECTION_BYTES;
        final float[] box = sectionBounds(slot);
        final int solid = TerrainLayer.Group.SOLID.ordinal();
        final int cutout = TerrainLayer.Group.CUTOUT.ordinal();
        final int flags = slot.totalQuads() > 0 ? FLAG_ACTIVE : 0;
        meta.putInt(b, slot.originX);
        meta.putInt(b + 4, slot.originY);
        meta.putInt(b + 8, slot.originZ);
        meta.putInt(b + 12, flags);
        meta.putFloat(b + 16, box[0]);
        meta.putFloat(b + 20, box[1]);
        meta.putFloat(b + 24, box[2]);
        meta.putFloat(b + 28, 0.0f);
        meta.putFloat(b + 32, box[3]);
        meta.putFloat(b + 36, box[4]);
        meta.putFloat(b + 40, box[5]);
        meta.putFloat(b + 44, 0.0f);
        meta.putInt(b + 48, Math.max(0, slot.groupStart[solid]));
        meta.putInt(b + 52, slot.groupQuads[solid]);
        meta.putInt(b + 56, Math.max(0, slot.groupStart[cutout]));
        meta.putInt(b + 60, slot.groupQuads[cutout]);
        meta.putLong(b + 64, 0L); // LOD ranges unused
        meta.putLong(b + 72, 0L);
        meta.putLong(b + 80, 0L); // translucent range unused: vanilla draws translucent terrain
        meta.putInt(b + 88, Math.max(0, slot.meshletStart));
        meta.putInt(b + 92, slot.meshletCount);
    }

    private static float[] sectionBounds(final SectionSlot slot) {
        final float[] box = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE,
                -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        boolean any = false;
        for (int l = 0; l < slot.bounds.length; l++) {
            final float[] b = slot.bounds[l];
            if (b == null || slot.quads[l] == 0) {
                continue;
            }
            any = true;
            for (int i = 0; i < 3; i++) {
                box[i] = Math.min(box[i], b[i]);
                box[i + 3] = Math.max(box[i + 3], b[i + 3]);
            }
        }
        if (!any) {
            return new float[6];
        }
        return box;
    }

    private int allocateVertices(final int count) {
        int at = vertices.allocate(count);
        if (at >= 0) {
            return at;
        }
        final long wanted = Math.max((long) vertices.capacity() * 2, (long) vertices.capacity() + count);
        final int capacity = (int) Math.min(wanted, MAX_VERTICES);
        if (capacity > vertices.capacity() && arena.growVertices(capacity)) {
            vertices.grow(capacity);
            at = vertices.allocate(count);
        }
        return at;
    }

    private int allocateMeshlets(final int count) {
        int at = meshlets.allocate(count);
        if (at >= 0) {
            return at;
        }
        final int capacity = Math.max(meshlets.capacity() * 2, meshlets.capacity() + count);
        if (arena.growMeshlets(capacity)) {
            meshlets.grow(capacity);
            at = meshlets.allocate(count);
        }
        return at;
    }

    /** Heap-backed arena for tests and for running the bookkeeping without a GPU. */
    public static final class HeapArena implements GpuArena {
        private ByteBuffer vertices;
        private ByteBuffer sections;
        private ByteBuffer meshlets;
        private int patternQuads;

        public HeapArena(final int vertexCapacity, final int sectionCapacity, final int meshletCapacity) {
            vertices = allocate(vertexCapacity * VERTEX_BYTES);
            sections = allocate(sectionCapacity * SECTION_BYTES);
            meshlets = allocate(meshletCapacity * MESHLET_BYTES);
        }

        private static ByteBuffer allocate(final int bytes) {
            return ByteBuffer.allocate(bytes).order(ByteOrder.LITTLE_ENDIAN);
        }

        private static ByteBuffer grow(final ByteBuffer old, final int bytes) {
            final ByteBuffer next = allocate(bytes);
            next.put(0, old, 0, old.capacity());
            return next;
        }

        @Override
        public ByteBuffer vertices() {
            return vertices;
        }

        @Override
        public boolean growVertices(final int vertexCapacity) {
            vertices = grow(vertices, vertexCapacity * VERTEX_BYTES);
            return true;
        }

        @Override
        public ByteBuffer sections() {
            return sections;
        }

        @Override
        public boolean growSections(final int sectionCapacity) {
            sections = grow(sections, sectionCapacity * SECTION_BYTES);
            return true;
        }

        @Override
        public ByteBuffer meshlets() {
            return meshlets;
        }

        @Override
        public boolean growMeshlets(final int meshletCapacity) {
            meshlets = grow(meshlets, meshletCapacity * MESHLET_BYTES);
            return true;
        }

        @Override
        public void ensurePatternQuads(final int quads) {
            patternQuads = Math.max(patternQuads, quads);
        }

        public int patternQuads() {
            return patternQuads;
        }
    }
}

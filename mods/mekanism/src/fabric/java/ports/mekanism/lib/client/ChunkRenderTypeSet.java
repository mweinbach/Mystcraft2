/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.renderer.RenderType;

/**
 * A set of the chunk render types ({@link RenderType#chunkBufferLayers()}).
 */
public final class ChunkRenderTypeSet implements Iterable<RenderType> {

    private static final List<RenderType> CHUNK_RENDER_TYPES = RenderType.chunkBufferLayers();
    private static final ChunkRenderTypeSet NONE = new ChunkRenderTypeSet(new BitSet());
    private static final ChunkRenderTypeSet ALL;

    static {
        BitSet bits = new BitSet();
        bits.set(0, CHUNK_RENDER_TYPES.size());
        ALL = new ChunkRenderTypeSet(bits);
    }

    private final BitSet bits;

    private ChunkRenderTypeSet(BitSet bits) {
        this.bits = bits;
    }

    public static ChunkRenderTypeSet none() {
        return NONE;
    }

    public static ChunkRenderTypeSet all() {
        return ALL;
    }

    public static ChunkRenderTypeSet of(RenderType... renderTypes) {
        return of(Arrays.asList(renderTypes));
    }

    public static ChunkRenderTypeSet of(Collection<RenderType> renderTypes) {
        return renderTypes.isEmpty() ? none() : of((Iterable<RenderType>) renderTypes);
    }

    private static ChunkRenderTypeSet of(Iterable<RenderType> renderTypes) {
        BitSet bits = new BitSet();
        for (RenderType renderType : renderTypes) {
            int index = CHUNK_RENDER_TYPES.indexOf(renderType);
            if (index < 0) {
                throw new IllegalArgumentException("Attempted to create chunk render type set with a non-chunk render type: " + renderType);
            }
            bits.set(index);
        }
        return new ChunkRenderTypeSet(bits);
    }

    public static ChunkRenderTypeSet union(ChunkRenderTypeSet... sets) {
        return union(Arrays.asList(sets));
    }

    public static ChunkRenderTypeSet union(Collection<ChunkRenderTypeSet> sets) {
        BitSet bits = new BitSet();
        for (ChunkRenderTypeSet set : sets) {
            bits.or(set.bits);
        }
        return new ChunkRenderTypeSet(bits);
    }

    public static ChunkRenderTypeSet intersection(ChunkRenderTypeSet... sets) {
        return intersection(Arrays.asList(sets));
    }

    public static ChunkRenderTypeSet intersection(Collection<ChunkRenderTypeSet> sets) {
        if (sets.isEmpty()) {
            return all();
        }
        BitSet bits = new BitSet();
        bits.set(0, CHUNK_RENDER_TYPES.size());
        for (ChunkRenderTypeSet set : sets) {
            bits.and(set.bits);
        }
        return new ChunkRenderTypeSet(bits);
    }

    public boolean isEmpty() {
        return bits.isEmpty();
    }

    public boolean contains(RenderType renderType) {
        int index = CHUNK_RENDER_TYPES.indexOf(renderType);
        return index >= 0 && bits.get(index);
    }

    @Override
    public Iterator<RenderType> iterator() {
        return asList().iterator();
    }

    public List<RenderType> asList() {
        List<RenderType> list = new ArrayList<>(bits.cardinality());
        bits.stream().forEach(index -> list.add(CHUNK_RENDER_TYPES.get(index)));
        return Collections.unmodifiableList(list);
    }
}

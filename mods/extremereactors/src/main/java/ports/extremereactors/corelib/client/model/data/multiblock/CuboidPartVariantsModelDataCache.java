/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.model.data.multiblock;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import ports.extremereactors.corelib.block.BlockFacings;
import ports.extremereactors.corelib.client.model.data.ModelData;
import ports.extremereactors.corelib.client.model.data.NamedModelProperty;

import java.util.Objects;

/**
 * Shares one immutable {@link ModelData} per (part, model variant, outward facings) combination of a multiblock
 * casing-like part, so parts that look the same reuse the same instance.
 */
public class CuboidPartVariantsModelDataCache {

    /** The index of the part type (usually the ordinal of the part-type enum). */
    public static final NamedModelProperty<Integer> BLOCK_ID = new NamedModelProperty<>("BLOCK_ID", Objects::nonNull);
    /** The index of the model variant to use for the part. */
    public static final NamedModelProperty<Integer> VARIANT_INDEX = new NamedModelProperty<>("VARIANT_INDEX", Objects::nonNull);
    /** The faces of the part that look out of the machine. */
    public static final NamedModelProperty<BlockFacings> OUTWARD_FACINGS = new NamedModelProperty<>("OUTWARD_FACINGS", Objects::nonNull);

    public CuboidPartVariantsModelDataCache() {
        this._cache = new Int2ObjectOpenHashMap<>(64);
    }

    public ModelData computeIfAbsent(final int blockId, final int variantIndex, final BlockFacings outwardFacings) {

        final int key = ((blockId & 0xFFF) << 14) | ((variantIndex & 0xFF) << 6) | (outwardFacings.value() & 0x3F);

        synchronized (this._cache) {
            return this._cache.computeIfAbsent(key, k -> ModelData.builder()
                    .with(BLOCK_ID, blockId)
                    .with(VARIANT_INDEX, variantIndex)
                    .with(OUTWARD_FACINGS, outwardFacings)
                    .build());
        }
    }

    public static int getBlockId(final ModelData data) {

        final Integer value = data.get(BLOCK_ID);

        return null != value ? value : 0;
    }

    public static int getVariantIndex(final ModelData data) {

        final Integer value = data.get(VARIANT_INDEX);

        return null != value ? value : 0;
    }

    public static BlockFacings getOutwardFacings(final ModelData data) {

        final BlockFacings value = data.get(OUTWARD_FACINGS);

        return null != value ? value : BlockFacings.NONE;
    }

    private final Int2ObjectMap<ModelData> _cache;
}

/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.block.multiblock;

import net.minecraft.world.level.block.state.BlockState;
import ports.extremereactors.multiblock.variant.IMultiblockVariant;

import java.util.Optional;

/**
 * A block entity that knows its machine variant from the {@link MultiblockPartBlock} it belongs to.
 */
public interface IMultiblockVariantProvider<Variant extends IMultiblockVariant> {

    BlockState getBlockState();

    @SuppressWarnings("unchecked")
    default Optional<Variant> getMultiblockVariant() {
        return this.getBlockState().getBlock() instanceof MultiblockPartBlock<?, ?> block ?
                block.getMultiblockVariant().map(variant -> (Variant) variant) : Optional.empty();
    }
}

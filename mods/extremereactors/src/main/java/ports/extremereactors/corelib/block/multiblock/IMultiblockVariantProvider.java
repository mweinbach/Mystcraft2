/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.block.multiblock;

import net.minecraft.world.level.block.entity.BlockEntity;
import ports.extremereactors.multiblock.variant.IMultiblockVariant;

import java.util.Optional;

/**
 * A block entity that knows its machine variant from the {@link MultiblockPartBlock} it belongs to.
 */
public interface IMultiblockVariantProvider<Variant extends IMultiblockVariant> {

    /**
     * The state comes from {@link BlockEntity#getBlockState()} rather than an abstract {@code getBlockState()} here: on
     * Fabric, Loom renames the vanilla method in production but not one declared by this interface, so implementations
     * inherited from {@code BlockEntity} do not satisfy it and the call throws {@code AbstractMethodError} (Sodium's
     * chunk rebuild in Tekxit 4 reached it through the model data).
     */
    @SuppressWarnings("unchecked")
    default Optional<Variant> getMultiblockVariant() {
        return this instanceof BlockEntity entity && entity.getBlockState().getBlock() instanceof MultiblockPartBlock<?, ?> block ?
                block.getMultiblockVariant().map(variant -> (Variant) variant) : Optional.empty();
    }
}

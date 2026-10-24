/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.block.multiblock;

import net.minecraft.world.level.block.entity.BlockEntity;
import ports.extremereactors.multiblock.IMultiblockController;

import java.util.Optional;

/**
 * A block entity that knows its part type from the {@link MultiblockPartBlock} it belongs to.
 */
public interface IMultiblockPartTypeProvider<Controller extends IMultiblockController<Controller>,
                                             PartType extends Enum<PartType> & IMultiblockPartType> {

    /**
     * Reads the state through {@link BlockEntity#getBlockState()} instead of declaring an abstract
     * {@code getBlockState()}: Fabric Loom remaps the vanilla method in production jars but not a method declared here,
     * so the inherited implementation would not satisfy the interface and calls would throw {@code AbstractMethodError}.
     * See {@link IMultiblockVariantProvider}.
     */
    @SuppressWarnings("unchecked")
    default Optional<PartType> getPartType() {
        return this instanceof BlockEntity entity && entity.getBlockState().getBlock() instanceof MultiblockPartBlock<?, ?> block ?
                Optional.of((PartType) block.getPartType()) : Optional.empty();
    }

    default boolean isTypeOfPart(final PartType type) {
        return this.getPartType().filter(type::equals).isPresent();
    }

    default PartType getPartTypeOrDefault(final PartType defaultType) {
        return this.getPartType().orElse(defaultType);
    }
}

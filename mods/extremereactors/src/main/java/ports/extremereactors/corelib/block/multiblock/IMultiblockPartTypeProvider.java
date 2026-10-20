/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.block.multiblock;

import net.minecraft.world.level.block.state.BlockState;
import ports.extremereactors.multiblock.IMultiblockController;

import java.util.Optional;

/**
 * A block entity that knows its part type from the {@link MultiblockPartBlock} it belongs to.
 */
public interface IMultiblockPartTypeProvider<Controller extends IMultiblockController<Controller>,
                                             PartType extends Enum<PartType> & IMultiblockPartType> {

    BlockState getBlockState();

    @SuppressWarnings("unchecked")
    default Optional<PartType> getPartType() {
        return this.getBlockState().getBlock() instanceof MultiblockPartBlock<?, ?> block ?
                Optional.of((PartType) block.getPartType()) : Optional.empty();
    }

    default PartType getPartTypeOrDefault(final PartType defaultType) {
        return this.getPartType().orElse(defaultType);
    }
}

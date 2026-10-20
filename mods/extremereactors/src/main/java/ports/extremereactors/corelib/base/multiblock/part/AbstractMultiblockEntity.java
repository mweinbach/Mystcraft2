/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.base.multiblock.part;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import ports.extremereactors.multiblock.cuboid.AbstractCuboidMultiblockController;
import ports.extremereactors.multiblock.cuboid.AbstractCuboidMultiblockPart;

/**
 * Base block entity of the parts of a cuboid machine.
 */
public abstract class AbstractMultiblockEntity<Controller extends AbstractCuboidMultiblockController<Controller>>
        extends AbstractCuboidMultiblockPart<Controller> {

    protected AbstractMultiblockEntity(final BlockEntityType<?> type, final BlockPos position, final BlockState blockState) {
        super(type, position, blockState);
    }
}

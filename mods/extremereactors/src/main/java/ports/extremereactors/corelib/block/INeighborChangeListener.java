/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block entity that wants to know when an adjacent block, or an adjacent block entity, changes.
 */
public interface INeighborChangeListener {

    /**
     * An adjacent block changed (it was placed, removed or changed state).
     *
     * @param state the state of the listener block
     */
    void onNeighborBlockChanged(BlockState state, BlockPos neighborPosition, boolean isMoving);

    /**
     * The block entity of an adjacent block changed without the block itself changing.
     *
     * @param state the state of the listener block
     */
    void onNeighborTileChanged(BlockState state, BlockPos neighborPosition);

    /**
     * Implemented by blocks whose block entities tell the adjacent listeners when they appear, disappear or change.
     */
    interface Notifier {
    }
}

/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.base.multiblock.part.io;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import ports.extremereactors.corelib.capability.Capability;
import ports.extremereactors.corelib.capability.LazyOptional;

import javax.annotation.Nullable;

/**
 * The logic of a port that exchanges something with the block in front of it.
 *
 * <p>An active handler pushes into, or pulls from, its neighbour on its own; a passive one only exposes a handler
 * that the neighbour can use.</p>
 */
public interface IIOPortHandler {

    boolean isActive();

    default boolean isPassive() {
        return !this.isActive();
    }

    /**
     * @return true if the last call to {@link #checkConnections} found something to exchange with
     */
    boolean isConnected();

    /**
     * Look again at the block in front of the port.
     */
    void checkConnections(@Nullable Level world, BlockPos position);

    /**
     * Forget the cached neighbour and invalidate the exposed handlers, if any.
     */
    void invalidate();

    /**
     * @return the handler exposed by the port for the given capability, or null if the port does not handle it
     */
    @Nullable
    <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction direction);
}

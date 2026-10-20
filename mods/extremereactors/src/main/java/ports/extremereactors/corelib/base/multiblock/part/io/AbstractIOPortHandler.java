/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.base.multiblock.part.io;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import ports.extremereactors.corelib.base.multiblock.part.AbstractMultiblockEntity;
import ports.extremereactors.corelib.capability.Capability;
import ports.extremereactors.corelib.capability.CapabilityHelper;
import ports.extremereactors.corelib.capability.LazyOptional;
import ports.extremereactors.corelib.data.IoMode;
import ports.extremereactors.multiblock.cuboid.AbstractCuboidMultiblockController;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Shared plumbing of the port handlers: the owning part, the mode and the lookup of the neighbour in front of
 * the port.
 */
public abstract class AbstractIOPortHandler<Controller extends AbstractCuboidMultiblockController<Controller>,
                                            Part extends AbstractMultiblockEntity<Controller>>
        implements IIOPortHandler {

    protected AbstractIOPortHandler(final Part part, final IoMode mode) {

        this._part = part;
        this._mode = mode;
    }

    public Part getPart() {
        return this._part;
    }

    public IoMode getMode() {
        return this._mode;
    }

    /**
     * @return the side of the machine the port is facing, if the machine is assembled
     */
    protected Optional<Direction> getOutwardDirection() {
        return this._part.isMachineAssembled() ? this._part.getOutwardDirection() : Optional.empty();
    }

    /**
     * @return true if the given side is one the port exchanges through
     */
    protected boolean isExposedOn(@Nullable final Direction direction) {
        return null == direction || this.getOutwardDirection().filter(direction::equals).isPresent();
    }

    /**
     * @return the handler exposed by the neighbour in front of the port, as seen from the port
     */
    protected <T> LazyOptional<T> lookupNeighbor(@Nullable final Level world, final BlockPos position,
                                                 final Capability<T> capability) {

        if (null == world) {
            return LazyOptional.empty();
        }

        return this.getOutwardDirection()
                .map(direction -> CapabilityHelper.getCapability(world, position.relative(direction), capability,
                        direction.getOpposite()))
                .orElseGet(LazyOptional::empty);
    }

    //region IIOPortHandler

    @Override
    public boolean isActive() {
        return this._mode.isActive();
    }

    //endregion
    //region internals

    private final Part _part;
    private final IoMode _mode;

    //endregion
}

/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.base.multiblock.part.io.fluid;

import ports.extremereactors.corelib.base.multiblock.part.AbstractMultiblockEntity;
import ports.extremereactors.corelib.base.multiblock.part.io.AbstractIOPortHandler;
import ports.extremereactors.corelib.data.IoMode;
import ports.extremereactors.multiblock.cuboid.AbstractCuboidMultiblockController;

/**
 * Base class of the fluid port handlers bound to a machine part.
 */
public abstract class AbstractFluidPortHandler<Controller extends AbstractCuboidMultiblockController<Controller>,
                                               T extends AbstractMultiblockEntity<Controller> & IFluidPort>
        extends AbstractIOPortHandler<Controller, T>
        implements IFluidPortHandler {

    protected AbstractFluidPortHandler(final T part, final IoMode mode) {
        super(part, mode);
    }
}

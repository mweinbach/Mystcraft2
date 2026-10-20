/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.base.multiblock.part.io.fluid;

import ports.extremereactors.corelib.data.IIoEntity;

/**
 * A machine part that moves fluids in or out of its machine.
 */
public interface IFluidPort extends IIoEntity {

    IFluidPortHandler getFluidPortHandler();
}

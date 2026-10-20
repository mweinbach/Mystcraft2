/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.base.multiblock.part.io.fluid;

import ports.extremereactors.corelib.base.multiblock.part.io.IIOPortHandler;
import ports.extremereactors.corelib.data.IoDirection;
import ports.extremereactors.corelib.fluid.FluidStack;
import ports.extremereactors.corelib.fluid.handler.IFluidHandler;

import java.util.function.Function;

/**
 * The fluid side of a fluid port.
 */
public interface IFluidPortHandler extends IIOPortHandler {

    /**
     * Push fluid into the neighbour of an active port.
     *
     * @return the amount accepted by the neighbour
     */
    int outputFluid(FluidStack stack);

    /**
     * Pull fluid from the neighbour of an active port.
     *
     * @return the amount moved into the destination
     */
    int inputFluid(IFluidHandler destination, int maxAmount);

    /**
     * Pick again the machine handler a passive port exposes, now that the port direction or its machine changed.
     *
     * @param handlerProvider gives the machine handler for a port direction
     */
    void update(Function<IoDirection, IFluidHandler> handlerProvider);
}

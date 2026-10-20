/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.fluid.handler;

import ports.extremereactors.corelib.fluid.FluidStack;

/** Holds nothing and accepts nothing. */
public class EmptyFluidHandler implements IFluidHandler {

    public static final EmptyFluidHandler INSTANCE = new EmptyFluidHandler();

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(final int tank) {
        return FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(final int tank) {
        return 0;
    }

    @Override
    public boolean isFluidValid(final int tank, final FluidStack stack) {
        return false;
    }

    @Override
    public int fill(final FluidStack resource, final FluidAction action) {
        return 0;
    }

    @Override
    public FluidStack drain(final FluidStack resource, final FluidAction action) {
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(final int maxDrain, final FluidAction action) {
        return FluidStack.EMPTY;
    }
}

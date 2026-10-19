/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.fluid.handler;

import java.util.Objects;
import ports.extremereactors.corelib.fluid.FluidStack;

/** Forwards every call to a handler that can be swapped at any time, so a capability can stay valid. */
public class FluidHandlerForwarder implements IFluidHandler {

    public FluidHandlerForwarder(final IFluidHandler handler) {
        this._handler = Objects.requireNonNull(handler);
    }

    public IFluidHandler getHandler() {
        return this._handler;
    }

    public void setHandler(final IFluidHandler handler) {
        this._handler = Objects.requireNonNull(handler);
    }

    @Override
    public int getTanks() {
        return this.getHandler().getTanks();
    }

    @Override
    public FluidStack getFluidInTank(final int tank) {
        return this.getHandler().getFluidInTank(tank);
    }

    @Override
    public int getTankCapacity(final int tank) {
        return this.getHandler().getTankCapacity(tank);
    }

    @Override
    public boolean isFluidValid(final int tank, final FluidStack stack) {
        return this.getHandler().isFluidValid(tank, stack);
    }

    @Override
    public int fill(final FluidStack resource, final FluidAction action) {
        return this.getHandler().fill(resource, action);
    }

    @Override
    public FluidStack drain(final FluidStack resource, final FluidAction action) {
        return this.getHandler().drain(resource, action);
    }

    @Override
    public FluidStack drain(final int maxDrain, final FluidAction action) {
        return this.getHandler().drain(maxDrain, action);
    }

    private IFluidHandler _handler;
}

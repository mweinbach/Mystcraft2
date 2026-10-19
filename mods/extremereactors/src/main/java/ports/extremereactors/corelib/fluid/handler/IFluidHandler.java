/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.fluid.handler;

import ports.extremereactors.corelib.fluid.FluidStack;

/** A set of fluid tanks that can be filled and drained. Amounts are in millibuckets. */
public interface IFluidHandler {

    enum FluidAction {

        EXECUTE,
        SIMULATE;

        public boolean execute() {
            return this == EXECUTE;
        }

        public boolean simulate() {
            return this == SIMULATE;
        }
    }

    int getTanks();

    /** The tank contents; callers must not modify the returned stack. */
    FluidStack getFluidInTank(int tank);

    int getTankCapacity(int tank);

    boolean isFluidValid(int tank, FluidStack stack);

    /** @return the amount accepted */
    int fill(FluidStack resource, FluidAction action);

    /** Drains up to {@code resource.getAmount()} of that fluid. */
    FluidStack drain(FluidStack resource, FluidAction action);

    /** Drains up to {@code maxDrain} of whatever fluid comes first. */
    FluidStack drain(int maxDrain, FluidAction action);
}

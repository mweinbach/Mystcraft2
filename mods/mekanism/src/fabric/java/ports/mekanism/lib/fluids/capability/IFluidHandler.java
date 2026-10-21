/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.fluids.capability;

import org.jetbrains.annotations.NotNull;
import ports.mekanism.lib.fluids.FluidStack;

/**
 * A fluid inventory measured in millibuckets. On Fabric 1 mB is 81 droplets.
 */
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

    @NotNull
    FluidStack getFluidInTank(int tank);

    int getTankCapacity(int tank);

    boolean isFluidValid(int tank, @NotNull FluidStack stack);

    int fill(FluidStack resource, FluidAction action);

    @NotNull
    FluidStack drain(FluidStack resource, FluidAction action);

    @NotNull
    FluidStack drain(int maxDrain, FluidAction action);
}

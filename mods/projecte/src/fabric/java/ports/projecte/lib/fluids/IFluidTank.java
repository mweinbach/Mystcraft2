/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.fluids;

import org.jetbrains.annotations.NotNull;
import ports.projecte.lib.fluids.capability.IFluidHandler.FluidAction;

public interface IFluidTank {

    @NotNull
    FluidStack getFluid();

    int getFluidAmount();

    int getCapacity();

    boolean isFluidValid(FluidStack stack);

    int fill(FluidStack resource, FluidAction action);

    @NotNull
    FluidStack drain(int maxDrain, FluidAction action);

    @NotNull
    FluidStack drain(FluidStack resource, FluidAction action);
}

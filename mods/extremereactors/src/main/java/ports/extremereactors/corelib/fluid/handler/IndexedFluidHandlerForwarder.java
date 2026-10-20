/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.fluid.handler;

import net.minecraft.world.level.material.Fluid;
import ports.extremereactors.corelib.data.stack.AllowedHandlerAction;
import ports.extremereactors.corelib.data.stack.IndexedStackContainer;
import ports.extremereactors.corelib.data.stack.OperationMode;
import ports.extremereactors.corelib.fluid.FluidStack;

/** Exposes one index of a fluid {@link IndexedStackContainer} as a single-tank fluid handler. */
public class IndexedFluidHandlerForwarder<Index extends Enum<Index>> implements IFluidHandler {

    public IndexedFluidHandlerForwarder(final IndexedStackContainer<Index, Fluid, FluidStack> container,
                                        final Index index, final AllowedHandlerAction allowedAction) {

        this._container = container;
        this._index = index;
        this._allowedAction = allowedAction;
    }

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(final int tank) {
        return 0 == tank ? this._container.getStackCopy(this._index) : FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(final int tank) {
        return 0 == tank ? this._container.getCapacity() : 0;
    }

    @Override
    public boolean isFluidValid(final int tank, final FluidStack stack) {
        return 0 == tank && this._allowedAction.canInsert() && this._container.isStackValidForIndex(this._index, stack);
    }

    @Override
    public int fill(final FluidStack resource, final FluidAction action) {
        return this._allowedAction.canInsert() ? this._container.insert(this._index, resource, mode(action)) : 0;
    }

    @Override
    public FluidStack drain(final FluidStack resource, final FluidAction action) {
        return this._allowedAction.canExtract() && !resource.isEmpty() ?
                this._container.extract(this._index, resource.getFluid(), resource.getAmount(), mode(action)) : FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(final int maxDrain, final FluidAction action) {
        return this._allowedAction.canExtract() ? this._container.extract(this._index, maxDrain, mode(action)) : FluidStack.EMPTY;
    }

    private static OperationMode mode(final FluidAction action) {
        return action.execute() ? OperationMode.Execute : OperationMode.Simulate;
    }

    private final IndexedStackContainer<Index, Fluid, FluidStack> _container;
    private final Index _index;
    private final AllowedHandlerAction _allowedAction;
}

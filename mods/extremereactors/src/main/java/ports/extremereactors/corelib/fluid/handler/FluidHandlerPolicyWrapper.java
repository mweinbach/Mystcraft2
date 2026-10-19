/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.fluid.handler;

import java.util.function.Predicate;
import ports.extremereactors.corelib.fluid.FluidStack;

/** Restricts which operations, and which fluids, a wrapped handler accepts from outside. */
public class FluidHandlerPolicyWrapper extends FluidHandlerForwarder {

    public static IFluidHandler inputOnly(final IFluidHandler handler) {
        return new FluidHandlerPolicyWrapper(handler, true, false, stack -> true);
    }

    public static IFluidHandler inputOnly(final IFluidHandler handler, final Predicate<FluidStack> validator) {
        return new FluidHandlerPolicyWrapper(handler, true, false, validator);
    }

    public static IFluidHandler outputOnly(final IFluidHandler handler) {
        return new FluidHandlerPolicyWrapper(handler, false, true, stack -> false);
    }

    public static IFluidHandler twoWay(final IFluidHandler handler, final Predicate<FluidStack> validator) {
        return new FluidHandlerPolicyWrapper(handler, true, true, validator);
    }

    protected FluidHandlerPolicyWrapper(final IFluidHandler handler, final boolean canFill, final boolean canDrain,
                                        final Predicate<FluidStack> validator) {

        super(handler);
        this._canFill = canFill;
        this._canDrain = canDrain;
        this._validator = validator;
    }

    @Override
    public boolean isFluidValid(final int tank, final FluidStack stack) {
        return this._canFill && this._validator.test(stack) && super.isFluidValid(tank, stack);
    }

    @Override
    public int fill(final FluidStack resource, final FluidAction action) {
        return this._canFill && !resource.isEmpty() && this._validator.test(resource) ? super.fill(resource, action) : 0;
    }

    @Override
    public FluidStack drain(final FluidStack resource, final FluidAction action) {
        return this._canDrain ? super.drain(resource, action) : FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(final int maxDrain, final FluidAction action) {
        return this._canDrain ? super.drain(maxDrain, action) : FluidStack.EMPTY;
    }

    private final boolean _canFill;
    private final boolean _canDrain;
    private final Predicate<FluidStack> _validator;
}

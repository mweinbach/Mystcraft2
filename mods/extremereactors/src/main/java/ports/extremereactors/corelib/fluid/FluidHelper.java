/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.fluid;

import net.minecraft.core.Registry;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import ports.extremereactors.corelib.fluid.handler.EmptyFluidHandler;
import ports.extremereactors.corelib.fluid.handler.IFluidHandler;
import ports.extremereactors.platform.Platform;

public final class FluidHelper {

    /** Millibuckets in a bucket. */
    public static final int BUCKET_VOLUME = 1000;

    public static final IFluidHandler EMPTY_FLUID_HANDLER = EmptyFluidHandler.INSTANCE;

    public static ResourceLocation getFluidId(final Fluid fluid) {
        return Registry.FLUID.getKey(fluid);
    }

    public static MutableComponent getFluidName(final Fluid fluid) {
        return Platform.backend().fluidName(fluid).copy();
    }

    public static MutableComponent getFluidName(final FluidStack stack) {
        return stack.getDisplayName().copy();
    }

    /** A new stack of the same fluid and NBT as {@code stack}, with the given amount. */
    public static FluidStack stackFrom(final FluidStack stack, final int amount) {
        return stack.isEmpty() || amount <= 0 ? FluidStack.EMPTY : new FluidStack(stack, amount);
    }

    private FluidHelper() {
    }
}

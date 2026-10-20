/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.compat.jei;

import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.helpers.IJeiHelpers;
import ports.extremereactors.corelib.fluid.FluidStack;

import java.util.Collection;

/**
 * Puts the port's fluid stacks, measured in millibuckets on both loaders, into JEI slots. JEI counts fluids in the
 * platform's own unit: millibuckets on Forge, droplets on Fabric.
 */
public final class JeiFluids {

    /** Call once JEI hands out its helpers, before any recipe is laid out. */
    public static void initialize(final IJeiHelpers helpers) {
        s_bucketVolume = helpers.getPlatformFluidHelper().bucketVolume();
    }

    /**
     * @return the given amount in JEI's fluid unit
     */
    public static long amount(final long millibuckets) {
        return millibuckets * s_bucketVolume / 1000L;
    }

    public static IRecipeSlotBuilder addFluids(final IRecipeSlotBuilder slot, final Collection<FluidStack> stacks) {

        for (final FluidStack stack : stacks) {
            slot.addFluidStack(stack.getFluid(), amount(stack.getAmount()), stack.getTag());
        }

        return slot;
    }

    //region internals

    private JeiFluids() {
    }

    private static long s_bucketVolume = 1000L;

    //endregion
}

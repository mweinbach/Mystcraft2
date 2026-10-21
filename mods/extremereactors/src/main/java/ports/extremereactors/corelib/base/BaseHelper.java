/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.base;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import ports.extremereactors.corelib.fluid.FluidHelper;
import ports.extremereactors.corelib.fluid.FluidStack;

/**
 * Text helpers shared by the screens and the recipe viewer integrations.
 */
public final class BaseHelper {

    /**
     * @return the fluid's name, or Extreme Reactors' "Empty" text for an empty stack
     */
    public static MutableComponent getFluidNameOrEmpty(final FluidStack stack) {
        return stack.isEmpty() ? Component.translatable("gui.bigreactors.generic.empty") : FluidHelper.getFluidName(stack);
    }

    private BaseHelper() {
    }
}

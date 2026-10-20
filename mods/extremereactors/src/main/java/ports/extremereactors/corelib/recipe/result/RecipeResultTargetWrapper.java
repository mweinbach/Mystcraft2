/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.recipe.result;

import net.minecraft.world.item.ItemStack;
import ports.extremereactors.corelib.data.stack.OperationMode;
import ports.extremereactors.corelib.fluid.FluidStack;
import ports.extremereactors.corelib.fluid.handler.IFluidHandler;
import ports.extremereactors.corelib.item.inventory.handler.IItemHandler;

/**
 * Adapts a tank or an inventory slot to {@link IRecipeResultTarget}. Results are only stored whole.
 */
public final class RecipeResultTargetWrapper {

    public static IRecipeResultTarget<FluidStackRecipeResult> wrap(final IFluidHandler handler) {
        return new IRecipeResultTarget<>() {

            @Override
            public int countStorableResults(final FluidStackRecipeResult result) {

                final FluidStack stack = result.getResult();

                if (stack.isEmpty()) {
                    return 0;
                }

                final int amount = stack.getAmount();

                stack.setAmount(Integer.MAX_VALUE);
                return handler.fill(stack, IFluidHandler.FluidAction.SIMULATE) / amount;
            }

            @Override
            public long setResult(final FluidStackRecipeResult result, final OperationMode mode) {

                final FluidStack stack = result.getResult();

                if (stack.isEmpty() || handler.fill(stack, IFluidHandler.FluidAction.SIMULATE) < stack.getAmount()) {
                    return 0;
                }

                return mode.execute() ? handler.fill(stack, IFluidHandler.FluidAction.EXECUTE) : stack.getAmount();
            }
        };
    }

    public static IRecipeResultTarget<ItemStackRecipeResult> wrap(final IItemHandler handler, final int slot) {
        return new IRecipeResultTarget<>() {

            @Override
            public int countStorableResults(final ItemStackRecipeResult result) {

                final ItemStack probe = result.getResult();

                if (probe.isEmpty()) {
                    return 0;
                }

                final int perResult = probe.getCount();

                probe.setCount(Math.max(perResult, probe.getMaxStackSize()));

                final int accepted = probe.getCount() - handler.insertItem(slot, probe, true).getCount();

                return accepted / perResult;
            }

            @Override
            public long setResult(final ItemStackRecipeResult result, final OperationMode mode) {

                final ItemStack stack = result.getResult();

                if (stack.isEmpty() || !handler.insertItem(slot, stack.copy(), true).isEmpty()) {
                    return 0;
                }

                return stack.getCount() - handler.insertItem(slot, stack, mode.simulate()).getCount();
            }
        };
    }

    private RecipeResultTargetWrapper() {
    }
}

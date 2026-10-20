/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.recipe.ingredient;

import net.minecraft.world.item.ItemStack;
import ports.extremereactors.corelib.fluid.FluidStack;
import ports.extremereactors.corelib.fluid.handler.IFluidHandler;
import ports.extremereactors.corelib.item.inventory.handler.IItemHandler;

/**
 * Adapts an inventory slot or a tank to {@link IRecipeIngredientSource}.
 */
public final class RecipeIngredientSourceWrapper {

    public static IRecipeIngredientSource<ItemStack> wrap(final IItemHandler handler, final int slot) {
        return new IRecipeIngredientSource<>() {

            @Override
            public ItemStack getIngredient() {
                return handler.getStackInSlot(slot);
            }

            @Override
            public ItemStack getMatchFrom(final IRecipeIngredient<ItemStack> ingredient) {
                return ingredient.getMatchFrom(this.getIngredient());
            }

            @Override
            public void consumeIngredient(final ItemStack ingredient) {

                if (!ingredient.isEmpty()) {
                    handler.extractItem(slot, ingredient.getCount(), false);
                }
            }
        };
    }

    public static IRecipeIngredientSource<FluidStack> wrap(final IFluidHandler handler, final int tank) {
        return new IRecipeIngredientSource<>() {

            @Override
            public FluidStack getIngredient() {
                return handler.getFluidInTank(tank);
            }

            @Override
            public FluidStack getMatchFrom(final IRecipeIngredient<FluidStack> ingredient) {
                return ingredient.getMatchFrom(this.getIngredient());
            }

            @Override
            public void consumeIngredient(final FluidStack ingredient) {

                if (!ingredient.isEmpty()) {
                    handler.drain(ingredient, IFluidHandler.FluidAction.EXECUTE);
                }
            }
        };
    }

    private RecipeIngredientSourceWrapper() {
    }
}

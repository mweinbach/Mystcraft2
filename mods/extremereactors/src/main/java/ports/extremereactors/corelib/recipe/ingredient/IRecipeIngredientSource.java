/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.recipe.ingredient;

/**
 * A place a machine takes recipe ingredients from, such as an inventory slot or a tank.
 *
 * @param <T> the stack type (item or fluid stacks)
 */
public interface IRecipeIngredientSource<T> {

    /** The stack currently available. Do not modify it. */
    T getIngredient();

    /** The part of the available stack that the ingredient would consume, or an empty stack if it does not match. */
    T getMatchFrom(IRecipeIngredient<T> ingredient);

    /** Remove the given stack (as returned by {@link #getMatchFrom(IRecipeIngredient)}) from the source. */
    void consumeIngredient(T ingredient);
}

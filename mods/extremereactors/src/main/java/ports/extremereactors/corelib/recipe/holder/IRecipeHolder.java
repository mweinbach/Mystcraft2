/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.recipe.holder;

import ports.extremereactors.corelib.recipe.ModRecipe;

/**
 * What a held recipe needs from the machine that holds it.
 */
public interface IRecipeHolder<R extends ModRecipe> {

    /** The ticks needed to complete one cycle of the recipe. */
    int getRecipeTicks(R recipe);

    /** True if the machine can process a tick of the recipe right now. */
    boolean canProcess(R recipe);

    void onRecipeTickProcessed(int currentTick);

    void onRecipeProcessed();

    /** Look the current recipe up again on the next request. */
    void refresh();
}

/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.recipe.holder;

import ports.extremereactors.corelib.recipe.ModRecipe;

/**
 * A recipe a machine is working on, together with its progress.
 */
public interface IHeldRecipe<R extends ModRecipe> {

    R getRecipe();

    /** The ticks processed so far for the current cycle. */
    int getCurrentTick();

    /** Restore the progress of the current cycle, for example after loading or synchronizing. */
    void setCurrentTick(int tick);

    /** The progress of the current cycle, from 0 to 1. */
    double getProgress();

    /**
     * Process one tick of the recipe; completes a cycle once enough ticks have been processed.
     *
     * @return true if the tick was processed, false if the machine could not process it
     */
    boolean processRecipe();
}

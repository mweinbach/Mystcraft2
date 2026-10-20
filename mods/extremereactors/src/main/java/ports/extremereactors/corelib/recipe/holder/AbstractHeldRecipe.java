/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.recipe.holder;

import ports.extremereactors.corelib.recipe.ModRecipe;

/**
 * Tick-counting part of a held recipe. Subclasses move the ingredients and results in {@link #onRecipeProcessed()}.
 * <p>
 * Each processed tick advances the counter and notifies the holder. When the counter reaches the recipe's tick
 * count the cycle completes: {@link #onRecipeProcessed()} runs, the holder is notified and the counter restarts.
 * A tick the holder refuses to process leaves the progress unchanged.
 */
public abstract class AbstractHeldRecipe<R extends ModRecipe>
        implements IHeldRecipe<R> {

    protected AbstractHeldRecipe(final R recipe, final IRecipeHolder<R> holder) {

        this._recipe = recipe;
        this._holder = holder;
        this._currentTick = 0;
    }

    /** Called when a cycle completes. */
    public abstract void onRecipeProcessed();

    //region IHeldRecipe

    @Override
    public R getRecipe() {
        return this._recipe;
    }

    @Override
    public int getCurrentTick() {
        return this._currentTick;
    }

    @Override
    public void setCurrentTick(final int tick) {
        this._currentTick = Math.max(0, Math.min(tick, this.getTicks()));
    }

    @Override
    public double getProgress() {

        final int ticks = this.getTicks();

        return ticks > 0 ? Math.min(1.0, (double)this._currentTick / (double)ticks) : 0.0;
    }

    @Override
    public boolean processRecipe() {

        if (!this._holder.canProcess(this._recipe)) {
            return false;
        }

        ++this._currentTick;
        this._holder.onRecipeTickProcessed(this._currentTick);

        if (this._currentTick >= this.getTicks()) {

            this.onRecipeProcessed();
            this._currentTick = 0;
            this._holder.onRecipeProcessed();
        }

        return true;
    }

    //endregion
    //region internals

    protected IRecipeHolder<R> getHolder() {
        return this._holder;
    }

    private int getTicks() {
        return this._holder.getRecipeTicks(this._recipe);
    }

    private final R _recipe;
    private final IRecipeHolder<R> _holder;
    private int _currentTick;

    //endregion
}

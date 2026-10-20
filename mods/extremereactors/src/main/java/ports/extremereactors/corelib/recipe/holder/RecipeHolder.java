/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.recipe.holder;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import ports.extremereactors.corelib.data.nbt.ISyncableEntity;
import ports.extremereactors.corelib.recipe.ModRecipe;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * Tracks the recipe a machine is working on.
 * <p>
 * The recipe is looked up through the factory the first time it is requested, after {@link #refresh()} and whenever
 * the machine reports that its ingredients changed. If the lookup finds the same recipe again the progress is kept;
 * a different recipe (or none) starts over and fires the recipe-changed callback.
 * <p>
 * The recipe id and current tick are saved and synchronized, so progress survives reloads and the client can show it.
 */
public final class RecipeHolder<R extends ModRecipe>
        implements IRecipeHolder<R>, ISyncableEntity {

    public static <R extends ModRecipe> Builder<R> builder(final Function<IRecipeHolder<R>, IHeldRecipe<R>> factory,
                                                           final ToIntFunction<R> recipeTicks) {
        return new Builder<>(factory, recipeTicks);
    }

    public static final class Builder<R extends ModRecipe> {

        /** The machine reports (and clears) whether its ingredients changed since the last call. */
        public Builder<R> onHasIngredientsChanged(final BooleanSupplier callback) {

            this._hasIngredientsChanged = Objects.requireNonNull(callback);
            return this;
        }

        public Builder<R> onCanProcess(final Predicate<R> callback) {

            this._canProcess = Objects.requireNonNull(callback);
            return this;
        }

        public Builder<R> onRecipeTickProcessed(final IntConsumer callback) {

            this._onRecipeTickProcessed = Objects.requireNonNull(callback);
            return this;
        }

        public Builder<R> onRecipeProcessed(final Runnable callback) {

            this._onRecipeProcessed = Objects.requireNonNull(callback);
            return this;
        }

        /** Called with the new recipe, or null when the machine no longer has one. */
        public Builder<R> onRecipeChanged(final Consumer<R> callback) {

            this._onRecipeChanged = Objects.requireNonNull(callback);
            return this;
        }

        public RecipeHolder<R> build() {
            return new RecipeHolder<>(this);
        }

        private Builder(final Function<IRecipeHolder<R>, IHeldRecipe<R>> factory, final ToIntFunction<R> recipeTicks) {

            this._factory = Objects.requireNonNull(factory);
            this._recipeTicks = Objects.requireNonNull(recipeTicks);
            this._hasIngredientsChanged = () -> false;
            this._canProcess = recipe -> true;
            this._onRecipeTickProcessed = tick -> {};
            this._onRecipeProcessed = () -> {};
            this._onRecipeChanged = recipe -> {};
        }

        private final Function<IRecipeHolder<R>, IHeldRecipe<R>> _factory;
        private final ToIntFunction<R> _recipeTicks;
        private BooleanSupplier _hasIngredientsChanged;
        private Predicate<R> _canProcess;
        private IntConsumer _onRecipeTickProcessed;
        private Runnable _onRecipeProcessed;
        private Consumer<R> _onRecipeChanged;
    }

    /** The recipe found by the last lookup, without looking it up again. */
    public Optional<IHeldRecipe<R>> getHeldRecipe() {

        if (null != this._pendingRecipeId) {
            this.lookup();
        }

        return Optional.ofNullable(this._held);
    }

    /** The current recipe, looked up again first if it may have changed. */
    public Optional<IHeldRecipe<R>> getCurrentRecipe() {

        final boolean ingredientsChanged = this._hasIngredientsChanged.getAsBoolean();

        if (this._dirty || ingredientsChanged || null != this._pendingRecipeId) {
            this.lookup();
        }

        return Optional.ofNullable(this._held);
    }

    //region IRecipeHolder

    @Override
    public int getRecipeTicks(final R recipe) {
        return this._recipeTicks.applyAsInt(recipe);
    }

    @Override
    public boolean canProcess(final R recipe) {
        return this._canProcess.test(recipe);
    }

    @Override
    public void onRecipeTickProcessed(final int currentTick) {
        this._onRecipeTickProcessed.accept(currentTick);
    }

    @Override
    public void onRecipeProcessed() {
        this._onRecipeProcessed.run();
    }

    @Override
    public void refresh() {
        this._dirty = true;
    }

    //endregion
    //region ISyncableEntity

    @Override
    public void syncDataFrom(final CompoundTag data, final SyncReason syncReason) {

        if (data.contains(NBT_RECIPE)) {

            this._pendingRecipeId = ResourceLocation.tryParse(data.getString(NBT_RECIPE));
            this._pendingTick = data.getInt(NBT_TICK);

            if (null != this._held && this._held.getRecipe().getId().equals(this._pendingRecipeId)) {

                this._held.setCurrentTick(this._pendingTick);
                this._pendingRecipeId = null;
            }

        } else {

            this._pendingRecipeId = null;

            if (null != this._held) {
                this._held.setCurrentTick(0);
            }
        }

        this._dirty = true;
    }

    @Override
    public CompoundTag syncDataTo(final CompoundTag data, final SyncReason syncReason) {

        if (null != this._held) {

            data.putString(NBT_RECIPE, this._held.getRecipe().getId().toString());
            data.putInt(NBT_TICK, this._held.getCurrentTick());

        } else if (null != this._pendingRecipeId) {

            data.putString(NBT_RECIPE, this._pendingRecipeId.toString());
            data.putInt(NBT_TICK, this._pendingTick);
        }

        return data;
    }

    //endregion
    //region internals

    private RecipeHolder(final Builder<R> builder) {

        this._factory = builder._factory;
        this._recipeTicks = builder._recipeTicks;
        this._hasIngredientsChanged = builder._hasIngredientsChanged;
        this._canProcess = builder._canProcess;
        this._onRecipeTickProcessed = builder._onRecipeTickProcessed;
        this._onRecipeProcessed = builder._onRecipeProcessed;
        this._onRecipeChanged = builder._onRecipeChanged;
        this._dirty = true;
    }

    private void lookup() {

        this._dirty = false;

        final IHeldRecipe<R> previous = this._held;
        final IHeldRecipe<R> found = this._factory.apply(this);
        final R previousRecipe = null == previous ? null : previous.getRecipe();
        final R foundRecipe = null == found ? null : found.getRecipe();

        final boolean sameRecipe = Objects.equals(previousRecipe, foundRecipe);

        if (null != found) {

            if (null != this._pendingRecipeId && foundRecipe.getId().equals(this._pendingRecipeId)) {
                found.setCurrentTick(this._pendingTick);
            } else if (sameRecipe && found != previous) {
                found.setCurrentTick(previous.getCurrentTick());
            }
        }

        this._pendingRecipeId = null;
        this._held = found;

        if (!sameRecipe) {
            this._onRecipeChanged.accept(foundRecipe);
        }
    }

    private static final String NBT_RECIPE = "id";
    private static final String NBT_TICK = "tick";

    private final Function<IRecipeHolder<R>, IHeldRecipe<R>> _factory;
    private final ToIntFunction<R> _recipeTicks;
    private final BooleanSupplier _hasIngredientsChanged;
    private final Predicate<R> _canProcess;
    private final IntConsumer _onRecipeTickProcessed;
    private final Runnable _onRecipeProcessed;
    private final Consumer<R> _onRecipeChanged;

    @Nullable
    private IHeldRecipe<R> _held;
    private boolean _dirty;
    @Nullable
    private ResourceLocation _pendingRecipeId;
    private int _pendingTick;

    //endregion
}

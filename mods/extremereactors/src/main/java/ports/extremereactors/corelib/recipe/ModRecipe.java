/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.recipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;

/**
 * Base class for machine recipes. They live in the vanilla recipe manager so data packs can add, replace or remove
 * them, but they are never crafted through a {@link Container}: machines look them up with
 * {@link ModRecipeType#findFirst(java.util.function.Predicate)} and match their own inputs.
 */
public abstract class ModRecipe
        implements Recipe<Container>, ISerializableRecipe {

    protected ModRecipe(final ResourceLocation id) {
        this._id = id;
    }

    //region Recipe

    @Override
    public ResourceLocation getId() {
        return this._id;
    }

    @Override
    public boolean matches(final Container container, final Level level) {
        return false;
    }

    @Override
    public ItemStack assemble(final Container container) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(final int width, final int height) {
        return false;
    }

    @Override
    public ItemStack getResultItem() {
        return ItemStack.EMPTY;
    }

    /** Keeps the client recipe book from trying to categorize machine recipes. */
    @Override
    public boolean isSpecial() {
        return true;
    }

    //endregion
    //region Object

    @Override
    public boolean equals(final Object other) {
        return this == other || (other instanceof ModRecipe recipe && this.getClass() == recipe.getClass() &&
                this._id.equals(recipe._id));
    }

    @Override
    public int hashCode() {
        return this._id.hashCode();
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "[" + this._id + "]";
    }

    //endregion
    //region internals

    private final ResourceLocation _id;

    //endregion
}

/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.recipe;

import net.minecraft.resources.ResourceLocation;
import ports.extremereactors.corelib.recipe.ingredient.IRecipeIngredient;
import ports.extremereactors.corelib.recipe.result.IRecipeResult;

/**
 * A recipe that turns one ingredient into one result.
 *
 * @param <Input> the ingredient stack type
 * @param <Output> the result stack type
 */
public abstract class AbstractOneToOneRecipe<Input, Output,
        IngredientT extends IRecipeIngredient<Input>, ResultT extends IRecipeResult<Output>>
        extends ModRecipe {

    protected AbstractOneToOneRecipe(final ResourceLocation id, final IngredientT ingredient, final ResultT result) {

        super(id);
        this._ingredient = ingredient;
        this._result = result;
    }

    public IngredientT getIngredient() {
        return this._ingredient;
    }

    public ResultT getResult() {
        return this._result;
    }

    /** True if the stack satisfies the ingredient, amount included. */
    public boolean test(final Input stack) {
        return this._ingredient.test(stack);
    }

    //region internals

    private final IngredientT _ingredient;
    private final ResultT _result;

    //endregion
}

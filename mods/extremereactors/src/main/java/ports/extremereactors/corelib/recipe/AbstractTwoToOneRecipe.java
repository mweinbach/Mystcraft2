/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.recipe;

import net.minecraft.resources.ResourceLocation;
import ports.extremereactors.corelib.recipe.ingredient.IRecipeIngredient;
import ports.extremereactors.corelib.recipe.result.IRecipeResult;

import java.util.function.IntFunction;

/**
 * A recipe that combines two ingredients into one result. The JSON key of each ingredient is given by
 * {@code jsonLabels}: index 0 for the first ingredient, 1 for the second.
 */
public abstract class AbstractTwoToOneRecipe<Input1, Input2, Output,
        Ingredient1T extends IRecipeIngredient<Input1>, Ingredient2T extends IRecipeIngredient<Input2>,
        ResultT extends IRecipeResult<Output>>
        extends ModRecipe {

    protected AbstractTwoToOneRecipe(final ResourceLocation id, final Ingredient1T ingredient1,
                                     final Ingredient2T ingredient2, final ResultT result,
                                     final IntFunction<String> jsonLabels) {

        super(id);
        this._ingredient1 = ingredient1;
        this._ingredient2 = ingredient2;
        this._result = result;
        this._jsonLabels = jsonLabels;
    }

    public Ingredient1T getIngredient1() {
        return this._ingredient1;
    }

    public Ingredient2T getIngredient2() {
        return this._ingredient2;
    }

    public ResultT getResult() {
        return this._result;
    }

    public String getJsonLabel(final int ingredientIndex) {
        return this._jsonLabels.apply(ingredientIndex);
    }

    /** True if both stacks satisfy their ingredient, amounts included. */
    public boolean test(final Input1 stack1, final Input2 stack2) {
        return this._ingredient1.test(stack1) && this._ingredient2.test(stack2);
    }

    //region internals

    private final Ingredient1T _ingredient1;
    private final Ingredient2T _ingredient2;
    private final ResultT _result;
    private final IntFunction<String> _jsonLabels;

    //endregion
}

/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.datagen.provider.recipe;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import ports.extremereactors.corelib.recipe.ingredient.IRecipeIngredient;
import ports.extremereactors.corelib.recipe.result.IRecipeResult;
import ports.extremereactors.corelib.recipe.serializer.OneToOneRecipeSerializer;

import java.util.function.IntFunction;

public class TwoToOneRecipeBuilder<Input1, Input2, Output>
        extends AbstractModRecipeBuilder<TwoToOneRecipeBuilder<Input1, Input2, Output>> {

    public TwoToOneRecipeBuilder(final ResourceLocation serializerId, final IRecipeIngredient<Input1> ingredient1,
                                 final IRecipeIngredient<Input2> ingredient2, final IRecipeResult<Output> result,
                                 final IntFunction<String> jsonLabels) {

        super(serializerId);
        this._ingredient1 = ingredient1;
        this._ingredient2 = ingredient2;
        this._result = result;
        this._jsonLabels = jsonLabels;
    }

    @Override
    protected void serializeRecipeData(final JsonObject json) {

        json.add(this._jsonLabels.apply(0), this._ingredient1.toJson());
        json.add(this._jsonLabels.apply(1), this._ingredient2.toJson());
        json.add(OneToOneRecipeSerializer.JSON_RESULT, this._result.toJson());
    }

    private final IRecipeIngredient<Input1> _ingredient1;
    private final IRecipeIngredient<Input2> _ingredient2;
    private final IRecipeResult<Output> _result;
    private final IntFunction<String> _jsonLabels;
}

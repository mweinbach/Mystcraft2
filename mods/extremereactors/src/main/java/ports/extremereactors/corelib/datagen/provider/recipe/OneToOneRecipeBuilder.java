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

public class OneToOneRecipeBuilder<Input, Output>
        extends AbstractModRecipeBuilder<OneToOneRecipeBuilder<Input, Output>> {

    public OneToOneRecipeBuilder(final ResourceLocation serializerId, final IRecipeIngredient<Input> ingredient,
                                 final IRecipeResult<Output> result) {

        super(serializerId);
        this._ingredient = ingredient;
        this._result = result;
    }

    @Override
    protected void serializeRecipeData(final JsonObject json) {

        json.add(OneToOneRecipeSerializer.JSON_INGREDIENT, this._ingredient.toJson());
        json.add(OneToOneRecipeSerializer.JSON_RESULT, this._result.toJson());
    }

    private final IRecipeIngredient<Input> _ingredient;
    private final IRecipeResult<Output> _result;
}

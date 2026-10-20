/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.recipe.serializer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import ports.extremereactors.corelib.recipe.AbstractTwoToOneRecipe;
import ports.extremereactors.corelib.recipe.ingredient.IRecipeIngredient;
import ports.extremereactors.corelib.recipe.result.IRecipeResult;

import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * Reads and writes two-to-one recipes. The ingredient JSON keys come from {@code jsonLabels} (index 0 and 1); the
 * result key is {@code "result"}.
 */
public class TwoToOneRecipeSerializer<Recipe extends AbstractTwoToOneRecipe<?, ?, ?, Ingredient1T, Ingredient2T, ResultT>,
        Ingredient1T extends IRecipeIngredient<?>, Ingredient2T extends IRecipeIngredient<?>, ResultT extends IRecipeResult<?>>
        implements RecipeSerializer<Recipe> {

    @FunctionalInterface
    public interface Factory<Recipe, Ingredient1T, Ingredient2T, ResultT> {
        Recipe create(ResourceLocation id, Ingredient1T ingredient1, Ingredient2T ingredient2, ResultT result);
    }

    public TwoToOneRecipeSerializer(final Factory<Recipe, Ingredient1T, Ingredient2T, ResultT> factory,
                                    final Function<JsonElement, Ingredient1T> ingredient1FromJson,
                                    final Function<FriendlyByteBuf, Ingredient1T> ingredient1FromNetwork,
                                    final Function<JsonElement, Ingredient2T> ingredient2FromJson,
                                    final Function<FriendlyByteBuf, Ingredient2T> ingredient2FromNetwork,
                                    final Function<JsonElement, ResultT> resultFromJson,
                                    final Function<FriendlyByteBuf, ResultT> resultFromNetwork,
                                    final IntFunction<String> jsonLabels) {

        this._factory = factory;
        this._ingredient1FromJson = ingredient1FromJson;
        this._ingredient1FromNetwork = ingredient1FromNetwork;
        this._ingredient2FromJson = ingredient2FromJson;
        this._ingredient2FromNetwork = ingredient2FromNetwork;
        this._resultFromJson = resultFromJson;
        this._resultFromNetwork = resultFromNetwork;
        this._jsonLabels = jsonLabels;
    }

    //region RecipeSerializer

    @Override
    public Recipe fromJson(final ResourceLocation id, final JsonObject json) {
        return this._factory.create(id,
                this._ingredient1FromJson.apply(OneToOneRecipeSerializer.member(json, this._jsonLabels.apply(0))),
                this._ingredient2FromJson.apply(OneToOneRecipeSerializer.member(json, this._jsonLabels.apply(1))),
                this._resultFromJson.apply(OneToOneRecipeSerializer.member(json, OneToOneRecipeSerializer.JSON_RESULT)));
    }

    @Override
    public Recipe fromNetwork(final ResourceLocation id, final FriendlyByteBuf buffer) {

        final Ingredient1T ingredient1 = this._ingredient1FromNetwork.apply(buffer);
        final Ingredient2T ingredient2 = this._ingredient2FromNetwork.apply(buffer);

        return this._factory.create(id, ingredient1, ingredient2, this._resultFromNetwork.apply(buffer));
    }

    @Override
    public void toNetwork(final FriendlyByteBuf buffer, final Recipe recipe) {

        recipe.getIngredient1().toNetwork(buffer);
        recipe.getIngredient2().toNetwork(buffer);
        recipe.getResult().toNetwork(buffer);
    }

    //endregion
    //region internals

    private final Factory<Recipe, Ingredient1T, Ingredient2T, ResultT> _factory;
    private final Function<JsonElement, Ingredient1T> _ingredient1FromJson;
    private final Function<FriendlyByteBuf, Ingredient1T> _ingredient1FromNetwork;
    private final Function<JsonElement, Ingredient2T> _ingredient2FromJson;
    private final Function<FriendlyByteBuf, Ingredient2T> _ingredient2FromNetwork;
    private final Function<JsonElement, ResultT> _resultFromJson;
    private final Function<FriendlyByteBuf, ResultT> _resultFromNetwork;
    private final IntFunction<String> _jsonLabels;

    //endregion
}

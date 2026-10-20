/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.recipe.serializer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import ports.extremereactors.corelib.recipe.AbstractOneToOneRecipe;
import ports.extremereactors.corelib.recipe.ingredient.IRecipeIngredient;
import ports.extremereactors.corelib.recipe.result.IRecipeResult;

import java.util.function.Function;

/**
 * Reads and writes one-to-one recipes. JSON keys: {@code "ingredient"} and {@code "result"}.
 */
public class OneToOneRecipeSerializer<Recipe extends AbstractOneToOneRecipe<?, ?, IngredientT, ResultT>,
        IngredientT extends IRecipeIngredient<?>, ResultT extends IRecipeResult<?>>
        implements RecipeSerializer<Recipe> {

    public static final String JSON_INGREDIENT = "ingredient";
    public static final String JSON_RESULT = "result";

    @FunctionalInterface
    public interface Factory<Recipe, IngredientT, ResultT> {
        Recipe create(ResourceLocation id, IngredientT ingredient, ResultT result);
    }

    public OneToOneRecipeSerializer(final Factory<Recipe, IngredientT, ResultT> factory,
                                    final Function<JsonElement, IngredientT> ingredientFromJson,
                                    final Function<FriendlyByteBuf, IngredientT> ingredientFromNetwork,
                                    final Function<JsonElement, ResultT> resultFromJson,
                                    final Function<FriendlyByteBuf, ResultT> resultFromNetwork) {

        this._factory = factory;
        this._ingredientFromJson = ingredientFromJson;
        this._ingredientFromNetwork = ingredientFromNetwork;
        this._resultFromJson = resultFromJson;
        this._resultFromNetwork = resultFromNetwork;
    }

    //region RecipeSerializer

    @Override
    public Recipe fromJson(final ResourceLocation id, final JsonObject json) {
        return this._factory.create(id, this._ingredientFromJson.apply(member(json, JSON_INGREDIENT)),
                this._resultFromJson.apply(member(json, JSON_RESULT)));
    }

    @Override
    public Recipe fromNetwork(final ResourceLocation id, final FriendlyByteBuf buffer) {

        final IngredientT ingredient = this._ingredientFromNetwork.apply(buffer);

        return this._factory.create(id, ingredient, this._resultFromNetwork.apply(buffer));
    }

    @Override
    public void toNetwork(final FriendlyByteBuf buffer, final Recipe recipe) {

        recipe.getIngredient().toNetwork(buffer);
        recipe.getResult().toNetwork(buffer);
    }

    //endregion
    //region internals

    static JsonElement member(final JsonObject json, final String name) {

        final JsonElement element = json.get(name);

        if (null == element || element.isJsonNull()) {
            throw new JsonSyntaxException("Missing " + name + " in recipe");
        }

        return element;
    }

    private final Factory<Recipe, IngredientT, ResultT> _factory;
    private final Function<JsonElement, IngredientT> _ingredientFromJson;
    private final Function<FriendlyByteBuf, IngredientT> _ingredientFromNetwork;
    private final Function<JsonElement, ResultT> _resultFromJson;
    private final Function<FriendlyByteBuf, ResultT> _resultFromNetwork;

    //endregion
}

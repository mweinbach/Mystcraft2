/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.recipe.ingredient;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;

import java.util.List;

/**
 * An item ingredient: a vanilla {@link Ingredient} plus a required count.
 * <p>
 * JSON: {@code {"ingredient": <vanilla ingredient>, "count": N}}; a bare vanilla ingredient means a count of 1.
 */
public final class ItemStackRecipeIngredient
        implements IRecipeIngredient<ItemStack> {

    public static ItemStackRecipeIngredient from(final ItemLike item, final int amount) {
        return new ItemStackRecipeIngredient(Ingredient.of(item), amount);
    }

    public static ItemStackRecipeIngredient from(final TagKey<Item> tag, final int amount) {
        return new ItemStackRecipeIngredient(Ingredient.of(tag), amount);
    }

    public static ItemStackRecipeIngredient from(final Ingredient ingredient, final int amount) {
        return new ItemStackRecipeIngredient(ingredient, amount);
    }

    public static ItemStackRecipeIngredient from(final JsonElement json) {

        if (!json.isJsonObject()) {
            return new ItemStackRecipeIngredient(Ingredient.fromJson(json), 1);
        }

        final JsonObject object = json.getAsJsonObject();

        if (!object.has(JSON_INGREDIENT)) {
            return new ItemStackRecipeIngredient(Ingredient.fromJson(object), 1);
        }

        final int amount = GsonHelper.getAsInt(object, JSON_COUNT, 1);

        if (amount < 1) {
            throw new JsonSyntaxException("Invalid ingredient count: " + amount);
        }

        return new ItemStackRecipeIngredient(Ingredient.fromJson(object.get(JSON_INGREDIENT)), amount);
    }

    public static ItemStackRecipeIngredient from(final FriendlyByteBuf buffer) {

        final Ingredient ingredient = Ingredient.fromNetwork(buffer);

        return new ItemStackRecipeIngredient(ingredient, buffer.readVarInt());
    }

    public Ingredient getIngredient() {
        return this._ingredient;
    }

    //region IRecipeIngredient

    @Override
    public int getAmount() {
        return this._amount;
    }

    @Override
    public boolean test(final ItemStack stack) {
        return this.testIgnoreAmount(stack) && stack.getCount() >= this._amount;
    }

    @Override
    public boolean testIgnoreAmount(final ItemStack stack) {
        return !stack.isEmpty() && this._ingredient.test(stack);
    }

    @Override
    public ItemStack getMatchFrom(final ItemStack stack) {

        if (!this.test(stack)) {
            return ItemStack.EMPTY;
        }

        final ItemStack match = stack.copy();

        match.setCount(this._amount);
        return match;
    }

    @Override
    public List<ItemStack> getMatchingElements() {

        final ImmutableList.Builder<ItemStack> list = ImmutableList.builder();

        for (final ItemStack stack : this._ingredient.getItems()) {

            final ItemStack copy = stack.copy();

            copy.setCount(this._amount);
            list.add(copy);
        }

        return list.build();
    }

    @Override
    public boolean isEmpty() {
        return this._ingredient.isEmpty();
    }

    @Override
    public JsonElement toJson() {

        final JsonObject json = new JsonObject();

        json.add(JSON_INGREDIENT, this._ingredient.toJson());
        json.addProperty(JSON_COUNT, this._amount);
        return json;
    }

    @Override
    public void toNetwork(final FriendlyByteBuf buffer) {

        this._ingredient.toNetwork(buffer);
        buffer.writeVarInt(this._amount);
    }

    //endregion
    //region internals

    private ItemStackRecipeIngredient(final Ingredient ingredient, final int amount) {

        this._ingredient = ingredient;
        this._amount = Math.max(1, amount);
    }

    private static final String JSON_INGREDIENT = "ingredient";
    private static final String JSON_COUNT = "count";

    private final Ingredient _ingredient;
    private final int _amount;

    //endregion
}

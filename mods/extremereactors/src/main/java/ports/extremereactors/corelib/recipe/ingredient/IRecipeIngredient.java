/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.recipe.ingredient;

import com.google.gson.JsonElement;
import net.minecraft.network.FriendlyByteBuf;

import java.util.List;
import java.util.function.Predicate;

/**
 * One input of a recipe: something that matches a family of stacks and requires a minimum amount of them.
 *
 * @param <T> the stack type (item or fluid stacks)
 */
public interface IRecipeIngredient<T>
        extends Predicate<T> {

    /** The amount of the matching stack the recipe consumes. */
    int getAmount();

    /** True if the stack matches this ingredient and holds at least {@link #getAmount()}. */
    @Override
    boolean test(T stack);

    /** True if the stack matches this ingredient, whatever its size. */
    boolean testIgnoreAmount(T stack);

    /**
     * The part of the stack this ingredient would consume: a copy resized to {@link #getAmount()}, or an empty stack
     * if the stack does not satisfy {@link #test(Object)}.
     */
    T getMatchFrom(T stack);

    /** Every stack this ingredient accepts, each sized to {@link #getAmount()}. Used for recipe viewers. */
    List<T> getMatchingElements();

    boolean isEmpty();

    JsonElement toJson();

    void toNetwork(FriendlyByteBuf buffer);
}

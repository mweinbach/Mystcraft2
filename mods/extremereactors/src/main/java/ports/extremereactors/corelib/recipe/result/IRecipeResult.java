/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.recipe.result;

import com.google.gson.JsonElement;
import net.minecraft.network.FriendlyByteBuf;

/**
 * The output of a recipe.
 *
 * @param <T> the stack type (item or fluid stacks)
 */
public interface IRecipeResult<T> {

    /** A fresh copy of the produced stack. */
    T getResult();

    /** The produced amount: items for item results, mB for fluid results. */
    long getAmount();

    boolean isEmpty();

    JsonElement toJson();

    void toNetwork(FriendlyByteBuf buffer);
}

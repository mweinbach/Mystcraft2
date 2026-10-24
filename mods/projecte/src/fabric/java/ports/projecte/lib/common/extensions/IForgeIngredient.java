/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.common.extensions;

import net.minecraft.world.item.crafting.Ingredient;
import ports.projecte.lib.common.crafting.IIngredientSerializer;
import ports.projecte.lib.common.crafting.VanillaIngredientSerializer;

/**
 * Methods Forge adds to {@link Ingredient}, injected into it on Fabric. A simple ingredient matches on item alone, so
 * recipe lookups may compare items instead of testing stacks.
 */
public interface IForgeIngredient {

    default boolean isSimple() {
        return true;
    }

    default boolean isVanilla() {
        return getClass() == Ingredient.class;
    }

    default IIngredientSerializer<? extends Ingredient> getSerializer() {
        if (isVanilla()) {
            return VanillaIngredientSerializer.INSTANCE;
        }
        throw new IllegalStateException("Modders must implement Ingredient.getSerializer in their custom Ingredients: " + this);
    }
}

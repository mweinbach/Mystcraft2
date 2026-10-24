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

    /**
     * Simple unless Fabric's {@code requiresTesting} says otherwise, which covers Fabric's own NBT ingredients and the
     * Forge ingredient types the Mekanism port carries its own copy of, when its reader runs before this port's.
     */
    default boolean projecte$isSimple() {
        return !((Ingredient) (Object) this).requiresTesting();
    }

    default boolean projecte$isVanilla() {
        return getClass() == Ingredient.class;
    }

    default IIngredientSerializer<? extends Ingredient> getSerializer() {
        if (projecte$isVanilla()) {
            return VanillaIngredientSerializer.INSTANCE;
        }
        throw new IllegalStateException("Modders must implement Ingredient.getSerializer in their custom Ingredients: " + this);
    }
}

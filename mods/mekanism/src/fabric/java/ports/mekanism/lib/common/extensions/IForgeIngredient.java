/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.common.extensions;

import net.minecraft.world.item.crafting.Ingredient;
import ports.mekanism.lib.common.crafting.IIngredientSerializer;
import ports.mekanism.lib.common.crafting.VanillaIngredientSerializer;

/**
 * Methods Forge adds to {@link Ingredient}, injected into it on Fabric. A simple ingredient matches on item alone, so
 * recipe lookups may compare items instead of testing stacks.
 */
public interface IForgeIngredient {

    /**
     * Forge treats ingredients as simple unless they say otherwise. On Fabric, an ingredient this port did not create
     * says otherwise through Fabric's {@code requiresTesting}: Fabric's own NBT ingredients, and the Forge ingredient
     * types another mod carries its own copy of (ProjectE's port). When that mod's reader runs before this port's, a
     * {@code forge:nbt} or {@code forge:partial_nbt} input in a Mekanism recipe is that mod's object, and must still
     * be tested by stack rather than looked up by item.
     */
    default boolean isSimple() {
        return !((Ingredient) (Object) this).requiresTesting();
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

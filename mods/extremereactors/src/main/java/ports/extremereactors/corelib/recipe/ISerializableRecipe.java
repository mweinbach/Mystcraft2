/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.recipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * A recipe loaded from (and synchronized as) data through its {@link RecipeSerializer}.
 */
public interface ISerializableRecipe {

    ResourceLocation getId();

    RecipeSerializer<?> getSerializer();

    RecipeType<?> getType();
}

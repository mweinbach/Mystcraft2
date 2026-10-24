/*
 * ProjectE 1.19.2 port - Fabric implementation of the Forge API shapes ProjectE uses.
 * Copyright (c) 2026 ProjectE 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.common.brewing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * The brewing recipes beyond vanilla's. Fabric mods add brewing mixes to {@code PotionBrewing} directly, which
 * {@link VanillaBrewingRecipe} already reads, so this list starts with that one recipe.
 */
public final class BrewingRecipeRegistry {

    private static final List<IBrewingRecipe> RECIPES = new ArrayList<>();

    static {
        RECIPES.add(new VanillaBrewingRecipe());
    }

    private BrewingRecipeRegistry() {
    }

    public static boolean addRecipe(Ingredient input, Ingredient ingredient, ItemStack output) {
        return addRecipe(new BrewingRecipe(input, ingredient, output));
    }

    public static boolean addRecipe(IBrewingRecipe recipe) {
        return RECIPES.add(recipe);
    }

    public static List<IBrewingRecipe> getRecipes() {
        return Collections.unmodifiableList(RECIPES);
    }
}

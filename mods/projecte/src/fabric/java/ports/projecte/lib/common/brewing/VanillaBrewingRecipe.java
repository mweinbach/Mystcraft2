/*
 * ProjectE 1.19.2 port - Fabric implementation of the Forge API shapes ProjectE uses.
 * Copyright (c) 2026 ProjectE 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.common.brewing;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionBrewing;

/**
 * Vanilla's potion and container mixes, read from {@link PotionBrewing}.
 */
public class VanillaBrewingRecipe implements IBrewingRecipe {

    @Override
    public boolean isInput(ItemStack stack) {
        return PotionBrewing.ALLOWED_CONTAINERS.stream().anyMatch(ingredient -> ingredient.test(stack));
    }

    @Override
    public boolean isIngredient(ItemStack stack) {
        return PotionBrewing.isIngredient(stack);
    }

    @Override
    public ItemStack getOutput(ItemStack input, ItemStack ingredient) {
        if (!input.isEmpty() && !ingredient.isEmpty() && isIngredient(ingredient)) {
            ItemStack result = PotionBrewing.mix(ingredient, input);
            return result != input ? result : ItemStack.EMPTY;
        }
        return ItemStack.EMPTY;
    }
}

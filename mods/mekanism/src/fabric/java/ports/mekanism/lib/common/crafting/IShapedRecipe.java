/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.common.crafting;

import net.minecraft.world.Container;
import net.minecraft.world.item.crafting.Recipe;

/**
 * A recipe with a fixed grid size. Injected into vanilla's ShapedRecipe on Fabric.
 */
public interface IShapedRecipe<T extends Container> extends Recipe<T> {

    int getRecipeWidth();

    int getRecipeHeight();
}

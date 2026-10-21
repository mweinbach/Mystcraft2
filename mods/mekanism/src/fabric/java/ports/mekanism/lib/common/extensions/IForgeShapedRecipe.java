/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.common.extensions;

import net.minecraft.world.item.crafting.ShapedRecipe;

/**
 * Forge's {@code IShapedRecipe} dimensions, injected into {@link ShapedRecipe} on Fabric.
 */
public interface IForgeShapedRecipe {

    default int getRecipeWidth() {
        return ((ShapedRecipe) this).getWidth();
    }

    default int getRecipeHeight() {
        return ((ShapedRecipe) this).getHeight();
    }
}

/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.client.event;

import net.minecraft.world.item.crafting.RecipeManager;
import ports.mekanism.lib.eventbus.api.Event;

/**
 * Fired on the client after it received the server's recipes.
 */
public class RecipesUpdatedEvent extends Event {

    private final RecipeManager recipeManager;

    public RecipesUpdatedEvent(RecipeManager recipeManager) {
        this.recipeManager = recipeManager;
    }

    public RecipeManager getRecipeManager() {
        return recipeManager;
    }
}

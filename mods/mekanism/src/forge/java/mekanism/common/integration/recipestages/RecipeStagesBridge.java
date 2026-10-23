/*
 * Mekanism 1.19.2 port - Forge adapter for Recipe Stages and GameStages.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.common.integration.recipestages;

import com.blamejared.recipestages.RecipeStagesUtil;
import com.blamejared.recipestages.recipes.IStagedRecipe;
import java.util.UUID;
import net.darkhax.gamestages.GameStageHelper;
import net.darkhax.gamestages.data.GameStageSaveHandler;
import net.darkhax.gamestages.data.IStageData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

/**
 * Loaded by name from {@link RecipeStagesCompat#load()} only when Recipe Stages is present.
 */
public final class RecipeStagesBridge implements RecipeStagesCompat.Bridge {

    @Override
    public boolean isStaged(Recipe<CraftingContainer> recipe) {
        return recipe instanceof IStagedRecipe;
    }

    @Override
    public ItemStack forceAssemble(Recipe<CraftingContainer> recipe, CraftingContainer inventory) {
        return recipe instanceof IStagedRecipe stagedRecipe ? stagedRecipe.forceAssemble(inventory) : recipe.assemble(inventory);
    }

    @Override
    public boolean hasStage(Recipe<CraftingContainer> recipe, Player player) {
        return RecipeStagesUtil.hasStageForRecipe(recipe, player);
    }

    @Override
    public boolean ownerHasStage(Recipe<CraftingContainer> recipe, MinecraftServer server, UUID owner) {
        if (!(recipe instanceof IStagedRecipe stagedRecipe)) {
            return true;
        }
        String stage = stagedRecipe.getStage();
        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        if (player != null) {
            // Online: GameStages' own check, which other mods can veto through GameStageEvent.Check.
            return GameStageHelper.hasStage(player, stage);
        }
        // Offline: GameStages keeps the stage data of every player who joined since the server started.
        IStageData data = GameStageSaveHandler.getPlayerData(owner);
        return data != null && data.hasStage(stage);
    }
}

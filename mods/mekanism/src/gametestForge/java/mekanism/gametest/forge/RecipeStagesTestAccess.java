/*
 * Mekanism 1.19.2 port - in-world tests, Forge side.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest.forge;

import com.blamejared.recipestages.recipes.RecipeStage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import net.darkhax.gamestages.data.GameStageSaveHandler;
import net.darkhax.gamestages.data.IStageData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * The Recipe Stages and Game Stages calls {@code ForgeRecipeStagesGameTests} makes. Kept out of the scanned test package so
 * the test class itself loads without those mods.
 */
public final class RecipeStagesTestAccess {

    private RecipeStagesTestAccess() {
    }

    /**
     * What Recipe Stages' {@code recipestages:stage} serializer makes from a data pack recipe.
     */
    public static CraftingRecipe staged(ResourceLocation id, String stage, CraftingRecipe recipe) {
        return new RecipeStage(id, stage, recipe, true);
    }

    /**
     * Game Stages reads a player's stages when the server loads that player's file. A fresh directory has none.
     */
    public static void loadStages(Player player) {
        File directory;
        try {
            directory = Files.createTempDirectory("mekanism-gamestages").toFile();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.LoadFromFile(player, directory, player.getStringUUID()));
    }

    public static void grant(Player player, String stage) {
        IStageData data = GameStageSaveHandler.getPlayerData(player.getUUID());
        if (data == null) {
            throw new IllegalStateException("Game Stages has no data for " + player.getUUID());
        }
        data.addStage(stage);
    }
}

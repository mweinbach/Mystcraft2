/*
 * Mekanism 1.19.2 port - shared entry point for the Recipe Stages integration.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.common.integration.recipestages;

import java.util.UUID;
import mekanism.common.Mekanism;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Recipe Stages gates crafting-table recipes behind GameStages stages. It has only Forge builds, so upstream's direct calls
 * go through this class and the Forge JAR installs {@code RecipeStagesBridge} when the mod is loaded. Without a bridge every
 * method behaves as if no recipe were staged.
 * <p>
 * A staged recipe's own {@code assemble} looks for the one player using the crafting menu. Mekanism's crafting grids have
 * no such player, so the callers decide here: the QIO crafting window lets the player who takes the result decide (upstream
 * behaviour), and the Formulaic Assemblicator lets its owner decide.
 */
public final class RecipeStagesCompat {

    private static final String BRIDGE = "mekanism.common.integration.recipestages.RecipeStagesBridge";
    @Nullable
    private static Bridge bridge;

    private RecipeStagesCompat() {
    }

    public static void load() {
        try {
            bridge = (Bridge) Class.forName(BRIDGE).getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            // The Fabric JAR has no Recipe Stages bridge; there is no Fabric build of the mod.
            bridge = null;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Recipe Stages is loaded but Mekanism's bridge failed to load", e);
        }
    }

    @Nullable
    private static Bridge bridge() {
        return Mekanism.hooks.RecipeStagesLoaded ? bridge : null;
    }

    /**
     * @return {@code true} if Recipe Stages is loaded and gates this recipe behind a stage.
     */
    public static boolean isStaged(Recipe<CraftingContainer> recipe) {
        Bridge active = bridge();
        return active != null && active.isStaged(recipe);
    }

    /**
     * The QIO crafting window: assembles a staged recipe whoever is looking, as {@link #hasStageForRecipe} decides who may take
     * it.
     */
    public static ItemStack assembleIgnoringStage(Recipe<CraftingContainer> recipe, CraftingContainer inventory) {
        Bridge active = bridge();
        if (active != null && active.isStaged(recipe)) {
            //Force assemble it as we handle validating if specific players can see/grab the output ourselves
            return active.forceAssemble(recipe, inventory);
        }
        return recipe.assemble(inventory);
    }

    /**
     * @return {@code false} only if Recipe Stages gates the recipe and the player lacks its stage.
     */
    public static boolean hasStageForRecipe(Recipe<CraftingContainer> recipe, Player player) {
        Bridge active = bridge();
        return active == null || active.hasStage(recipe, player);
    }

    /**
     * Machines: a staged recipe is assembled when the block's owner has the stage. Otherwise, and for blocks without an owner,
     * Recipe Stages' own rules decide, so stages a pack grants to Mekanism's crafting container still apply.
     */
    public static ItemStack assembleForOwner(Recipe<CraftingContainer> recipe, CraftingContainer inventory, @Nullable Level level,
          @Nullable UUID owner) {
        Bridge active = bridge();
        if (active != null && owner != null && level != null && active.isStaged(recipe)) {
            MinecraftServer server = level.getServer();
            if (server != null && active.ownerHasStage(recipe, server, owner)) {
                return active.forceAssemble(recipe, inventory);
            }
        }
        return recipe.assemble(inventory);
    }

    /**
     * What the Forge JAR implements with Recipe Stages and GameStages types.
     */
    public interface Bridge {

        boolean isStaged(Recipe<CraftingContainer> recipe);

        ItemStack forceAssemble(Recipe<CraftingContainer> recipe, CraftingContainer inventory);

        boolean hasStage(Recipe<CraftingContainer> recipe, Player player);

        /**
         * @return {@code true} if the owner, online or with stage data loaded this session, has the recipe's stage.
         */
        boolean ownerHasStage(Recipe<CraftingContainer> recipe, MinecraftServer server, UUID owner);
    }
}

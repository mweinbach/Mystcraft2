/*
 * Mekanism 1.19.2 port - in-world tests, Forge side.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import static mekanism.gametest.CoreMachineGameTests.TEMPLATE;
import static mekanism.gametest.CoreMachineGameTests.check;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.Mekanism;
import mekanism.common.integration.MekanismHooks;
import mekanism.common.integration.recipestages.RecipeStagesCompat;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.tile.machine.TileEntityFormulaicAssemblicator;
import mekanism.common.util.MekanismUtils;
import mekanism.gametest.forge.RecipeStagesTestAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraftforge.fml.ModList;

/**
 * Recipe Stages: Mekanism's crafting grids respect staged crafting recipes. The QIO crafting window lets the player taking the
 * result decide (upstream), and the Formulaic Assemblicator lets its owner decide. Recipe Stages and Game Stages have no
 * Fabric build for 1.19.2. Run with {@code -PwithRecipeStages}; without the mod the tests check the integration is off.
 * <p>
 * The staged recipe is added to the recipe manager for the test, as Recipe Stages' {@code recipestages:stage} serializer would
 * add it from a data pack, and removed afterwards.
 */
public class ForgeRecipeStagesGameTests {

    private static final String STAGE = "mekanism_gametest";
    private static final ResourceLocation RECIPE_ID = new ResourceLocation(Mekanism.MODID, "gametest_staged_diamond");

    @GameTest(template = TEMPLATE)
    public static void assemblicatorCraftsStagedRecipesForItsOwner(GameTestHelper helper) {
        if (!recipeStagesLoaded()) {
            helper.succeed();
            return;
        }
        ServerPlayer owner = player(helper, "assemblicator_owner");
        RecipeStagesTestAccess.loadStages(owner);
        withStagedRecipe(helper, () -> {
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, MekanismBlocks.FORMULAIC_ASSEMBLICATOR.getBlock());
            TileEntityFormulaicAssemblicator assemblicator = (TileEntityFormulaicAssemblicator) helper.getBlockEntity(pos);
            List<IInventorySlot> slots = assemblicator.getInventorySlots(null);
            // 0 formula, 1-18 inputs, 19-27 crafting grid, 28-33 outputs, 34 energy
            slots.get(19).setStack(new ItemStack(Items.DEAD_BUSH));
            slots.get(20).setStack(new ItemStack(Items.FLINT));
            slots.get(21).setStack(new ItemStack(Items.STICK));

            check(!assemblicator.hasRecipe(), "An assemblicator without an owner crafted a staged recipe");
            check(!assemblicator.craftSingle(), "An assemblicator without an owner crafted a staged recipe");

            assemblicator.setOwnerUUID(owner.getUUID());
            check(!assemblicator.craftSingle(), "The assemblicator crafted a staged recipe for an owner without the stage");

            RecipeStagesTestAccess.grant(owner, STAGE);
            check(assemblicator.craftSingle(), "The assemblicator did not craft a staged recipe for an owner with the stage");
            check(slots.get(28).getStack().is(Items.DIAMOND), "The assemblicator's output holds " + slots.get(28).getStack());
            check(slots.get(19).isEmpty() && slots.get(20).isEmpty() && slots.get(21).isEmpty(), "The assemblicator did not use its ingredients");
        });
        helper.succeed();
    }

    /**
     * The QIO crafting window shows a staged recipe's result to everyone and lets only players with the stage take it.
     */
    @GameTest(template = TEMPLATE)
    public static void qioCraftingWindowChecksThePlayersStage(GameTestHelper helper) {
        if (!recipeStagesLoaded()) {
            helper.succeed();
            return;
        }
        ServerPlayer player = player(helper, "qio_crafter");
        RecipeStagesTestAccess.loadStages(player);
        CraftingRecipe staged = RecipeStagesTestAccess.staged(RECIPE_ID, STAGE, innerRecipe());
        CraftingContainer grid = MekanismUtils.getDummyCraftingInv();
        grid.setItem(0, new ItemStack(Items.DEAD_BUSH));
        grid.setItem(1, new ItemStack(Items.FLINT));
        grid.setItem(2, new ItemStack(Items.STICK));
        check(staged.matches(grid, helper.getLevel()), "The staged recipe does not match its ingredients");
        check(RecipeStagesCompat.isStaged(staged), "Mekanism does not see the Recipe Stages recipe as staged");
        check(staged.assemble(grid).isEmpty(), "Recipe Stages assembled a staged recipe in a grid with no player");
        check(RecipeStagesCompat.assembleIgnoringStage(staged, grid).is(Items.DIAMOND), "The QIO window did not assemble the staged recipe");
        check(!RecipeStagesCompat.hasStageForRecipe(staged, player), "A player without the stage may take a staged recipe");
        RecipeStagesTestAccess.grant(player, STAGE);
        check(RecipeStagesCompat.hasStageForRecipe(staged, player), "A player with the stage may not take a staged recipe");
        check(RecipeStagesCompat.hasStageForRecipe(innerRecipe(), player), "An unstaged recipe is gated");
        helper.succeed();
    }

    private static boolean recipeStagesLoaded() {
        boolean loaded = ModList.get().isLoaded(MekanismHooks.RECIPE_STAGES_MOD_ID);
        check(Mekanism.hooks.RecipeStagesLoaded == loaded, "RecipeStagesLoaded is " + Mekanism.hooks.RecipeStagesLoaded + " with Recipe Stages "
                                                           + (loaded ? "installed" : "absent"));
        if (!loaded) {
            CraftingRecipe recipe = innerRecipe();
            check(!RecipeStagesCompat.isStaged(recipe), "A recipe is staged without Recipe Stages");
            check(RecipeStagesCompat.hasStageForRecipe(recipe, null), "A recipe is gated without Recipe Stages");
        }
        return loaded;
    }

    private static CraftingRecipe innerRecipe() {
        return new ShapelessRecipe(RECIPE_ID, "", new ItemStack(Items.DIAMOND),
              NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.DEAD_BUSH), Ingredient.of(Items.FLINT), Ingredient.of(Items.STICK)));
    }

    private static void withStagedRecipe(GameTestHelper helper, Runnable test) {
        RecipeManager manager = helper.getLevel().getRecipeManager();
        List<Recipe<?>> original = new ArrayList<>(manager.getRecipes());
        List<Recipe<?>> withStaged = new ArrayList<>(original);
        withStaged.add(RecipeStagesTestAccess.staged(RECIPE_ID, STAGE, innerRecipe()));
        manager.replaceRecipes(withStaged);
        try {
            test.run();
        } finally {
            manager.replaceRecipes(original);
        }
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        return new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), name), null);
    }
}

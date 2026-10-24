/*
 * ProjectE 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 ProjectE 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package moze_intel.projecte.gametest;

import static moze_intel.projecte.gametest.PEGameTestSupport.TEMPLATE;
import static moze_intel.projecte.gametest.PEGameTestSupport.check;

import java.util.Optional;
import moze_intel.projecte.gameObjs.registries.PEItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** The Philosopher's Stone: world transmutation and its use as a reusable crafting ingredient. */
public class PhilosophersStoneGameTests {

    private static final BlockPos TARGET = new BlockPos(2, 1, 2);

    @GameTest(template = TEMPLATE)
    public static void transmutesStoneIntoCobblestone(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.STONE);
        ServerPlayer player = PEGameTestSupport.player(helper, "philo_stone");
        use(helper, player, TARGET);
        helper.assertBlockPresent(Blocks.COBBLESTONE, TARGET);
        helper.succeed();
    }

    /**
     * The back-and-forth pairs registered in code (crimson and warped planks here). Upstream 1.1.0's
     * {@code registerDefault} drops the sneaking alternate, so sneaking is not tested.
     */
    @GameTest(template = TEMPLATE)
    public static void transmutesCrimsonPlanksIntoWarped(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.CRIMSON_PLANKS);
        ServerPlayer player = PEGameTestSupport.player(helper, "philo_planks");
        use(helper, player, TARGET);
        helper.assertBlockPresent(Blocks.WARPED_PLANKS, TARGET);
        helper.succeed();
    }

    /** Four coal and the stone make alchemical coal, and the stone stays in the grid. */
    @GameTest(template = TEMPLATE)
    public static void craftingKeepsTheStone(GameTestHelper helper) {
        CraftingContainer grid = new CraftingContainer(new AbstractContainerMenu(null, -1) {
            @Override
            public ItemStack quickMoveStack(Player player, int index) {
                return ItemStack.EMPTY;
            }

            @Override
            public boolean stillValid(Player player) {
                return true;
            }
        }, 3, 3);
        grid.setItem(0, new ItemStack(PEItems.PHILOSOPHERS_STONE.get()));
        for (int slot = 1; slot <= 4; slot++) {
            grid.setItem(slot, new ItemStack(Items.COAL));
        }
        Optional<CraftingRecipe> recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, helper.getLevel());
        check(helper, recipe.isPresent(), "No recipe for the stone and four coal");
        ItemStack result = recipe.get().assemble(grid);
        check(helper, result.is(PEItems.ALCHEMICAL_COAL.get()), "The stone and four coal made " + result);
        NonNullList<ItemStack> remaining = recipe.get().getRemainingItems(grid);
        check(helper, remaining.get(0).is(PEItems.PHILOSOPHERS_STONE.get()), "The stone was used up: " + remaining);
        helper.succeed();
    }

    private static void use(GameTestHelper helper, ServerPlayer player, BlockPos relative) {
        ItemStack stone = new ItemStack(PEItems.PHILOSOPHERS_STONE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stone);
        BlockPos pos = helper.absolutePos(relative);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        stone.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }
}

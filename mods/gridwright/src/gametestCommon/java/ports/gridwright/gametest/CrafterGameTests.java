package ports.gridwright.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import ports.gridwright.registry.ModBlocks;
import ports.gridwright.worker.MechanicalCrafterBlockEntity;
import ports.gridwright.worker.WorkerBlockEntity.Status;

import static ports.gridwright.gametest.GameTestSupport.Owner;
import static ports.gridwright.gametest.GameTestSupport.TEMPLATE;
import static ports.gridwright.gametest.GameTestSupport.check;
import static ports.gridwright.gametest.GameTestSupport.owned;
import static ports.gridwright.gametest.GameTestSupport.place;
import static ports.gridwright.gametest.GameTestSupport.platform;
import static ports.gridwright.gametest.GameTestSupport.producer;
import static ports.gridwright.gametest.GameTestSupport.waterColumn;

/** The mechanical crafter: crafting from its grid, keeping the layout, refills by pipe, and its leftovers. */
public class CrafterGameTests {

    private static final BlockPos CRAFTER = new BlockPos(2, 2, 2);
    private static final BlockPos MILL = new BlockPos(5, 2, 5);
    private static final int OUTPUT = 9;

    private static MechanicalCrafterBlockEntity crafter(GameTestHelper helper, Owner owner) {
        MechanicalCrafterBlockEntity crafter = owned(place(helper, ModBlocks.MECHANICAL_CRAFTER, CRAFTER), owner);
        // A two-sided water mill: 6 GP for the crafter's 4.
        producer(helper, ModBlocks.WATER_MILL, MILL, owner);
        waterColumn(helper, MILL, Direction.EAST);
        waterColumn(helper, MILL, Direction.WEST);
        return crafter;
    }

    private static int output(MechanicalCrafterBlockEntity crafter, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int slot = OUTPUT; slot < OUTPUT + 9; slot++) if (crafter.getItem(slot).is(item)) total += crafter.getItem(slot).getCount();
        return total;
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void crafterKeepsOneOfEachAsThePattern(GameTestHelper helper) {
        MechanicalCrafterBlockEntity crafter = crafter(helper, Owner.random("gwMc"));
        // A chest: planks all round the grid, three planks each.
        for (int slot = 0; slot < 9; slot++) if (slot != 4) crafter.setItem(slot, new ItemStack(Items.OAK_PLANKS, 3));
        helper.startSequence()
                .thenWaitUntil(() -> check(output(crafter, Items.CHEST) == 2, "Chests made: " + output(crafter, Items.CHEST)))
                // The status shown is the last tick's; the crafter notices on its next tick.
                .thenWaitUntil(() -> check(crafter.status() == Status.SHORT, "Status " + crafter.status()))
                .thenExecuteFor(30, () -> {
                    check(output(crafter, Items.CHEST) == 2, "Crafted past the pattern");
                    check(crafter.status() == Status.SHORT, "Status " + crafter.status());
                    for (int slot = 0; slot < 9; slot++) {
                        int expected = slot == 4 ? 0 : 1;
                        check(crafter.getItem(slot).getCount() == expected, "Slot " + slot + " holds " + crafter.getItem(slot));
                    }
                })
                .thenExecute(() -> {
                    TestPlatform.ItemAccess pipe = platform().items(crafter, Direction.UP);
                    check(!pipe.insert(new ItemStack(Items.COBBLESTONE)).isEmpty(), "Pipes put cobblestone in a planks grid");
                    // Eight planks fill the eight plank slots evenly, one each.
                    for (int i = 0; i < 8; i++) check(pipe.insert(new ItemStack(Items.OAK_PLANKS)).isEmpty(), "Refused planks");
                    for (int slot = 0; slot < 9; slot++) {
                        int expected = slot == 4 ? 0 : 2;
                        check(crafter.getItem(slot).getCount() == expected, "Uneven refill: slot " + slot + " holds " + crafter.getItem(slot));
                    }
                    check(pipe.extract(0, 1, true).isEmpty(), "Pipes took an ingredient");
                })
                .thenWaitUntil(() -> check(output(crafter, Items.CHEST) == 3, "Refill not crafted"))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void crafterRefillsUnstackablesAndKeepsLeftovers(GameTestHelper helper) {
        MechanicalCrafterBlockEntity crafter = crafter(helper, Owner.random("gwMk"));
        // A cake: milk, sugar, egg, wheat. Milk buckets do not stack, so they are used up and refilled.
        for (int slot = 0; slot < 3; slot++) crafter.setItem(slot, new ItemStack(Items.MILK_BUCKET));
        crafter.setItem(3, new ItemStack(Items.SUGAR, 2));
        crafter.setItem(4, new ItemStack(Items.EGG, 2));
        crafter.setItem(5, new ItemStack(Items.SUGAR, 2));
        for (int slot = 6; slot < 9; slot++) crafter.setItem(slot, new ItemStack(Items.WHEAT, 2));
        helper.startSequence()
                .thenWaitUntil(() -> {
                    check(output(crafter, Items.CAKE) == 1, "No cake");
                    check(output(crafter, Items.BUCKET) == 3, "Buckets left over: " + output(crafter, Items.BUCKET));
                    for (int slot = 0; slot < 3; slot++) check(crafter.getItem(slot).isEmpty(), "Milk left in slot " + slot);
                })
                .thenWaitUntil(() -> check(crafter.status() == Status.SHORT, "Should wait for milk: " + crafter.status()))
                .thenExecute(() -> {
                    TestPlatform.ItemAccess pipe = platform().items(crafter, Direction.NORTH);
                    for (int i = 0; i < 3; i++) check(pipe.insert(new ItemStack(Items.MILK_BUCKET)).isEmpty(), "Milk refused");
                    for (int slot = 0; slot < 3; slot++) check(crafter.getItem(slot).is(Items.MILK_BUCKET), "Milk not back in slot " + slot);
                    check(!pipe.insert(new ItemStack(Items.MILK_BUCKET)).isEmpty(), "A fourth milk bucket was taken");
                })
                .thenExecuteFor(5, () -> check(crafter.status() == Status.SHORT && output(crafter, Items.CAKE) == 1,
                        "Should wait for the stackables: " + crafter.status()))
                .thenExecute(() -> {
                    TestPlatform.ItemAccess pipe = platform().items(crafter, Direction.NORTH);
                    for (int slot = 3; slot < 9; slot++) {
                        ItemStack held = crafter.getItem(slot);
                        check(pipe.insert(new ItemStack(held.getItem())).isEmpty(), "Refused " + held.getItem());
                    }
                })
                .thenWaitUntil(() -> check(output(crafter, Items.CAKE) == 2, "Second cake not made"))
                .thenSucceed();
    }

    /** A failed search cached before a datapack reload (an older recipe manager) is searched again. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void crafterSearchesAgainAfterARecipeReload(GameTestHelper helper) {
        MechanicalCrafterBlockEntity crafter = crafter(helper, Owner.random("gwMr"));
        crafter.setItem(0, new ItemStack(Items.OAK_PLANKS, 3));
        crafter.setItem(3, new ItemStack(Items.OAK_PLANKS, 3));
        // As if this layout had found nothing under the recipe manager a reload since replaced.
        java.util.List<net.minecraft.world.item.Item> key = new java.util.ArrayList<>();
        for (int slot = 0; slot < 9; slot++) key.add(crafter.getItem(slot).getItem());
        GameTestSupport.setField(MechanicalCrafterBlockEntity.class, "cachedKey", crafter, key);
        GameTestSupport.setField(MechanicalCrafterBlockEntity.class, "cachedRecipe", crafter, null);
        GameTestSupport.setField(MechanicalCrafterBlockEntity.class, "cachedManager", crafter,
                new net.minecraft.world.item.crafting.RecipeManager());
        helper.succeedWhen(() -> check(output(crafter, Items.STICK) > 0, "No sticks after the reload: " + crafter.status()));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void crafterNeedsPowerARecipeAndNoSignal(GameTestHelper helper) {
        Owner owner = Owner.random("gwMp");
        MechanicalCrafterBlockEntity crafter = owned(place(helper, ModBlocks.MECHANICAL_CRAFTER, CRAFTER), owner);
        crafter.setItem(0, new ItemStack(Items.DIRT, 5));
        crafter.setItem(1, new ItemStack(Items.OAK_PLANKS, 5));
        helper.startSequence()
                .thenWaitUntil(() -> check(crafter.status() == Status.NO_RECIPE, "Dirt and planks: " + crafter.status()))
                .thenExecuteFor(10, () -> check(crafter.status() == Status.NO_RECIPE, "Dirt and planks: " + crafter.status()))
                .thenExecute(() -> {
                    crafter.setItem(0, new ItemStack(Items.OAK_PLANKS, 5));
                    crafter.setItem(1, ItemStack.EMPTY);
                    crafter.setItem(3, new ItemStack(Items.OAK_PLANKS, 5));
                })
                .thenWaitUntil(() -> check(crafter.status() == Status.OVERLOADED, "Unpowered crafter: " + crafter.status()))
                .thenExecute(() -> check(output(crafter, Items.STICK) == 0, "Crafted without power"))
                .thenExecute(() -> helper.setBlock(CRAFTER.above(), Blocks.REDSTONE_BLOCK))
                .thenWaitUntil(() -> check(crafter.status() == Status.PAUSED, "Ignored redstone: " + crafter.status()))
                .thenSucceed();
    }
}

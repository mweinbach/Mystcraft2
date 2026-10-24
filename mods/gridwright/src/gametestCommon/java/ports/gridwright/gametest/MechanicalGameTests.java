package ports.gridwright.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import ports.gridwright.block.MechanicalBlock;
import ports.gridwright.registry.ModBlocks;
import ports.gridwright.worker.MechanicalMinerBlockEntity;
import ports.gridwright.worker.MechanicalUserBlockEntity;
import ports.gridwright.worker.WorkerBlockEntity;
import ports.gridwright.worker.WorkerBlockEntity.Status;
import ports.gridwright.worker.WorkerLayout;

import java.util.List;
import java.util.function.Supplier;

import static ports.gridwright.gametest.GameTestSupport.Owner;
import static ports.gridwright.gametest.GameTestSupport.TEMPLATE;
import static ports.gridwright.gametest.GameTestSupport.check;
import static ports.gridwright.gametest.GameTestSupport.owned;

/** The mechanical miner and user act as their owner, through the loader events claim mods use. */
public class MechanicalGameTests {

    private static final BlockPos MACHINE = new BlockPos(2, 2, 3);
    private static final BlockPos FRONT = MACHINE.east();

    /** Places a mechanical block facing east (towards {@link #FRONT}), owned by {@code owner} if not null. */
    @SuppressWarnings("unchecked")
    private static <T extends WorkerBlockEntity> T machine(GameTestHelper helper, Supplier<? extends MechanicalBlock> block, Owner owner) {
        helper.setBlock(MACHINE, block.get().defaultBlockState().setValue(MechanicalBlock.FACING, Direction.EAST));
        T machine = (T) helper.getBlockEntity(MACHINE);
        check(machine != null, "No block entity");
        return owner == null ? machine : owned(machine, owner);
    }

    /** How many of {@code item} are in the worker's output slots. */
    private static int output(Container container, WorkerLayout layout, Item item) {
        int total = 0;
        for (int slot = layout.firstOutput(); slot < layout.size(); slot++) {
            if (container.getItem(slot).is(item)) total += container.getItem(slot).getCount();
        }
        return total;
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void minerBreaksWithItsTool(GameTestHelper helper) {
        Owner owner = Owner.random("gwMa");
        MechanicalMinerBlockEntity miner = machine(helper, ModBlocks.MECHANICAL_MINER, owner);
        int toolSlot = WorkerLayout.MECHANICAL_MINER.first(WorkerLayout.Role.TOOL);
        miner.setItem(toolSlot, new ItemStack(Items.IRON_PICKAXE));
        helper.setBlock(FRONT, Blocks.STONE);
        TestProtection.power(helper, owner, MechanicalMinerBlockEntity.GP);
        helper.startSequence()
                .thenWaitUntil(() -> check(helper.getBlockState(FRONT).isAir(), "Stone still there (" + miner.status() + ")"))
                .thenExecute(() -> {
                    check(output(miner, WorkerLayout.MECHANICAL_MINER, Items.COBBLESTONE) == 1, "No cobblestone in the output");
                    check(miner.getItem(toolSlot).is(Items.IRON_PICKAXE) && miner.getItem(toolSlot).getDamageValue() == 1,
                            "The pickaxe did not wear: " + miner.getItem(toolSlot));
                    helper.setBlock(FRONT, Blocks.DIRT);
                })
                .thenWaitUntil(() -> check(helper.getBlockState(FRONT).isAir(), "Dirt still there"))
                .thenExecute(() -> check(output(miner, WorkerLayout.MECHANICAL_MINER, Items.DIRT) == 1, "No dirt in the output"))
                .thenSucceed();
    }

    /** Bare-handed, stone takes a player's bare-handed time and drops nothing. */
    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void minerWithoutToolHarvestsLikeAHand(GameTestHelper helper) {
        Owner owner = Owner.random("gwMb");
        MechanicalMinerBlockEntity miner = machine(helper, ModBlocks.MECHANICAL_MINER, owner);
        helper.setBlock(FRONT, Blocks.STONE);
        TestProtection.power(helper, owner, MechanicalMinerBlockEntity.GP);
        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> check(helper.getBlockState(FRONT).is(Blocks.STONE), "Bare hands broke stone in 40 ticks"))
                .thenWaitUntil(() -> check(helper.getBlockState(FRONT).isAir(), "Stone still there"))
                .thenExecute(() -> check(miner.isEmpty(), "Bare-handed stone dropped something"))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void minerRespectsProtection(GameTestHelper helper) {
        Owner owner = Owner.random("gwMc");
        MechanicalMinerBlockEntity miner = machine(helper, ModBlocks.MECHANICAL_MINER, owner);
        miner.setItem(WorkerLayout.MECHANICAL_MINER.first(WorkerLayout.Role.TOOL), new ItemStack(Items.DIAMOND_PICKAXE));
        helper.setBlock(FRONT, Blocks.DIRT);
        TestProtection.protect(helper, FRONT);
        TestProtection.power(helper, owner, MechanicalMinerBlockEntity.GP);
        helper.startSequence()
                .thenWaitUntil(() -> check(miner.status() == Status.PROTECTED, "Expected PROTECTED, got " + miner.status()))
                .thenIdle(20)
                .thenExecute(() -> check(helper.getBlockState(FRONT).is(Blocks.DIRT), "Mined a protected block"))
                .thenSucceed();
    }

    /** Swapping the tool, even for the same pickaxe with another enchantment, or removing it restarts the break. */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void minerRestartsWhenTheToolChanges(GameTestHelper helper) {
        Owner owner = Owner.random("gwMf");
        MechanicalMinerBlockEntity miner = machine(helper, ModBlocks.MECHANICAL_MINER, owner);
        int toolSlot = WorkerLayout.MECHANICAL_MINER.first(WorkerLayout.Role.TOOL);
        miner.setItem(toolSlot, new ItemStack(Items.DIAMOND_PICKAXE));
        helper.setBlock(FRONT, Blocks.OBSIDIAN);
        TestProtection.power(helper, owner, MechanicalMinerBlockEntity.GP);
        ItemStack silkTouch = new ItemStack(Items.DIAMOND_PICKAXE);
        silkTouch.enchant(Enchantments.SILK_TOUCH, 1);
        helper.startSequence()
                .thenWaitUntil(() -> check(miner.data().get(2) > 300, "Progress " + miner.data().get(2) + " (" + miner.status() + ")"))
                .thenExecute(() -> miner.setItem(toolSlot, silkTouch))
                .thenExecuteAfter(2, () -> check(miner.data().get(2) < 100, "A tool swap kept progress " + miner.data().get(2)))
                .thenWaitUntil(() -> check(miner.data().get(2) > 300, "Progress " + miner.data().get(2) + " (" + miner.status() + ")"))
                .thenExecute(() -> miner.setItem(toolSlot, ItemStack.EMPTY))
                .thenExecuteAfter(2, () -> {
                    check(miner.data().get(2) < 100, "Removing the tool kept progress " + miner.data().get(2));
                    check(helper.getBlockState(FRONT).is(Blocks.OBSIDIAN), "Obsidian broke early");
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE)
    public static void minerHoldsItsLootWhileFull(GameTestHelper helper) {
        Owner owner = Owner.random("gwMe");
        MechanicalMinerBlockEntity miner = machine(helper, ModBlocks.MECHANICAL_MINER, owner);
        miner.setItem(WorkerLayout.MECHANICAL_MINER.first(WorkerLayout.Role.TOOL), new ItemStack(Items.DIAMOND_PICKAXE));
        for (int slot = WorkerLayout.MECHANICAL_MINER.firstOutput(); slot < WorkerLayout.MECHANICAL_MINER.size(); slot++) {
            miner.setItem(slot, new ItemStack(Items.BEDROCK, 64));
        }
        helper.setBlock(FRONT, Blocks.STONE);
        TestProtection.power(helper, owner, MechanicalMinerBlockEntity.GP);
        helper.startSequence()
                .thenWaitUntil(() -> check(miner.status() == Status.BLOCKED, "Expected BLOCKED, got " + miner.status()))
                .thenExecute(() -> {
                    check(helper.getBlockState(FRONT).isAir(), "The block was not broken");
                    check(miner.pendingDrops().size() == 1 && miner.pendingDrops().get(0).is(Items.COBBLESTONE),
                            "Held loot: " + miner.pendingDrops());
                    check(miner.saveWithoutMetadata().contains("Pending"), "Held loot is not saved");
                    miner.removeItem(WorkerLayout.MECHANICAL_MINER.firstOutput(), 64);
                })
                .thenWaitUntil(() -> check(output(miner, WorkerLayout.MECHANICAL_MINER, Items.COBBLESTONE) == 1
                        && miner.pendingDrops().isEmpty(), "Held loot not delivered"))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE)
    public static void minerNeedsOwnerPowerAndNoSignal(GameTestHelper helper) {
        MechanicalMinerBlockEntity unowned = machine(helper, ModBlocks.MECHANICAL_MINER, null);
        helper.setBlock(FRONT, Blocks.DIRT);
        helper.startSequence()
                .thenIdle(30)
                .thenExecute(() -> {
                    check(unowned.status() == Status.NO_OWNER && helper.getBlockState(FRONT).is(Blocks.DIRT), "Unowned miner: " + unowned.status());
                    unowned.setOwner(Owner.random("gwMd").id(), "gwMd");
                })
                .thenIdle(30)
                .thenExecute(() -> {
                    check(unowned.status() == Status.OVERLOADED && helper.getBlockState(FRONT).is(Blocks.DIRT), "Unpowered miner: " + unowned.status());
                    helper.setBlock(MACHINE.west(), Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(2)
                .thenExecute(() -> check(unowned.status() == Status.PAUSED && unowned.demand() == 0, "Redstone did not pause: " + unowned.status()))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE)
    public static void userPlacesBlocks(GameTestHelper helper) {
        Owner owner = Owner.random("gwUa");
        MechanicalUserBlockEntity user = machine(helper, ModBlocks.MECHANICAL_USER, owner);
        int input = WorkerLayout.MECHANICAL_USER.first(WorkerLayout.Role.INPUT);
        user.setItem(input, new ItemStack(Items.COBBLESTONE, 4));
        TestProtection.power(helper, owner, MechanicalUserBlockEntity.GP);
        helper.startSequence()
                .thenWaitUntil(() -> check(helper.getBlockState(FRONT).is(Blocks.COBBLESTONE), "Nothing placed (" + user.status() + ")"))
                .thenExecute(() -> check(user.getItem(input).is(Items.COBBLESTONE) && user.getItem(input).getCount() == 3,
                        "Input after placing: " + user.getItem(input)))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE)
    public static void userFillsBuckets(GameTestHelper helper) {
        Owner owner = Owner.random("gwUb");
        MechanicalUserBlockEntity user = machine(helper, ModBlocks.MECHANICAL_USER, owner);
        int input = WorkerLayout.MECHANICAL_USER.first(WorkerLayout.Role.INPUT);
        for (Direction side : Direction.values()) {
            if (side != Direction.WEST) helper.setBlock(FRONT.relative(side), Blocks.GLASS);
        }
        helper.setBlock(FRONT, Blocks.WATER);
        user.setItem(input, new ItemStack(Items.BUCKET, 2));
        TestProtection.power(helper, owner, MechanicalUserBlockEntity.GP);
        helper.startSequence()
                .thenWaitUntil(() -> check(output(user, WorkerLayout.MECHANICAL_USER, Items.WATER_BUCKET) == 1,
                        "No water bucket in the output (" + user.status() + ")"))
                .thenExecute(() -> {
                    check(helper.getBlockState(FRONT).isAir(), "Water source not taken");
                    check(user.getItem(input).is(Items.BUCKET) && user.getItem(input).getCount() == 1, "Input: " + user.getItem(input));
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE)
    public static void userRespectsProtection(GameTestHelper helper) {
        Owner owner = Owner.random("gwUc");
        MechanicalUserBlockEntity user = machine(helper, ModBlocks.MECHANICAL_USER, owner);
        int input = WorkerLayout.MECHANICAL_USER.first(WorkerLayout.Role.INPUT);
        user.setItem(input, new ItemStack(Items.COBBLESTONE, 4));
        TestProtection.protect(helper, FRONT);
        TestProtection.power(helper, owner, MechanicalUserBlockEntity.GP);
        helper.startSequence()
                .thenWaitUntil(() -> check(user.status() == Status.PROTECTED, "Expected PROTECTED, got " + user.status()))
                .thenExecute(() -> {
                    check(helper.getBlockState(FRONT).isAir(), "Placed into a protected block");
                    check(user.getItem(input).getCount() == 4, "Used up an item it was refused: " + user.getItem(input));
                })
                .thenSucceed();
    }

    /** A claim that refuses only the click (breaking allowed) must not be bypassed by using the item in the air. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void userStopsWhenTheClickIsRefused(GameTestHelper helper) {
        Owner owner = Owner.random("gwUd");
        MechanicalUserBlockEntity user = machine(helper, ModBlocks.MECHANICAL_USER, owner);
        int input = WorkerLayout.MECHANICAL_USER.first(WorkerLayout.Role.INPUT);
        for (Direction side : Direction.values()) {
            if (side != Direction.WEST) helper.setBlock(FRONT.relative(side), Blocks.GLASS);
        }
        helper.setBlock(FRONT, Blocks.WATER);
        user.setItem(input, new ItemStack(Items.BUCKET));
        TestProtection.denyUse(helper, FRONT);
        TestProtection.power(helper, owner, MechanicalUserBlockEntity.GP);
        helper.startSequence()
                .thenWaitUntil(() -> check(user.status() == Status.PROTECTED, "Expected PROTECTED, got " + user.status()))
                .thenExecute(() -> {
                    check(helper.getBlockState(FRONT).is(Blocks.WATER), "Took the water through a refused click");
                    check(user.getItem(input).is(Items.BUCKET), "Input after refusal: " + user.getItem(input));
                })
                .thenSucceed();
    }

    /** Item cooldowns run out for machines' fake players, which the server never ticks. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void userCooldownsRunOut(GameTestHelper helper) {
        Owner owner = Owner.random("gwUe");
        MechanicalUserBlockEntity user = machine(helper, ModBlocks.MECHANICAL_USER, owner);
        int input = WorkerLayout.MECHANICAL_USER.first(WorkerLayout.Role.INPUT);
        user.setItem(input, new ItemStack(Items.ENDER_PEARL, 3));
        // A wall a block beyond the air in front catches the pearls, so none land in other tests' arenas.
        helper.setBlock(FRONT.east(), Blocks.STONE);
        TestProtection.power(helper, owner, MechanicalUserBlockEntity.GP);
        helper.startSequence()
                .thenWaitUntil(() -> check(user.getItem(input).getCount() <= 1,
                        "Pearls left: " + user.getItem(input).getCount() + " (" + user.status() + ")"))
                .thenSucceed();
    }

    /** An item used in the air must not reach past the block in front into a protected one. */
    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void userCannotReachPastIntoProtection(GameTestHelper helper) {
        Owner owner = Owner.random("gwUf");
        MechanicalUserBlockEntity user = machine(helper, ModBlocks.MECHANICAL_USER, owner);
        int input = WorkerLayout.MECHANICAL_USER.first(WorkerLayout.Role.INPUT);
        BlockPos beyond = FRONT.east();
        for (BlockPos pos : List.of(FRONT, beyond)) {
            for (Direction side : Direction.values()) {
                BlockPos next = pos.relative(side);
                if (!next.equals(MACHINE) && !next.equals(FRONT) && !next.equals(beyond)) helper.setBlock(next, Blocks.GLASS);
            }
        }
        helper.setBlock(beyond, Blocks.WATER);
        user.setItem(input, new ItemStack(Items.BUCKET));
        TestProtection.protect(helper, beyond);
        TestProtection.power(helper, owner, MechanicalUserBlockEntity.GP);
        helper.startSequence()
                .thenWaitUntil(() -> check(user.status() == Status.PROTECTED, "Expected PROTECTED, got " + user.status()))
                .thenExecute(() -> {
                    check(helper.getBlockState(beyond).is(Blocks.WATER) && helper.getBlockState(beyond).getFluidState().isSource(),
                            "Took the protected water source behind the block in front");
                    check(user.getItem(input).is(Items.BUCKET), "Input after refusal: " + user.getItem(input));
                })
                .thenSucceed();
    }
}

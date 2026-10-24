package ports.gridwright.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.material.Fluids;
import ports.gridwright.grid.ProducerBlockEntity;
import ports.gridwright.machine.ResonatorBlockEntity;
import ports.gridwright.registry.ModBlocks;
import ports.gridwright.registry.ModItems;
import ports.gridwright.transfer.FilterMenu;
import ports.gridwright.transfer.ItemFilter;
import ports.gridwright.transfer.NodeBlock;
import ports.gridwright.transfer.NodeBlockEntity;
import ports.gridwright.transfer.NodeBlockEntity.Power;
import ports.gridwright.transfer.NodeKind;
import ports.gridwright.transfer.NodeMenu;
import ports.gridwright.transfer.PipeNetwork;
import ports.gridwright.transfer.TransferPipeBlock;
import ports.gridwright.transfer.TransferRegistry;
import ports.gridwright.transfer.UpgradeKind;

import java.util.List;

import static ports.gridwright.gametest.GameTestSupport.Owner;
import static ports.gridwright.gametest.GameTestSupport.TEMPLATE;
import static ports.gridwright.gametest.GameTestSupport.check;
import static ports.gridwright.gametest.GameTestSupport.owned;
import static ports.gridwright.gametest.GameTestSupport.player;
import static ports.gridwright.gametest.GameTestSupport.producer;
import static ports.gridwright.gametest.GameTestSupport.snapshot;
import static ports.gridwright.gametest.GameTestSupport.waterColumn;

/**
 * Transfer nodes, pipes, upgrades and filters. Most tests use one line along x at z = 1: the block
 * the node serves at x = 1, the node at x = 2 facing west, pipes from x = 3 and a target at the end.
 * Tests that need Grid Power add a two-sided water mill (6 GP) at the back of the arena.
 */
public class TransferGameTests {

    private static final BlockPos FRONT = new BlockPos(1, 2, 1);
    private static final BlockPos NODE = new BlockPos(2, 2, 1);
    private static final BlockPos PIPE = new BlockPos(3, 2, 1);
    private static final BlockPos END = new BlockPos(4, 2, 1);
    private static final BlockPos MILL = new BlockPos(3, 2, 6);

    private static TransferTestPlatform fixtures() {
        return TransferTestPlatform.Holder.get();
    }

    private static NodeBlockEntity node(GameTestHelper helper, NodeKind kind, BlockPos pos, Direction facing) {
        helper.setBlock(pos, TransferRegistry.NODES.get(kind).get().defaultBlockState().setValue(NodeBlock.FACING, facing));
        check(helper.getBlockEntity(pos) instanceof NodeBlockEntity, "No node at " + pos);
        return (NodeBlockEntity) helper.getBlockEntity(pos);
    }

    private static void pipe(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, TransferRegistry.PIPE.get());
    }

    private static Container chest(GameTestHelper helper, BlockPos pos, ItemStack... contents) {
        helper.setBlock(pos, Blocks.CHEST);
        Container chest = (Container) helper.getBlockEntity(pos);
        for (int i = 0; i < contents.length; i++) chest.setItem(i, contents[i]);
        return chest;
    }

    private static TransferTestPlatform.TankAccess tank(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, Blocks.JUKEBOX);
        return fixtures().tank(helper.getBlockEntity(pos));
    }

    private static int count(Container container, Item item) {
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (container.getItem(i).is(item)) total += container.getItem(i).getCount();
        }
        return total;
    }

    private static ItemStack upgrade(UpgradeKind kind, int count) {
        return new ItemStack(TransferRegistry.UPGRADES.get(kind).get(), count);
    }

    private static ItemStack filter(boolean blacklist, boolean matchNbt, ItemStack... entries) {
        ItemStack filter = new ItemStack(TransferRegistry.FILTER.get());
        ItemFilter.write(filter, List.of(entries), blacklist, matchNbt);
        return filter;
    }

    /** A two-sided water mill owned by {@code owner}: 6 GP once it has measured its water. */
    private static ProducerBlockEntity mill(GameTestHelper helper, Owner owner) {
        ProducerBlockEntity mill = producer(helper, ModBlocks.WATER_MILL, MILL, owner);
        waterColumn(helper, MILL, Direction.EAST);
        waterColumn(helper, MILL, Direction.WEST);
        return mill;
    }

    /** Seals {@code pos} with glass on every side except towards the node. */
    private static void enclose(GameTestHelper helper, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos side = pos.relative(direction);
            if (!side.equals(NODE)) helper.setBlock(side, Blocks.GLASS);
        }
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void itemNodeMovesOneItemPerSecond(GameTestHelper helper) {
        Container source = chest(helper, FRONT, new ItemStack(Items.COBBLESTONE, 10));
        NodeBlockEntity node = node(helper, NodeKind.ITEM_TRANSFER, NODE, Direction.WEST);
        pipe(helper, PIPE);
        Container target = chest(helper, END);
        helper.startSequence()
                .thenIdle(50)
                .thenExecute(() -> {
                    int moved = count(target, Items.COBBLESTONE);
                    check(moved >= 1 && moved <= 3, "Expected about one item per second, moved " + moved);
                    check(count(source, Items.COBBLESTONE) + moved == 10, "Items were lost or duplicated");
                    check(node.power() == Power.NONE, "A node without upgrades draws nothing: " + node.power());
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE)
    public static void pipesShowConnections(GameTestHelper helper) {
        pipe(helper, PIPE);
        chest(helper, END);
        node(helper, NodeKind.ITEM_TRANSFER, NODE, Direction.WEST);
        // A node facing the pipe serves it, so the pipe does not join that node.
        node(helper, NodeKind.ITEM_TRANSFER, PIPE.south(), Direction.NORTH);
        pipe(helper, PIPE.above());
        BlockState state = helper.getBlockState(PIPE);
        check(state.getValue(TransferPipeBlock.SIDES.get(Direction.EAST)), "Pipe should join the chest");
        check(state.getValue(TransferPipeBlock.SIDES.get(Direction.WEST)), "Pipe should join the node's back");
        check(state.getValue(TransferPipeBlock.SIDES.get(Direction.UP)), "Pipe should join the pipe above");
        check(!state.getValue(TransferPipeBlock.SIDES.get(Direction.SOUTH)), "Pipe must not join a node facing it");
        check(!state.getValue(TransferPipeBlock.SIDES.get(Direction.NORTH)), "Nothing to the north");
        List<PipeNetwork.Target> targets = PipeNetwork.targets(helper.getLevel(), helper.absolutePos(NODE), Direction.WEST, false);
        check(targets.size() == 1 && targets.get(0).pos().equals(helper.absolutePos(END)), "Targets " + targets);
        check(PipeNetwork.targets(helper.getLevel(), helper.absolutePos(NODE), Direction.WEST, true).isEmpty(), "A chest is no fluid target");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void roundRobinAcrossTargets(GameTestHelper helper) {
        Container source = chest(helper, FRONT, new ItemStack(Items.COBBLESTONE, 4));
        node(helper, NodeKind.ITEM_TRANSFER, NODE, Direction.WEST);
        pipe(helper, PIPE);
        pipe(helper, END);
        Container north = chest(helper, END.north());
        Container south = chest(helper, END.south());
        helper.succeedWhen(() -> {
            check(source.isEmpty(), "Source not empty yet");
            check(count(north, Items.COBBLESTONE) == 2 && count(south, Items.COBBLESTONE) == 2,
                    "Expected 2 + 2, got " + count(north, Items.COBBLESTONE) + " + " + count(south, Items.COBBLESTONE));
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void retrievalNodePullsFromNetwork(GameTestHelper helper) {
        Container served = chest(helper, FRONT);
        node(helper, NodeKind.ITEM_RETRIEVAL, NODE, Direction.WEST);
        pipe(helper, PIPE);
        Container network = chest(helper, END, new ItemStack(Items.IRON_INGOT, 2));
        helper.succeedWhen(() -> {
            check(count(served, Items.IRON_INGOT) == 2, "Retrieved " + count(served, Items.IRON_INGOT));
            check(network.isEmpty(), "Network chest still holds items");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void rejectedItemsDoNotStallTheNode(GameTestHelper helper) {
        Container source = chest(helper, FRONT, new ItemStack(Items.COBBLESTONE, 4), new ItemStack(Items.REDSTONE, 2));
        node(helper, NodeKind.ITEM_TRANSFER, NODE, Direction.WEST);
        pipe(helper, PIPE);
        // An unowned resonator never runs, and only takes recipe inputs such as redstone.
        ResonatorBlockEntity resonator = GameTestSupport.resonator(helper, END, null);
        helper.succeedWhen(() -> {
            check(resonator.getItem(0).is(Items.REDSTONE) && resonator.getItem(0).getCount() == 2,
                    "Resonator input " + resonator.getItem(0));
            check(count(source, Items.COBBLESTONE) == 4, "Cobblestone should stay put");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void lockedSlotsDoNotHideOutput(GameTestHelper helper) {
        ResonatorBlockEntity resonator = GameTestSupport.resonator(helper, FRONT, null);
        resonator.setItem(0, new ItemStack(Items.REDSTONE, 5));
        resonator.setItem(1, new ItemStack(ModItems.GRID_CRYSTAL.get(), 2));
        node(helper, NodeKind.ITEM_TRANSFER, NODE, Direction.WEST);
        pipe(helper, PIPE);
        Container target = chest(helper, END);
        helper.succeedWhen(() -> {
            check(count(target, ModItems.GRID_CRYSTAL.get()) == 2, "Collected " + count(target, ModItems.GRID_CRYSTAL.get()));
            check(resonator.getItem(0).getCount() == 5 && count(target, Items.REDSTONE) == 0, "Inputs must stay in the resonator");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void filtersAllowAndDeny(GameTestHelper helper) {
        BlockPos offset = new BlockPos(0, 0, 3);
        Container allowSource = chest(helper, FRONT, new ItemStack(Items.DIRT, 2), new ItemStack(Items.COBBLESTONE, 2));
        NodeBlockEntity allow = node(helper, NodeKind.ITEM_TRANSFER, NODE, Direction.WEST);
        allow.inventory().setItem(NodeBlockEntity.FILTER_SLOT, filter(false, false, new ItemStack(Items.COBBLESTONE)));
        pipe(helper, PIPE);
        Container allowTarget = chest(helper, END);
        chest(helper, FRONT.offset(offset), new ItemStack(Items.DIRT, 2), new ItemStack(Items.COBBLESTONE, 2));
        NodeBlockEntity deny = node(helper, NodeKind.ITEM_TRANSFER, NODE.offset(offset), Direction.WEST);
        deny.inventory().setItem(NodeBlockEntity.FILTER_SLOT, filter(true, false, new ItemStack(Items.COBBLESTONE)));
        pipe(helper, PIPE.offset(offset));
        Container denyTarget = chest(helper, END.offset(offset));
        helper.startSequence()
                .thenWaitUntil(() -> {
                    check(count(allowTarget, Items.COBBLESTONE) == 2, "Allow list moved " + count(allowTarget, Items.COBBLESTONE));
                    check(count(denyTarget, Items.DIRT) == 2, "Deny list moved " + count(denyTarget, Items.DIRT));
                })
                .thenIdle(45)
                .thenExecute(() -> {
                    check(count(allowTarget, Items.DIRT) == 0 && count(allowSource, Items.DIRT) == 2, "Allow list let dirt through");
                    check(count(denyTarget, Items.COBBLESTONE) == 0, "Deny list let cobblestone through");
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE)
    public static void filterMatchesNbtAndFluids(GameTestHelper helper) {
        ItemStack named = new ItemStack(Items.DIAMOND);
        named.setHoverName(Component.literal("Shiny"));
        ItemStack plain = new ItemStack(Items.DIAMOND);
        check(ItemFilter.of(filter(false, false, named)).test(plain), "Without NBT matching any diamond is listed");
        check(!ItemFilter.of(filter(false, true, named)).test(plain), "With NBT matching a plain diamond is not listed");
        check(ItemFilter.of(filter(false, true, named)).test(named.copy()), "With NBT matching the named diamond is listed");
        check(!ItemFilter.of(filter(false, false)).test(plain), "An empty allow list allows nothing");
        check(ItemFilter.of(new ItemStack(Items.PAPER)).test(plain), "No filter lets everything through");
        ItemFilter water = ItemFilter.of(filter(false, false, new ItemStack(Items.WATER_BUCKET)));
        check(water.testFluid(Fluids.WATER) && !water.testFluid(Fluids.LAVA), "A water bucket lists water only");
        ItemFilter noWater = ItemFilter.of(filter(true, false, new ItemStack(Items.WATER_BUCKET)));
        check(!noWater.testFluid(Fluids.WATER) && noWater.testFluid(Fluids.LAVA), "A denied water bucket denies water only");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void filterMenuEditsHeldFilter(GameTestHelper helper) {
        ServerPlayer player = player(helper, Owner.random("gwF"));
        ItemStack held = new ItemStack(TransferRegistry.FILTER.get());
        player.getInventory().setItem(0, held);
        player.getInventory().selected = 0;
        player.getInventory().setItem(5, new ItemStack(Items.EMERALD, 3));
        FilterMenu menu = new FilterMenu(0, player.getInventory(), 0);
        int hotbarStart = ItemFilter.SIZE + 27;

        menu.setCarried(new ItemStack(Items.DIAMOND, 5));
        menu.clicked(0, 0, ClickType.PICKUP, player);
        check(menu.ghost(0).is(Items.DIAMOND) && menu.ghost(0).getCount() == 1, "Ghost slot " + menu.ghost(0));
        check(menu.getCarried().getCount() == 5, "A ghost slot must not take the carried stack");
        menu.setCarried(ItemStack.EMPTY);

        menu.quickMoveStack(player, hotbarStart + 5);
        check(menu.ghost(1).is(Items.EMERALD), "Shift-click should list the emerald: " + menu.ghost(1));
        check(player.getInventory().getItem(5).getCount() == 3, "Shift-click must not move the emeralds");

        check(menu.clickMenuButton(player, FilterMenu.BUTTON_BLACKLIST), "Blacklist button");
        check(menu.clickMenuButton(player, FilterMenu.BUTTON_MATCH_NBT), "NBT button");

        // The filter being edited stays put.
        menu.clicked(hotbarStart, 0, ClickType.PICKUP, player);
        menu.clicked(hotbarStart + 3, 0, ClickType.SWAP, player);
        check(player.getInventory().getItem(0) == held && menu.getCarried().isEmpty(), "The edited filter moved");

        ItemFilter saved = ItemFilter.of(player.getInventory().getItem(0));
        check(saved.size() == 2 && saved.blacklist() && saved.matchNbt(), "Saved filter: size " + saved.size()
                + ", blacklist " + saved.blacklist() + ", nbt " + saved.matchNbt());
        check(!saved.test(new ItemStack(Items.DIAMOND)) && saved.test(new ItemStack(Items.DIRT)), "Saved filter behaviour");

        menu.clicked(0, 0, ClickType.PICKUP, player);
        check(menu.ghost(0).isEmpty() && ItemFilter.of(held).size() == 1, "Clicking with an empty hand clears a ghost slot");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void upgradesNeedOwnerAndPower(GameTestHelper helper) {
        BlockPos offset = new BlockPos(0, 0, 3);
        chest(helper, FRONT, new ItemStack(Items.COBBLESTONE, 64));
        NodeBlockEntity unowned = node(helper, NodeKind.ITEM_TRANSFER, NODE, Direction.WEST);
        unowned.inventory().setItem(0, upgrade(UpgradeKind.SPEED, 4));
        pipe(helper, PIPE);
        Container unownedTarget = chest(helper, END);
        Owner owner = Owner.random("gwU");
        chest(helper, FRONT.offset(offset), new ItemStack(Items.COBBLESTONE, 64));
        NodeBlockEntity unpowered = owned(node(helper, NodeKind.ITEM_TRANSFER, NODE.offset(offset), Direction.WEST), owner);
        unpowered.inventory().setItem(0, upgrade(UpgradeKind.SPEED, 4));
        pipe(helper, PIPE.offset(offset));
        Container unpoweredTarget = chest(helper, END.offset(offset));
        helper.startSequence()
                .thenIdle(50)
                .thenExecute(() -> {
                    check(unowned.power() == Power.NO_OWNER, "Unowned node: " + unowned.power());
                    check(unpowered.power() == Power.UNPOWERED, "Owned node on an empty grid: " + unpowered.power());
                    int a = count(unownedTarget, Items.COBBLESTONE);
                    int b = count(unpoweredTarget, Items.COBBLESTONE);
                    check(a >= 1 && a <= 3 && b >= 1 && b <= 3, "Unpowered upgrades must not speed up: " + a + ", " + b);
                    check(snapshot(helper, owner.gridKey()).demand() == 1.0, "Four speed upgrades ask for 1 GP");
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 500)
    public static void poweredSpeedUpgradesMoveFaster(GameTestHelper helper) {
        Owner owner = Owner.random("gwS");
        ProducerBlockEntity mill = mill(helper, owner);
        chest(helper, FRONT, new ItemStack(Items.COBBLESTONE, 64));
        NodeBlockEntity node = owned(node(helper, NodeKind.ITEM_TRANSFER, NODE, Direction.WEST), owner);
        node.inventory().setItem(0, upgrade(UpgradeKind.SPEED, 4));
        pipe(helper, PIPE);
        Container target = chest(helper, END);
        int[] start = new int[1];
        helper.startSequence()
                .thenWaitUntil(() -> {
                    check(mill.output() >= 6.0 - 1e-9, "Mill output " + mill.output());
                    check(node.power() == Power.POWERED, "Node power " + node.power());
                })
                .thenExecute(() -> start[0] = count(target, Items.COBBLESTONE))
                .thenIdle(40)
                .thenExecute(() -> {
                    int moved = count(target, Items.COBBLESTONE) - start[0];
                    check(moved >= 8 && moved <= 12, "Five operations per second should move about 10 in 40 ticks, moved " + moved);
                    var grid = snapshot(helper, owner.gridKey());
                    check(Math.abs(grid.demand() - 1.0) < 1e-9 && grid.consumers() == 1, "Grid " + grid);
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 500)
    public static void stackUpgradeMovesStacks(GameTestHelper helper) {
        Owner owner = Owner.random("gwT");
        mill(helper, owner);
        chest(helper, FRONT, new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.COBBLESTONE, 64));
        NodeBlockEntity node = owned(node(helper, NodeKind.ITEM_TRANSFER, NODE, Direction.WEST), owner);
        node.inventory().setItem(0, upgrade(UpgradeKind.STACK, 2));
        pipe(helper, PIPE);
        Container target = chest(helper, END);
        long[] start = new long[2];
        helper.startSequence()
                .thenWaitUntil(() -> check(node.power() == Power.POWERED, "Node power " + node.power()))
                .thenExecute(() -> {
                    start[0] = node.operations();
                    start[1] = count(target, Items.COBBLESTONE);
                })
                .thenWaitUntil(() -> check(count(target, Items.COBBLESTONE) == 128, "Moved " + count(target, Items.COBBLESTONE)))
                .thenExecute(() -> {
                    check(node.upgrades(UpgradeKind.STACK) == 1, "Only one stack upgrade counts");
                    long operations = node.operations() - start[0];
                    long expected = (128 - start[1] + 63) / 64;
                    check(operations <= expected, "Powered stack moves took " + operations + " operations, expected " + expected);
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void fluidNodesMoveFluid(GameTestHelper helper) {
        BlockPos offset = new BlockPos(0, 0, 3);
        TransferTestPlatform.TankAccess source = tank(helper, FRONT);
        source.set(Fluids.WATER, 1000);
        node(helper, NodeKind.FLUID_TRANSFER, NODE, Direction.WEST);
        pipe(helper, PIPE);
        TransferTestPlatform.TankAccess target = tank(helper, END);
        TransferTestPlatform.TankAccess served = tank(helper, FRONT.offset(offset));
        node(helper, NodeKind.FLUID_RETRIEVAL, NODE.offset(offset), Direction.WEST);
        pipe(helper, PIPE.offset(offset));
        TransferTestPlatform.TankAccess network = tank(helper, END.offset(offset));
        network.set(Fluids.LAVA, 250);
        helper.startSequence()
                .thenIdle(50)
                .thenExecute(() -> {
                    check(target.fluid() == Fluids.WATER, "Target fluid " + target.fluid());
                    check(target.millibuckets() >= 100 && target.millibuckets() <= 300, "Moved " + target.millibuckets() + " mB");
                    check(source.millibuckets() + target.millibuckets() == 1000, "Fluid was lost or made");
                    check(served.fluid() == Fluids.LAVA && served.millibuckets() >= 100, "Retrieved " + served.millibuckets() + " mB");
                    check(served.millibuckets() + network.millibuckets() == 250, "Retrieved fluid was lost or made");
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 500)
    public static void worldInteractionMinesIntoNetwork(GameTestHelper helper) {
        Owner owner = Owner.random("gwW");
        mill(helper, owner);
        helper.setBlock(FRONT, Blocks.STONE);
        NodeBlockEntity node = owned(node(helper, NodeKind.ITEM_TRANSFER, NODE, Direction.WEST), owner);
        node.inventory().setItem(0, upgrade(UpgradeKind.WORLD_INTERACTION, 1));
        pipe(helper, PIPE);
        Container target = chest(helper, END);
        helper.succeedWhen(() -> {
            check(helper.getBlockState(FRONT).isAir(), "Stone not mined; power " + node.power());
            check(count(target, Items.COBBLESTONE) == 1, "Expected the cobblestone drop in the chest");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 500)
    public static void worldInteractionRespectsProtection(GameTestHelper helper) {
        Owner owner = Owner.random("gwP");
        mill(helper, owner);
        helper.setBlock(FRONT, Blocks.STONE);
        BlockPos protectedPos = helper.absolutePos(FRONT);
        TransferTestPlatform.PROTECTED.add(protectedPos);
        NodeBlockEntity node = owned(node(helper, NodeKind.ITEM_TRANSFER, NODE, Direction.WEST), owner);
        node.inventory().setItem(0, upgrade(UpgradeKind.WORLD_INTERACTION, 1));
        pipe(helper, PIPE);
        Container target = chest(helper, END);
        BlockPos offset = new BlockPos(0, 0, 3);
        helper.setBlock(FRONT.offset(offset), Blocks.STONE);
        NodeBlockEntity unowned = node(helper, NodeKind.ITEM_TRANSFER, NODE.offset(offset), Direction.WEST);
        unowned.inventory().setItem(0, upgrade(UpgradeKind.WORLD_INTERACTION, 1));
        pipe(helper, PIPE.offset(offset));
        chest(helper, END.offset(offset));
        helper.startSequence()
                .thenWaitUntil(() -> check(node.power() == Power.POWERED, "Node power " + node.power()))
                .thenExecuteFor(60, () -> {
                    check(helper.getBlockState(FRONT).is(Blocks.STONE), "Mined a protected block");
                    check(target.isEmpty(), "Protected block's drops arrived");
                    check(unowned.power() == Power.NO_OWNER, "Unowned node: " + unowned.power());
                    check(helper.getBlockState(FRONT.offset(offset)).is(Blocks.STONE), "An unowned node mined");
                })
                .thenExecute(() -> TransferTestPlatform.PROTECTED.remove(protectedPos))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 500)
    public static void worldInteractionPlacesBlocks(GameTestHelper helper) {
        Owner owner = Owner.random("gwL");
        mill(helper, owner);
        NodeBlockEntity node = owned(node(helper, NodeKind.ITEM_RETRIEVAL, NODE, Direction.WEST), owner);
        node.inventory().setItem(0, upgrade(UpgradeKind.WORLD_INTERACTION, 1));
        pipe(helper, PIPE);
        Container network = chest(helper, END, new ItemStack(Items.STICK, 4), new ItemStack(Items.COBBLESTONE, 3));
        helper.succeedWhen(() -> {
            check(helper.getBlockState(FRONT).is(Blocks.COBBLESTONE), "Nothing placed; power " + node.power());
            check(count(network, Items.COBBLESTONE) == 2 && count(network, Items.STICK) == 4, "Network chest contents");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 600)
    public static void worldInteractionMinesOnlyWhenEveryDropFits(GameTestHelper helper) {
        Owner owner = Owner.random("gwF");
        mill(helper, owner);
        // A potted dandelion always drops the pot and the flower: two kinds that need two free slots.
        // (Not ripe wheat: crops pop off on the next shape update when the arena is dark, which on
        // Forge's underground GameTest arenas raced the light engine and made this test flaky.)
        helper.setBlock(FRONT, Blocks.POTTED_DANDELION);
        NodeBlockEntity node = owned(node(helper, NodeKind.ITEM_TRANSFER, NODE, Direction.WEST), owner);
        node.inventory().setItem(0, upgrade(UpgradeKind.WORLD_INTERACTION, 1));
        pipe(helper, PIPE);
        ItemStack[] full = new ItemStack[26];
        for (int i = 0; i < full.length; i++) full[i] = new ItemStack(Items.STONE, 64);
        Container target = chest(helper, END, full);
        helper.startSequence()
                .thenWaitUntil(() -> check(node.power() == Power.POWERED, "Node power " + node.power()))
                .thenExecuteFor(60, () -> {
                    check(helper.getBlockState(FRONT).is(Blocks.POTTED_DANDELION), "Mined with room for only one of two drops");
                    check(count(target, Items.FLOWER_POT) + count(target, Items.DANDELION) == 0, "A drop arrived early");
                })
                .thenExecute(() -> target.setItem(0, ItemStack.EMPTY))
                .thenWaitUntil(() -> {
                    check(!helper.getBlockState(FRONT).is(Blocks.POTTED_DANDELION), "Potted dandelion not mined once both drops fit");
                    check(count(target, Items.FLOWER_POT) == 1 && count(target, Items.DANDELION) == 1,
                            "Expected the pot and the dandelion in the chest");
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 600)
    public static void worldInteractionCountsDoubleChestSpaceOnce(GameTestHelper helper) {
        Owner owner = Owner.random("gwC");
        mill(helper, owner);
        helper.setBlock(FRONT, Blocks.POTTED_DANDELION);
        NodeBlockEntity node = owned(node(helper, NodeKind.ITEM_TRANSFER, NODE, Direction.WEST), owner);
        node.inventory().setItem(0, upgrade(UpgradeKind.WORLD_INTERACTION, 1));
        // Pipes touch both halves of one double chest, so the network reaches its 54 slots twice.
        // Both halves go down before the pipes: Forge keeps the handler a chest first hands out, so a
        // half asked for one while still single would only ever offer its own 27 slots.
        BlockPos otherHalf = END.south();
        BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.WEST);
        helper.setBlock(END, chest.setValue(ChestBlock.TYPE, ChestType.RIGHT));
        helper.setBlock(otherHalf, chest.setValue(ChestBlock.TYPE, ChestType.LEFT));
        pipe(helper, PIPE);
        pipe(helper, PIPE.south());
        Container first = (Container) helper.getBlockEntity(END);
        Container second = (Container) helper.getBlockEntity(otherHalf);
        BlockPos absolute = helper.absolutePos(END);
        Container combined = ChestBlock.getContainer((ChestBlock) Blocks.CHEST, helper.getLevel().getBlockState(absolute),
                helper.getLevel(), absolute, true);
        check(combined != null && combined.getContainerSize() == 54, "Not a double chest");
        // One free slot in the whole double chest: room for the pot or the dandelion, not both.
        for (int i = 0; i < 27; i++) first.setItem(i, new ItemStack(Items.STONE, 64));
        for (int i = 1; i < 27; i++) second.setItem(i, new ItemStack(Items.STONE, 64));
        helper.startSequence()
                .thenWaitUntil(() -> check(node.power() == Power.POWERED, "Node power " + node.power()))
                .thenExecuteFor(60, () -> {
                    check(helper.getBlockState(FRONT).is(Blocks.POTTED_DANDELION), "Mined with room for only one of two drops");
                    check(count(first, Items.FLOWER_POT) + count(second, Items.FLOWER_POT) == 0, "A drop arrived early");
                })
                .thenExecute(() -> first.setItem(0, ItemStack.EMPTY))
                .thenWaitUntil(() -> {
                    check(!helper.getBlockState(FRONT).is(Blocks.POTTED_DANDELION), "Potted dandelion not mined once both drops fit");
                    check(count(first, Items.FLOWER_POT) + count(second, Items.FLOWER_POT) == 1
                            && count(first, Items.DANDELION) + count(second, Items.DANDELION) == 1,
                            "Expected the pot and the dandelion in the double chest");
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 500)
    public static void worldInteractionSkipsUnplaceableBlocks(GameTestHelper helper) {
        Owner owner = Owner.random("gwK");
        mill(helper, owner);
        // A sapling cannot stand on stone; it comes first in the chest and must not hide the cobblestone.
        helper.setBlock(FRONT.below(), Blocks.STONE);
        NodeBlockEntity node = owned(node(helper, NodeKind.ITEM_RETRIEVAL, NODE, Direction.WEST), owner);
        node.inventory().setItem(0, upgrade(UpgradeKind.WORLD_INTERACTION, 1));
        pipe(helper, PIPE);
        Container network = chest(helper, END, new ItemStack(Items.OAK_SAPLING, 4), new ItemStack(Items.COBBLESTONE, 3));
        helper.succeedWhen(() -> {
            check(helper.getBlockState(FRONT).is(Blocks.COBBLESTONE), "Nothing placed; power " + node.power());
            check(count(network, Items.COBBLESTONE) == 2 && count(network, Items.OAK_SAPLING) == 4, "Network chest contents");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 500)
    public static void worldInteractionPlacesDirectionalBlocks(GameTestHelper helper) {
        Owner owner = Owner.random("gwD");
        mill(helper, owner);
        NodeBlockEntity node = owned(node(helper, NodeKind.ITEM_RETRIEVAL, NODE, Direction.WEST), owner);
        node.inventory().setItem(0, upgrade(UpgradeKind.WORLD_INTERACTION, 1));
        pipe(helper, PIPE);
        // Pistons ask which way the placer looks; a playerless context must still answer.
        Container network = chest(helper, END, new ItemStack(Items.PISTON, 2));
        helper.succeedWhen(() -> {
            check(helper.getBlockState(FRONT).is(Blocks.PISTON), "No piston placed; power " + node.power());
            check(count(network, Items.PISTON) == 1, "Network chest holds " + count(network, Items.PISTON));
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void sidedInventoriesOfferEveryConnectedFace(GameTestHelper helper) {
        Container source = chest(helper, FRONT, new ItemStack(Items.RAW_IRON, 2));
        node(helper, NodeKind.ITEM_TRANSFER, NODE, Direction.WEST);
        pipe(helper, PIPE);
        // The pipe meets the furnace's side (fuel only) first, and its top (smelting input) by a detour.
        helper.setBlock(END, Blocks.FURNACE);
        pipe(helper, PIPE.above());
        pipe(helper, END.above());
        Container furnace = (Container) helper.getBlockEntity(END);
        helper.succeedWhen(() -> {
            check(furnace.getItem(0).is(Items.RAW_IRON) && furnace.getItem(0).getCount() == 2,
                    "Furnace input " + furnace.getItem(0));
            check(source.isEmpty(), "Source chest still holds raw iron");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 500)
    public static void worldInteractionPumpsSources(GameTestHelper helper) {
        Owner owner = Owner.random("gwQ");
        mill(helper, owner);
        enclose(helper, FRONT);
        helper.setBlock(FRONT, Blocks.WATER);
        NodeBlockEntity node = owned(node(helper, NodeKind.FLUID_TRANSFER, NODE, Direction.WEST), owner);
        node.inventory().setItem(0, upgrade(UpgradeKind.WORLD_INTERACTION, 1));
        pipe(helper, PIPE);
        TransferTestPlatform.TankAccess target = tank(helper, END);
        helper.succeedWhen(() -> {
            check(helper.getBlockState(FRONT).isAir(), "Source not pumped; power " + node.power());
            check(target.fluid() == Fluids.WATER && target.millibuckets() == 1000, "Tank holds " + target.millibuckets());
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 500)
    public static void worldInteractionPoursSources(GameTestHelper helper) {
        Owner owner = Owner.random("gwR");
        mill(helper, owner);
        enclose(helper, FRONT);
        NodeBlockEntity node = owned(node(helper, NodeKind.FLUID_RETRIEVAL, NODE, Direction.WEST), owner);
        node.inventory().setItem(0, upgrade(UpgradeKind.WORLD_INTERACTION, 1));
        pipe(helper, PIPE);
        TransferTestPlatform.TankAccess network = tank(helper, END);
        network.set(Fluids.WATER, 1500);
        helper.succeedWhen(() -> {
            var poured = helper.getLevel().getFluidState(helper.absolutePos(FRONT));
            check(poured.isSource() && poured.getType() == Fluids.WATER, "No water source poured; power " + node.power());
            check(network.millibuckets() == 500, "Tank holds " + network.millibuckets());
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 500)
    public static void worldInteractionPoursFromLaterTanks(GameTestHelper helper) {
        Owner owner = Owner.random("gwT");
        mill(helper, owner);
        enclose(helper, FRONT);
        NodeBlockEntity node = owned(node(helper, NodeKind.FLUID_RETRIEVAL, NODE, Direction.WEST), owner);
        node.inventory().setItem(0, upgrade(UpgradeKind.WORLD_INTERACTION, 1));
        pipe(helper, PIPE);
        // One storage with two tanks: less than a bucket of water listed before a full bucket of lava.
        helper.setBlock(END, Blocks.ENCHANTING_TABLE);
        TransferTestPlatform.TankAccess water = fixtures().multiTank(helper.getBlockEntity(END), 0);
        TransferTestPlatform.TankAccess lava = fixtures().multiTank(helper.getBlockEntity(END), 1);
        water.set(Fluids.WATER, 100);
        lava.set(Fluids.LAVA, 1000);
        helper.succeedWhen(() -> {
            var poured = helper.getLevel().getFluidState(helper.absolutePos(FRONT));
            check(poured.isSource() && poured.getType() == Fluids.LAVA, "No lava source poured; power " + node.power());
            check(lava.millibuckets() == 0 && water.millibuckets() == 100,
                    "Tanks hold " + water.millibuckets() + " mB water, " + lava.millibuckets() + " mB lava");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 500)
    public static void worldInteractionStaysInsideBuildHeight(GameTestHelper helper) {
        Owner owner = Owner.random("gwH");
        mill(helper, owner);
        // The same line, but on the top buildable layer above the arena with the node facing up, out of the world.
        BlockPos column = helper.absolutePos(NODE);
        BlockPos top = helper.relativePos(new BlockPos(column.getX(), helper.getLevel().getMaxBuildHeight() - 1, column.getZ()));
        List<BlockPos> line = List.of(top, top.east(), top.east(2));
        NodeBlockEntity node = owned(node(helper, NodeKind.FLUID_RETRIEVAL, top, Direction.UP), owner);
        node.inventory().setItem(0, upgrade(UpgradeKind.WORLD_INTERACTION, 1));
        pipe(helper, top.east());
        TransferTestPlatform.TankAccess network = tank(helper, top.east(2));
        network.set(Fluids.WATER, 1500);
        helper.startSequence()
                .thenWaitUntil(() -> check(node.power() == Power.POWERED, "Node power " + node.power()))
                .thenExecuteFor(60, () -> check(network.millibuckets() == 1500, "Pouring out of the world used fluid: "
                        + network.millibuckets()))
                .thenExecute(() -> line.forEach(pos -> helper.setBlock(pos, Blocks.AIR)))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE)
    public static void nodeMenuSortsShiftClicks(GameTestHelper helper) {
        NodeBlockEntity node = node(helper, NodeKind.ITEM_TRANSFER, NODE, Direction.WEST);
        ServerPlayer player = player(helper, Owner.random("gwN"));
        player.getInventory().setItem(0, upgrade(UpgradeKind.SPEED, 70));
        player.getInventory().setItem(1, new ItemStack(TransferRegistry.FILTER.get()));
        player.getInventory().setItem(2, new ItemStack(Items.DIRT, 5));
        NodeMenu menu = new NodeMenu(0, player.getInventory(), node.inventory(),
                new SimpleContainerData(NodeBlockEntity.DATA_COUNT), p -> true);
        int hotbarStart = NodeBlockEntity.SLOTS + 27;
        check(!menu.getSlot(NodeBlockEntity.FILTER_SLOT).mayPlace(upgrade(UpgradeKind.SPEED, 1)), "Filter slot took an upgrade");
        check(!menu.getSlot(0).mayPlace(new ItemStack(TransferRegistry.FILTER.get())), "Upgrade slot took a filter");
        menu.clicked(hotbarStart, 0, ClickType.QUICK_MOVE, player);
        menu.clicked(hotbarStart + 1, 0, ClickType.QUICK_MOVE, player);
        menu.clicked(hotbarStart + 2, 0, ClickType.QUICK_MOVE, player);
        check(node.inventory().getItem(0).getCount() == 64 && node.inventory().getItem(1).getCount() == 6, "Upgrades split "
                + node.inventory().getItem(0) + ", " + node.inventory().getItem(1));
        check(node.inventory().getItem(NodeBlockEntity.FILTER_SLOT).getItem() == TransferRegistry.FILTER.get(), "Filter not moved in");
        check(player.getInventory().getItem(2).getCount() == 5, "Dirt has no node slot");
        check(node.upgrades(UpgradeKind.SPEED) == 64, "Speed upgrades count up to 64, got " + node.upgrades(UpgradeKind.SPEED));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void nodeStatePersists(GameTestHelper helper) {
        NodeBlockEntity node = node(helper, NodeKind.ITEM_TRANSFER, NODE, Direction.WEST);
        check(!NodeBlockEntity.accepts(0, new ItemStack(Items.DIRT)), "Upgrade slots take upgrades only");
        check(!NodeBlockEntity.accepts(NodeBlockEntity.FILTER_SLOT, upgrade(UpgradeKind.SPEED, 1)), "The filter slot takes filters only");
        check(NodeBlockEntity.accepts(NodeBlockEntity.FILTER_SLOT, new ItemStack(TransferRegistry.FILTER.get())), "Filter slot");
        node.inventory().setItem(0, upgrade(UpgradeKind.SPEED, 3));
        node.inventory().setItem(1, upgrade(UpgradeKind.STACK, 1));
        node.inventory().setItem(NodeBlockEntity.FILTER_SLOT, filter(true, false, new ItemStack(Items.DIRT)));
        CompoundTag tag = node.saveWithoutMetadata();
        NodeBlockEntity copy = new NodeBlockEntity(helper.absolutePos(NODE), node.getBlockState());
        copy.load(tag);
        check(copy.upgrades(UpgradeKind.SPEED) == 3 && copy.upgrades(UpgradeKind.STACK) == 1, "Upgrades after reload");
        check(Math.abs(copy.upgradeCost() - (3 * UpgradeKind.SPEED.gp() + UpgradeKind.STACK.gp())) < 1e-9, "Cost " + copy.upgradeCost());
        check(copy.filter().blacklist() && !copy.filter().test(new ItemStack(Items.DIRT)), "Filter after reload");
        check(copy.kind() == NodeKind.ITEM_TRANSFER, "Kind " + copy.kind());
        helper.succeed();
    }
}

package ports.gridwright.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import ports.gridwright.item.BiomeMarkerItem;
import ports.gridwright.registry.ModBlocks;
import ports.gridwright.registry.ModItems;
import ports.gridwright.worker.EnderQuarryBlockEntity;
import ports.gridwright.worker.QuantumQuarryBlockEntity;
import ports.gridwright.worker.QuarryArea;
import ports.gridwright.worker.QuarryTable;
import ports.gridwright.worker.Upgrades;
import ports.gridwright.worker.WorkerBlockEntity.Status;
import ports.gridwright.worker.WorkerLayout;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ports.gridwright.gametest.GameTestSupport.Owner;
import static ports.gridwright.gametest.GameTestSupport.TEMPLATE;
import static ports.gridwright.gametest.GameTestSupport.check;
import static ports.gridwright.gametest.GameTestSupport.owned;
import static ports.gridwright.gametest.GameTestSupport.place;
import static ports.gridwright.gametest.GameTestSupport.platform;

/** The quantum quarry's virtual world and the ender quarry's boundary, protection and energy rules. */
public class QuarryGameTests {

    private static final BlockPos QUARRY = new BlockPos(3, 2, 3);

    // ---- Quantum quarry -------------------------------------------------------------------------

    private static QuantumQuarryBlockEntity quantumQuarry(GameTestHelper helper, Owner owner, boolean actuators) {
        QuantumQuarryBlockEntity quarry = owned(place(helper, ModBlocks.QUANTUM_QUARRY, QUARRY), owner);
        if (actuators) {
            for (Direction side : Direction.Plane.HORIZONTAL) helper.setBlock(QUARRY.relative(side), ModBlocks.QUARRY_ACTUATOR.get());
        }
        return quarry;
    }

    private static int upgradeSlot(int index) {
        return WorkerLayout.QUANTUM_QUARRY.first(WorkerLayout.Role.UPGRADE) + index;
    }

    @GameTest(template = TEMPLATE)
    public static void quantumQuarryNeedsActuators(GameTestHelper helper) {
        Owner owner = Owner.random("gwQ");
        QuantumQuarryBlockEntity quarry = quantumQuarry(helper, owner, false);
        TestProtection.power(helper, owner, 1000);
        helper.setBlock(QUARRY.east(), ModBlocks.QUARRY_ACTUATOR.get());
        helper.startSequence()
                .thenIdle(3)
                .thenExecuteFor(10, () -> check(quarry.status() == Status.INCOMPLETE && quarry.mined() == 0 && quarry.demand() == 0,
                        "Expected INCOMPLETE with no draw, got " + quarry.status()))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE)
    public static void quantumQuarryStopsWhenOverloaded(GameTestHelper helper) {
        QuantumQuarryBlockEntity quarry = quantumQuarry(helper, Owner.random("gwR"), true);
        helper.startSequence()
                .thenIdle(3)
                .thenExecuteFor(QuantumQuarryBlockEntity.BASE_TICKS + 5, () -> check(quarry.status() == Status.OVERLOADED
                        && quarry.mined() == 0 && quarry.demand() == QuantumQuarryBlockEntity.BASE_GP, "Expected OVERLOADED, got " + quarry.status()))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void quantumQuarryMinesWithPowerAndUpgrades(GameTestHelper helper) {
        Owner owner = Owner.random("gwS");
        QuantumQuarryBlockEntity quarry = quantumQuarry(helper, owner, true);
        quarry.setItem(upgradeSlot(0), Upgrades.speed(Upgrades.MAX_SPEED));
        Upgrades upgrades = Upgrades.read(quarry, WorkerLayout.QUANTUM_QUARRY);
        check(upgrades.speed() == Upgrades.MAX_SPEED && QuantumQuarryBlockEntity.ticksPerBlock(upgrades) == 4, "Speed " + upgrades);
        double gp = QuantumQuarryBlockEntity.gp(upgrades);
        check(gp == QuantumQuarryBlockEntity.BASE_GP + 8 * QuantumQuarryBlockEntity.GP_PER_UPGRADE, "GP " + gp);
        TestProtection.power(helper, owner, gp);
        helper.setBlock(QUARRY.below(), Blocks.STONE);
        helper.startSequence()
                .thenWaitUntil(() -> check(quarry.mined() >= 10, "Mined only " + quarry.mined() + " (" + quarry.status() + ")"))
                .thenExecute(() -> {
                    check(quarry.status() == Status.RUNNING && quarry.demand() == gp, "Status " + quarry.status() + " at " + quarry.demand());
                    int items = 0;
                    for (int slot = WorkerLayout.QUANTUM_QUARRY.firstOutput(); slot < WorkerLayout.QUANTUM_QUARRY.size(); slot++) {
                        items += quarry.getItem(slot).getCount();
                    }
                    check(items >= 10, "Only " + items + " items in the output");
                    // The world is untouched: the quarry mines a virtual one.
                    check(helper.getBlockState(QUARRY.below()).is(Blocks.STONE), "The quantum quarry mined real terrain");
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE)
    public static void quantumQuarryIsDeterministic(GameTestHelper helper) {
        QuantumQuarryBlockEntity quarry = quantumQuarry(helper, Owner.random("gwT"), true);
        Upgrades none = Upgrades.NONE;
        boolean varied = false;
        List<ItemStack> first = quarry.mine(helper.getLevel(), none, 0);
        for (long index = 0; index < 40; index++) {
            List<ItemStack> a = quarry.mine(helper.getLevel(), none, index);
            List<ItemStack> b = quarry.mine(helper.getLevel(), none, index);
            check(a.size() == b.size(), "Block " + index + " differs between runs");
            for (int i = 0; i < a.size(); i++) check(ItemStack.matches(a.get(i), b.get(i)), "Block " + index + " differs between runs");
            if (a.size() != first.size() || (!a.isEmpty() && !a.get(0).is(first.get(0).getItem()))) varied = true;
        }
        check(varied, "Forty blocks in a row were the same");
        helper.succeed();
    }

    /** Draws many blocks from a table and counts them by block. */
    private static Map<Block, Integer> sample(QuarryTable table, int draws) {
        RandomSource random = RandomSource.create(1234L);
        Map<Block, Integer> counts = new HashMap<>();
        for (int i = 0; i < draws; i++) counts.merge(table.draw(random).getBlock(), 1, Integer::sum);
        return counts;
    }

    @GameTest(template = TEMPLATE)
    public static void quarryTablesFollowTheirRegion(GameTestHelper helper) {
        int draws = 40_000;
        Map<Block, Integer> overworld = sample(QuarryTable.of(QuarryTable.Region.OVERWORLD, QuarryTable.Terrain.NORMAL, List.of()), draws);
        int ores = 0;
        List<TagKey<Block>> oreTags = List.of(BlockTags.COAL_ORES, BlockTags.IRON_ORES, BlockTags.COPPER_ORES, BlockTags.GOLD_ORES,
                BlockTags.REDSTONE_ORES, BlockTags.LAPIS_ORES, BlockTags.DIAMOND_ORES, BlockTags.EMERALD_ORES);
        for (Map.Entry<Block, Integer> entry : overworld.entrySet()) {
            if (oreTags.stream().anyMatch(tag -> entry.getKey().defaultBlockState().is(tag))) ores += entry.getValue();
        }
        double share = (double) ores / draws;
        check(share > 0.02 && share < 0.08, "Overworld ore share " + share + " is outside 2-8%");
        int diamonds = overworld.getOrDefault(Blocks.DIAMOND_ORE, 0) + overworld.getOrDefault(Blocks.DEEPSLATE_DIAMOND_ORE, 0);
        check(diamonds > 0 && diamonds < draws / 500, "Diamonds " + diamonds + " of " + draws);
        check(!overworld.containsKey(Blocks.ANCIENT_DEBRIS) && !overworld.containsKey(Blocks.EMERALD_ORE), "Overworld plains has nether ore or emeralds");
        check(overworld.getOrDefault(Blocks.STONE, 0) + overworld.getOrDefault(Blocks.DEEPSLATE, 0) > draws / 2, "Mostly stone expected");

        Map<Block, Integer> mountain = sample(QuarryTable.of(QuarryTable.Region.OVERWORLD, QuarryTable.Terrain.MOUNTAIN, List.of()), draws);
        check(mountain.getOrDefault(Blocks.EMERALD_ORE, 0) > 0, "Mountains have no emeralds");
        Map<Block, Integer> nether = sample(QuarryTable.of(QuarryTable.Region.NETHER, QuarryTable.Terrain.NORMAL, List.of()), draws);
        check(nether.getOrDefault(Blocks.NETHERRACK, 0) > draws / 2 && nether.getOrDefault(Blocks.NETHER_QUARTZ_ORE, 0) > 0, "Nether table " + nether);
        check(!nether.containsKey(Blocks.STONE) && !nether.containsKey(Blocks.DIAMOND_ORE), "Nether table has overworld blocks");
        check(nether.getOrDefault(Blocks.ANCIENT_DEBRIS, 0) < draws / 1000, "Too much ancient debris");
        Map<Block, Integer> end = sample(QuarryTable.of(QuarryTable.Region.END, QuarryTable.Terrain.NORMAL, List.of()), 1000);
        check(end.keySet().equals(java.util.Set.of(Blocks.END_STONE)), "End table " + end);
        helper.succeed();
    }

    /** Ores found through the ore tags join the table of the region their id names, at a small weight. */
    @GameTest(template = TEMPLATE)
    public static void taggedOresJoinTheirRegion(GameTestHelper helper) {
        List<Block> candidates = List.of(Blocks.AMETHYST_BLOCK, Blocks.DEEPSLATE_TILES, Blocks.NETHER_BRICKS, Blocks.END_STONE_BRICKS,
                Blocks.IRON_ORE);
        QuarryTable overworld = QuarryTable.of(QuarryTable.Region.OVERWORLD, QuarryTable.Terrain.NORMAL, candidates);
        QuarryTable nether = QuarryTable.of(QuarryTable.Region.NETHER, QuarryTable.Terrain.NORMAL, candidates);
        QuarryTable end = QuarryTable.of(QuarryTable.Region.END, QuarryTable.Terrain.NORMAL, candidates);
        check(entry(overworld, Blocks.AMETHYST_BLOCK) != null && entry(overworld, Blocks.AMETHYST_BLOCK).minY() == 0, "Shallow modded ore");
        check(entry(overworld, Blocks.DEEPSLATE_TILES) != null && entry(overworld, Blocks.DEEPSLATE_TILES).maxY() < 0, "Deep modded ore");
        check(entry(overworld, Blocks.NETHER_BRICKS) == null && entry(nether, Blocks.NETHER_BRICKS) != null, "Nether ore in the wrong region");
        check(entry(end, Blocks.END_STONE_BRICKS) != null && entry(overworld, Blocks.END_STONE_BRICKS) == null, "End ore in the wrong region");
        check(entry(overworld, Blocks.AMETHYST_BLOCK).weight() == QuarryTable.MODDED_WEIGHT, "Modded weight");
        long ironEntries = overworld.entries().stream().filter(e -> e.state().is(Blocks.IRON_ORE)).count();
        check(ironEntries == 1, "Vanilla ore added twice: " + ironEntries);
        helper.succeed();
    }

    private static QuarryTable.Entry entry(QuarryTable table, Block block) {
        return table.entries().stream().filter(e -> e.state().is(block)).findFirst().orElse(null);
    }

    @GameTest(template = TEMPLATE)
    public static void biomeMarkerPicksTheTable(GameTestHelper helper) {
        QuantumQuarryBlockEntity quarry = quantumQuarry(helper, Owner.random("gwU"), true);
        check(quarry.table(helper.getLevel()).region() == QuarryTable.Region.OVERWORLD, "Default table");
        int marker = WorkerLayout.QUANTUM_QUARRY.first(WorkerLayout.Role.MARKER);
        quarry.setItem(marker, BiomeMarkerItem.withBiome(new ItemStack(ModItems.BIOME_MARKER.get()), new ResourceLocation("minecraft:nether_wastes")));
        check(quarry.table(helper.getLevel()).region() == QuarryTable.Region.NETHER, "Nether marker ignored");
        quarry.setItem(marker, BiomeMarkerItem.withBiome(new ItemStack(ModItems.BIOME_MARKER.get()), new ResourceLocation("minecraft:the_end")));
        check(quarry.table(helper.getLevel()).region() == QuarryTable.Region.END, "End marker ignored");
        check(!quarry.canPlaceItem(marker, new ItemStack(Items.DIRT)) && !quarry.canPlaceItem(upgradeSlot(0), new ItemStack(Items.DIRT)),
                "Marker and upgrade slots accept anything");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void quarryUpgradesChangeLoot(GameTestHelper helper) {
        BlockState diamond = Blocks.DIAMOND_ORE.defaultBlockState();
        BlockPos pos = helper.absolutePos(QUARRY);
        List<ItemStack> plain = QuantumQuarryBlockEntity.drops(helper.getLevel(), pos, diamond, Upgrades.NONE.tool(), RandomSource.create(1));
        check(plain.size() == 1 && plain.get(0).is(Items.DIAMOND) && plain.get(0).getCount() == 1, "Plain " + plain);
        List<ItemStack> silk = QuantumQuarryBlockEntity.drops(helper.getLevel(), pos, diamond, new Upgrades(0, 0, true).tool(), RandomSource.create(1));
        check(silk.size() == 1 && silk.get(0).is(Items.DIAMOND_ORE), "Silk " + silk);
        int total = 0;
        for (int seed = 0; seed < 200; seed++) {
            for (ItemStack stack : QuantumQuarryBlockEntity.drops(helper.getLevel(), pos, diamond, new Upgrades(0, 3, false).tool(),
                    RandomSource.create(seed))) total += stack.getCount();
        }
        check(total > 300 && total < 600, "Fortune III gave " + total + " diamonds from 200 ores");
        // Silk touch replaces fortune, and levels are capped.
        QuantumQuarryBlockEntity quarry = quantumQuarry(helper, Owner.random("gwV"), true);
        quarry.setItem(upgradeSlot(0), new ItemStack(ModItems.FORTUNE_UPGRADE.get(), 3));
        quarry.setItem(upgradeSlot(1), new ItemStack(ModItems.SILK_TOUCH_UPGRADE.get()));
        quarry.setItem(upgradeSlot(2), Upgrades.speed(8));
        Upgrades read = Upgrades.read(quarry, WorkerLayout.QUANTUM_QUARRY);
        check(read.silkTouch() && read.fortune() == 0 && read.speed() == 8, "Upgrades " + read);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void quantumQuarryWaitsWhenFull(GameTestHelper helper) {
        Owner owner = Owner.random("gwW");
        QuantumQuarryBlockEntity quarry = quantumQuarry(helper, owner, true);
        quarry.setItem(upgradeSlot(0), Upgrades.speed(8));
        for (int slot = WorkerLayout.QUANTUM_QUARRY.firstOutput(); slot < WorkerLayout.QUANTUM_QUARRY.size(); slot++) {
            quarry.setItem(slot, new ItemStack(Items.BEDROCK, 64));
        }
        TestProtection.power(helper, owner, 1000);
        helper.startSequence()
                .thenWaitUntil(() -> check(quarry.status() == Status.BLOCKED, "Expected BLOCKED, got " + quarry.status()))
                .thenExecute(() -> {
                    long mined = quarry.mined();
                    check(mined == 1 && quarry.demand() == 0, "Kept mining or drawing while full: " + mined + ", " + quarry.demand());
                    CompoundTag saved = quarry.saveWithoutMetadata();
                    check(saved.contains("Pending"), "Waiting drops are not saved");
                    quarry.removeItem(WorkerLayout.QUANTUM_QUARRY.firstOutput(), 64);
                })
                .thenWaitUntil(() -> check(quarry.status() == Status.RUNNING && quarry.mined() >= 2, "Did not resume: " + quarry.status()))
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void brokenQuarryDropsWaitingLoot(GameTestHelper helper) {
        Owner owner = Owner.random("gwX");
        QuantumQuarryBlockEntity quarry = quantumQuarry(helper, owner, true);
        quarry.setItem(upgradeSlot(0), Upgrades.speed(8));
        for (int slot = WorkerLayout.QUANTUM_QUARRY.firstOutput(); slot < WorkerLayout.QUANTUM_QUARRY.size(); slot++) {
            quarry.setItem(slot, new ItemStack(Items.BEDROCK, 64));
        }
        TestProtection.power(helper, owner, 1000);
        helper.startSequence()
                .thenWaitUntil(() -> check(quarry.status() == Status.BLOCKED, "Expected BLOCKED, got " + quarry.status()))
                .thenExecute(() -> {
                    List<ItemStack> waiting = quarry.pendingDrops().stream().map(ItemStack::copy).toList();
                    check(!waiting.isEmpty(), "Nothing was waiting for output space");
                    helper.setBlock(QUARRY, Blocks.AIR);
                    for (ItemStack stack : waiting) helper.assertItemEntityPresent(stack.getItem(), QUARRY, 2.0);
                })
                .thenSucceed();
    }

    // ---- Ender quarry ---------------------------------------------------------------------------

    /**
     * A fence loop around x 2..6, z 2..6 (interior 3..5) at y 2, with the quarry outside it at (1, 2, 4).
     * Leaves out the fence at {@code gap} if it is not null.
     */
    private static void fenceLoop(GameTestHelper helper, BlockPos gap) {
        for (int i = 2; i <= 6; i++) {
            for (BlockPos pos : List.of(new BlockPos(i, 2, 2), new BlockPos(i, 2, 6), new BlockPos(2, 2, i), new BlockPos(6, 2, i))) {
                if (!pos.equals(gap)) helper.setBlock(pos, Blocks.OAK_FENCE);
            }
        }
    }

    private static final BlockPos ENDER = new BlockPos(1, 2, 4);

    private static EnderQuarryBlockEntity enderQuarry(GameTestHelper helper, Owner owner) {
        EnderQuarryBlockEntity quarry = place(helper, ModBlocks.ENDER_QUARRY, ENDER);
        return owner == null ? quarry : owned(quarry, owner);
    }

    private static void charge(EnderQuarryBlockEntity quarry, long amount) {
        TestPlatform.EnergyAccess energy = platform().energy(quarry, Direction.UP);
        long inserted = 0;
        for (int i = 0; i < 100 && inserted < amount; i++) inserted += energy.insert(amount - inserted, false);
        check(inserted == amount, "Inserted only " + inserted);
    }

    @GameTest(template = TEMPLATE)
    public static void enderQuarryFindsItsBoundary(GameTestHelper helper) {
        fenceLoop(helper, null);
        QuarryArea.Scan scan = QuarryArea.scan(helper.getLevel(), helper.absolutePos(ENDER));
        check(scan.area() != null && scan.area().columns() == 9, "Closed loop: " + scan.status() + " " + (scan.area() == null ? 0 : scan.area().columns()));
        BlockPos inside = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos fence = helper.absolutePos(new BlockPos(2, 2, 4));
        check(scan.area().contains(inside.getX(), inside.getZ()) && !scan.area().contains(fence.getX(), fence.getZ()), "Interior");
        check(QuarryArea.load(scan.area().save()).equals(scan.area()), "Area save round trip");

        helper.setBlock(new BlockPos(6, 2, 4), Blocks.AIR);
        QuarryArea.Scan open = QuarryArea.scan(helper.getLevel(), helper.absolutePos(ENDER));
        check(open.status() == Status.OPEN_BOUNDARY, "Open loop: " + open.status());
        helper.setBlock(new BlockPos(6, 2, 4), Blocks.COBBLESTONE_WALL);
        check(QuarryArea.scan(helper.getLevel(), helper.absolutePos(ENDER)).area() != null, "Walls close loops too");
        check(QuarryArea.scan(helper.getLevel(), helper.absolutePos(new BlockPos(0, 5, 0))).status() == Status.NO_BOUNDARY, "No boundary");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void enderQuarryMinesInsideOnlyAndRespectsProtection(GameTestHelper helper) {
        fenceLoop(helper, null);
        // Two layers inside: dirt at the fence height, stone below, with things it must leave alone.
        for (int x = 3; x <= 5; x++) {
            for (int z = 3; z <= 5; z++) {
                helper.setBlock(new BlockPos(x, 2, z), Blocks.DIRT);
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        BlockPos bedrock = new BlockPos(3, 1, 3);
        BlockPos barrier = new BlockPos(4, 1, 3);
        BlockPos chest = new BlockPos(5, 1, 3);
        BlockPos claimed = new BlockPos(4, 2, 4);
        helper.setBlock(bedrock, Blocks.BEDROCK);
        helper.setBlock(barrier, Blocks.BARRIER);
        helper.setBlock(chest, Blocks.CHEST);
        TestProtection.protect(helper, claimed);
        BlockPos outside = new BlockPos(7, 2, 4);
        helper.setBlock(outside, Blocks.DIRT);
        helper.setBlock(outside.below(), Blocks.STONE);

        EnderQuarryBlockEntity quarry = enderQuarry(helper, Owner.random("gwX"));
        charge(quarry, EnderQuarryBlockEntity.CAPACITY);
        // The quarry's ticker is run here rather than left to the world. A block entity ticks only once its chunk is
        // ticking with its entities loaded, and on a loaded CI server a freshly placed GameTest area can wait longer
        // than the timeout for that, so the area was sometimes still untouched (dirt left at the first column) when
        // the test gave up. MAX_DRIVEN_TICKS is the old timeout; the 14 minable blocks take about 60 quarry ticks.
        BlockState state = quarry.getBlockState();
        int ticks = 0;
        while (ticks < MAX_DRIVEN_TICKS && !enderAreaMined(helper, claimed, bedrock, barrier, chest)) {
            EnderQuarryBlockEntity.serverTick(helper.getLevel(), quarry.getBlockPos(), state, quarry);
            ticks++;
        }
        for (int x = 3; x <= 5; x++) {
            for (int z = 3; z <= 5; z++) {
                BlockPos top = new BlockPos(x, 2, z);
                BlockPos low = new BlockPos(x, 1, z);
                String after = " after " + ticks + " quarry ticks (" + quarry.status() + ", " + quarry.mined() + " mined)";
                if (!top.equals(claimed)) check(helper.getBlockState(top).isAir(), "Dirt left at " + top + after);
                if (!low.equals(bedrock) && !low.equals(barrier) && !low.equals(chest)) {
                    check(helper.getBlockState(low).isAir(), "Stone left at " + low + after);
                }
            }
        }
        check(helper.getBlockState(claimed).is(Blocks.DIRT), "Mined a protected block");
        check(helper.getBlockState(bedrock).is(Blocks.BEDROCK) && helper.getBlockState(barrier).is(Blocks.BARRIER)
                && helper.getBlockState(chest).is(Blocks.CHEST), "Mined an unbreakable block or a block entity");
        check(helper.getBlockState(outside).is(Blocks.DIRT) && helper.getBlockState(outside.below()).is(Blocks.STONE), "Mined outside the loop");
        check(helper.getBlockState(new BlockPos(2, 2, 4)).is(Blocks.OAK_FENCE), "Mined its own boundary");
        int dirt = 0;
        int cobble = 0;
        for (int slot = WorkerLayout.ENDER_QUARRY.firstOutput(); slot < WorkerLayout.ENDER_QUARRY.size(); slot++) {
            ItemStack stack = quarry.getItem(slot);
            if (stack.is(Items.DIRT)) dirt += stack.getCount();
            if (stack.is(Items.COBBLESTONE)) cobble += stack.getCount();
        }
        check(dirt >= 8 && cobble >= 6, "Output: " + dirt + " dirt, " + cobble + " cobblestone");
        long spent = EnderQuarryBlockEntity.CAPACITY - quarry.energy().stored();
        check(spent == quarry.mined() * EnderQuarryBlockEntity.BASE_ENERGY, "Energy " + spent + " for " + quarry.mined() + " blocks");
        helper.succeed();
    }

    private static final int MAX_DRIVEN_TICKS = 400;

    /** Whether every minable block inside the loop of {@link #enderQuarryMinesInsideOnlyAndRespectsProtection} is gone. */
    private static boolean enderAreaMined(GameTestHelper helper, BlockPos claimed, BlockPos... kept) {
        List<BlockPos> keep = List.of(kept);
        for (int x = 3; x <= 5; x++) {
            for (int z = 3; z <= 5; z++) {
                BlockPos top = new BlockPos(x, 2, z);
                BlockPos low = new BlockPos(x, 1, z);
                if (!top.equals(claimed) && !helper.getBlockState(top).isAir()) return false;
                if (!keep.contains(low) && !helper.getBlockState(low).isAir()) return false;
            }
        }
        return true;
    }

    @GameTest(template = TEMPLATE)
    public static void enderQuarryNeedsEnergyOwnerAndLoop(GameTestHelper helper) {
        fenceLoop(helper, null);
        helper.setBlock(new BlockPos(4, 2, 4), Blocks.DIRT);
        EnderQuarryBlockEntity quarry = enderQuarry(helper, Owner.random("gwY"));
        EnderQuarryBlockEntity unowned = place(helper, ModBlocks.ENDER_QUARRY, new BlockPos(4, 2, 7));
        TestPlatform.EnergyAccess energy = platform().energy(quarry, Direction.NORTH);
        check(energy.extract(100, true) == 0, "Energy can be pulled out of the ender quarry");
        check(energy.insert(EnderQuarryBlockEntity.MAX_RECEIVE * 2, true) == EnderQuarryBlockEntity.MAX_RECEIVE, "Receive limit");
        helper.startSequence()
                .thenIdle(25)
                .thenExecute(() -> {
                    check(quarry.status() == Status.NO_ENERGY, "Expected NO_ENERGY, got " + quarry.status());
                    check(helper.getBlockState(new BlockPos(4, 2, 4)).is(Blocks.DIRT), "Mined without energy");
                    check(unowned.status() == Status.NO_OWNER, "Expected NO_OWNER, got " + unowned.status());
                })
                .thenSucceed();
    }

    @GameTest(template = TEMPLATE)
    public static void enderQuarryReportsBadBoundaries(GameTestHelper helper) {
        fenceLoop(helper, new BlockPos(6, 2, 4));
        EnderQuarryBlockEntity open = enderQuarry(helper, Owner.random("gwZ"));
        EnderQuarryBlockEntity none = owned(place(helper, ModBlocks.ENDER_QUARRY, new BlockPos(0, 4, 0)), Owner.random("gwZ"));
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    check(open.status() == Status.OPEN_BOUNDARY && open.area() == null, "Expected OPEN_BOUNDARY, got " + open.status());
                    check(none.status() == Status.NO_BOUNDARY, "Expected NO_BOUNDARY, got " + none.status());
                })
                .thenSucceed();
    }
}

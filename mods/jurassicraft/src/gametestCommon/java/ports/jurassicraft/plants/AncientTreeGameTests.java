package ports.jurassicraft.plants;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import ports.jurassicraft.gametest.TestPlatform;
import ports.jurassicraft.PortMod;

public final class AncientTreeGameTests {
    private AncientTreeGameTests() {}
    private static void require(GameTestHelper helper, boolean condition, String message) { if (!condition) helper.fail(message); }

    /** Reference hashes are from running the five unmodified upstream generators in an empty world. */
    @GameTest(template = "jurassicraft:plant_culture_test")
    public static void treesMatchUpstreamLayouts(GameTestHelper helper) throws Exception {
        try (var input = AncientTreeGameTests.class.getResourceAsStream("/assets/jurassicraft/test/tree_layout_reference.txt")) {
            require(helper, input != null, "upstream layout reference missing");
            var lines = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            for (String line : lines.lines().toList()) {
                String[] fields = line.split(" ");
                PlantSpecies species = PlantSpecies.valueOf(fields[0]);
                var log = PlantRegistry.log(species).get();
                var leaf = PlantRegistry.leaves(species).get();
                var shape = AncientTreeShape.create(species, RandomSource.create(Long.parseLong(fields[1])), log.defaultBlockState(), leaf.defaultBlockState());
                require(helper, shape.blocks().size() == Integer.parseInt(fields[2]), "wrong tree size for " + line);
                var entries = new ArrayList<>(shape.blocks().entrySet());
                entries.sort(Comparator.<Map.Entry<BlockPos, AncientTreeShape.Placement>>comparingInt(e -> e.getKey().getX())
                        .thenComparingInt(e -> e.getKey().getY()).thenComparingInt(e -> e.getKey().getZ()));
                var text = new StringBuilder();
                for (var entry : entries) {
                    BlockPos p = entry.getKey();
                    BlockState state = entry.getValue().state();
                    text.append(p.getX()).append(',').append(p.getY()).append(',').append(p.getZ()).append(',')
                            .append(state.is(leaf) ? "leaf" : state.getValue(RotatedPillarBlock.AXIS).getName()).append(';');
                }
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8)));
                require(helper, hash.equals(fields[3]), "tree differs from upstream: " + fields[0] + " seed " + fields[1]);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:plant_culture_test")
    public static void leavesFollowDiagonalSameSpeciesSupport(GameTestHelper helper) {
        Map<BlockPos, BlockState> cells = new HashMap<>();
        BlockGetter world = new BlockGetter() {
            @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
            @Override public BlockState getBlockState(BlockPos pos) { return cells.getOrDefault(pos, Blocks.AIR.defaultBlockState()); }
            @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
            @Override public int getHeight() { return 384; }
            @Override public int getMinBuildHeight() { return -64; }
        };
        var leaf = PlantRegistry.leaves(PlantSpecies.GINKGO).get();
        var log = PlantRegistry.log(PlantSpecies.GINKGO).get();
        for (int i = 1; i < 7; i++) cells.put(new BlockPos(i, i, i), leaf.defaultBlockState());
        cells.put(new BlockPos(7, 7, 7), log.defaultBlockState());
        require(helper, WoodBlocks.Leaves.hasSupport(world, BlockPos.ZERO, leaf, log), "seven-step diagonal path should support leaves");
        cells.remove(new BlockPos(4, 4, 4));
        require(helper, !WoodBlocks.Leaves.hasSupport(world, BlockPos.ZERO, leaf, log), "a broken path must not support leaves");
        cells.put(new BlockPos(4, 4, 4), PlantRegistry.leaves(PlantSpecies.PHOENIX).get().defaultBlockState());
        require(helper, !WoodBlocks.Leaves.hasSupport(world, BlockPos.ZERO, leaf, log), "other species leaves cannot connect the path");
        cells.put(new BlockPos(4, 4, 4), leaf.defaultBlockState());
        cells.put(new BlockPos(7, 7, 7), PlantRegistry.log(PlantSpecies.PHOENIX).get().defaultBlockState());
        require(helper, !WoodBlocks.Leaves.hasSupport(world, BlockPos.ZERO, leaf, log), "other species logs cannot support leaves");
        cells.put(new BlockPos(7, 7, 7), leaf.defaultBlockState());
        cells.put(new BlockPos(8, 8, 8), log.defaultBlockState());
        require(helper, !WoodBlocks.Leaves.hasSupport(world, BlockPos.ZERO, leaf, log), "eight-step paths must not support leaves");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:tree_growth_test")
    public static void saplingGrowthRespectsObstructions(GameTestHelper helper) {
        BlockPos root = new BlockPos(11, 1, 11);
        var sapling = (AncientSaplingBlock) PlantRegistry.plant(PlantSpecies.GINKGO).get();
        helper.setBlock(root.below(), Blocks.DIRT);
        helper.setBlock(root, sapling);
        sapling.advanceTree(helper.getLevel(), helper.absolutePos(root), helper.getBlockState(root), RandomSource.create(123));
        require(helper, helper.getBlockState(root).getValue(SaplingBlock.STAGE) == 1, "sapling must advance through its first stage");
        helper.setBlock(root.above(2), Blocks.BEDROCK);
        sapling.grow(helper.getLevel(), helper.absolutePos(root), RandomSource.create(123));
        require(helper, helper.getBlockState(root).is(sapling), "a blocked trunk must preserve the sapling");
        require(helper, helper.getBlockState(root.above(2)).is(Blocks.BEDROCK), "blocked growth damaged the obstacle");
        helper.setBlock(root.above(2), Blocks.AIR);
        helper.setBlock(root.east().above(2), Blocks.CHEST);
        helper.setBlock(root.west().above(2), Blocks.WATER);
        sapling.grow(helper.getLevel(), helper.absolutePos(root), RandomSource.create(123));
        require(helper, helper.getBlockState(root).is(PlantRegistry.log(PlantSpecies.GINKGO).get()), "unblocked trunk did not grow");
        require(helper, helper.getBlockState(root.east().above(2)).is(Blocks.CHEST), "a branch replaced a block entity");
        require(helper, helper.getBlockState(root.west().above(2)).is(Blocks.WATER), "a branch replaced water");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:tree_growth_test")
    public static void brokenTreeRechecksDiagonalLeaves(GameTestHelper helper) {
        BlockPos base = new BlockPos(5, 2, 5);
        var log = PlantRegistry.log(PlantSpecies.GINKGO).get();
        var leaf = (WoodBlocks.Leaves) PlantRegistry.leaves(PlantSpecies.GINKGO).get();
        helper.setBlock(base, log);
        for (int i = 1; i <= 6; i++) helper.setBlock(base.offset(i, i, i), leaf);
        BlockPos tip = base.offset(6, 6, 6), absoluteTip = helper.absolutePos(tip);
        leaf.randomTick(helper.getBlockState(tip), helper.getLevel(), absoluteTip, RandomSource.create(1));
        require(helper, helper.getBlockState(tip).getValue(LeavesBlock.DISTANCE) == 1, "connected diagonal leaf was not marked supported");
        helper.setBlock(base, Blocks.AIR);
        require(helper, helper.getBlockState(tip).getValue(LeavesBlock.DISTANCE) == 7, "removing a log must recheck diagonal leaves");
        leaf.randomTick(helper.getBlockState(tip), helper.getLevel(), absoluteTip, RandomSource.create(1));
        require(helper, helper.getBlockState(tip).isAir(), "unsupported leaf did not decay");
        helper.setBlock(tip, leaf.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
        leaf.randomTick(helper.getBlockState(tip), helper.getLevel(), absoluteTip, RandomSource.create(1));
        require(helper, helper.getBlockState(tip).is(leaf), "player-placed leaves must not decay");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:tree_growth_test")
    public static void unsupportedLeafDecayDoesNotRecheckSupportedNeighbors(GameTestHelper helper) {
        BlockPos root = new BlockPos(4, 2, 5);
        var log = PlantRegistry.log(PlantSpecies.GINKGO).get();
        var leaf = (WoodBlocks.Leaves) PlantRegistry.leaves(PlantSpecies.GINKGO).get();
        for (int i = 0; i < 8; i++) helper.setBlock(root.east(i), leaf);
        helper.setBlock(root.east(8), log);
        BlockPos neighbor = root.east();
        leaf.randomTick(helper.getBlockState(neighbor), helper.getLevel(), helper.absolutePos(neighbor), RandomSource.create(1));
        require(helper, helper.getBlockState(neighbor).getValue(LeavesBlock.DISTANCE) == 1, "neighbor at seven steps must be supported");
        leaf.randomTick(helper.getBlockState(root), helper.getLevel(), helper.absolutePos(root), RandomSource.create(1));
        require(helper, helper.getBlockState(root).isAir(), "leaf eight steps from its log must decay");
        require(helper, helper.getBlockState(neighbor).getValue(LeavesBlock.DISTANCE) == 1, "natural decay needlessly rechecked its supported neighbor");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:tree_growth_test")
    public static void manuallyRemovedLeafRechecksSupportedNeighbors(GameTestHelper helper) {
        BlockPos root = new BlockPos(4, 2, 5);
        var log = PlantRegistry.log(PlantSpecies.GINKGO).get();
        var leaf = (WoodBlocks.Leaves) PlantRegistry.leaves(PlantSpecies.GINKGO).get();
        helper.setBlock(root, log);
        for (int i = 1; i <= 7; i++) helper.setBlock(root.east(i), leaf);
        BlockPos tip = root.east(7);
        leaf.randomTick(helper.getBlockState(tip), helper.getLevel(), helper.absolutePos(tip), RandomSource.create(1));
        require(helper, helper.getBlockState(tip).getValue(LeavesBlock.DISTANCE) == 1, "tip must initially be supported");
        helper.setBlock(root.east(4), Blocks.AIR);
        require(helper, helper.getBlockState(tip).getValue(LeavesBlock.DISTANCE) == 7, "manual leaf removal did not recheck dependent leaves");
        leaf.randomTick(helper.getBlockState(tip), helper.getLevel(), helper.absolutePos(tip), RandomSource.create(1));
        require(helper, helper.getBlockState(tip).isAir(), "disconnected tip did not decay");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:plant_culture_test")
    public static void naturalDecayScopeHandlesNestedCallbacksAndExceptions(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos outer = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos inner = outer.east();
        WoodBlocks.Leaves.withNaturalDecay(level, outer, () -> {
            require(helper, !WoodBlocks.Leaves.consumeNaturalDecay(level, inner), "outer scope suppressed another block");
            WoodBlocks.Leaves.withNaturalDecay(level, inner, () -> {
                require(helper, WoodBlocks.Leaves.consumeNaturalDecay(level, inner), "nested removal was not recognized");
                require(helper, !WoodBlocks.Leaves.consumeNaturalDecay(level, inner), "nested removal exemption was reused");
            });
            require(helper, WoodBlocks.Leaves.consumeNaturalDecay(level, outer), "outer removal scope was not restored");
            require(helper, !WoodBlocks.Leaves.consumeNaturalDecay(level, outer), "outer removal exemption was reused");
        });
        require(helper, !WoodBlocks.Leaves.consumeNaturalDecay(level, outer), "decay scope leaked after removal");
        try {
            WoodBlocks.Leaves.withNaturalDecay(level, outer, () -> { throw new IllegalStateException("test"); });
            helper.fail("expected exception from callback");
        } catch (IllegalStateException expected) {
            require(helper, !WoodBlocks.Leaves.consumeNaturalDecay(level, outer), "decay scope leaked after exception");
        }
        helper.succeed();
    }

    /** Fire spread (and other mods) read the original wood fire odds through each loader's fire table. */
    @GameTest(template = "jurassicraft:plant_culture_test")
    public static void woodIsFlammable(GameTestHelper helper) {
        var level = helper.getLevel(); var pos = helper.absolutePos(BlockPos.ZERO);
        int trees = 0;
        for (PlantSpecies species : PlantSpecies.values()) if (species.isTree()) {
            trees++;
            requireOdds(helper, TestPlatform.get().fireOdds(level, pos, PlantRegistry.log(species).get()), 5, 5, species + " log");
            requireOdds(helper, TestPlatform.get().fireOdds(level, pos, PlantRegistry.leaves(species).get()), 30, 60, species + " leaves");
            for (String suffix : new String[]{"planks", "stairs", "slab", "fence", "fence_gate"}) {
                var block = Registry.BLOCK.get(new ResourceLocation(PortMod.MOD_ID, species.id() + "_" + suffix));
                require(helper, block != Blocks.AIR, "missing " + species.id() + "_" + suffix);
                requireOdds(helper, TestPlatform.get().fireOdds(level, pos, block), 5, 20, species.id() + "_" + suffix);
            }
        }
        require(helper, trees == 5, "expected the five original trees, found " + trees);
        helper.succeed();
    }

    private static void requireOdds(GameTestHelper helper, int[] odds, int burn, int spread, String what) {
        require(helper, odds != null && odds[0] == burn && odds[1] == spread,
                what + " does not have the original fire odds (" + burn + "/" + spread + "): "
                        + (odds == null ? "not flammable" : odds[0] + "/" + odds[1]));
    }
}

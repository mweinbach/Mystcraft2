package ports.jurassicraft.plants;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.TagKey;
import net.minecraftforge.common.Tags;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.JurassicContent;

/** In-world checks for safe replacement and retained species/orientation. */
@GameTestHolder(PortMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlantWorldgenGameTests {
    private PlantWorldgenGameTests() {}
    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }

    @GameTest(template = "plant_culture_test", templateNamespace = PortMod.MOD_ID)
    public static void petrifiedTrunkRespectsNaturalBlocks(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos start = new BlockPos(1, 0, 1);
        helper.setBlock(start, Blocks.DIRT);
        helper.setBlock(start.above(), Blocks.STONE);
        helper.setBlock(start.above(2), Blocks.BEDROCK);
        helper.setBlock(start.above(3), Blocks.CHEST);
        helper.setBlock(start.above(4), Blocks.WATER);
        require(helper, PetrifiedTreeFeature.generateTrunk(level, helper.absolutePos(start), PlantSpecies.GINKGO, 5, 0, 1, 0), "no trunk generated");
        for (int i = 0; i < 2; i++) {
            var state = level.getBlockState(helper.absolutePos(start.above(i)));
            require(helper, state.is(PlantRegistry.petrifiedLog(PlantSpecies.GINKGO).get()), "wrong log");
            require(helper, state.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Y, "wrong axis");
        }
        require(helper, level.getBlockState(helper.absolutePos(start.above(2))).is(Blocks.BEDROCK), "bedrock replaced");
        require(helper, level.getBlockState(helper.absolutePos(start.above(3))).is(Blocks.CHEST), "block entity replaced");
        require(helper, level.getBlockState(helper.absolutePos(start.above(4))).is(Blocks.WATER), "water replaced");
        helper.succeed();
    }

    @GameTest(template = "plant_culture_test", templateNamespace = PortMod.MOD_ID)
    public static void petrifiedTrunkRetainsEachSpecies(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos start = new BlockPos(0, 1, 1);
        for (PlantSpecies tree : PlantSpecies.values()) {
            if (!tree.isTree()) continue;
            for (int i = 0; i < 3; i++) helper.setBlock(start.east(i), Blocks.STONE);
            require(helper, PetrifiedTreeFeature.generateTrunk(level, helper.absolutePos(start), tree, 3, 1, 0, 0), "no trunk for " + tree);
            for (int i = 0; i < 3; i++) {
                var state = level.getBlockState(helper.absolutePos(start.east(i)));
                require(helper, state.is(PlantRegistry.petrifiedLog(tree).get()), "wrong species " + tree);
                require(helper, state.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.X, "wrong trunk axis");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "plant_culture_test", templateNamespace = PortMod.MOD_ID)
    public static void fossilsReplaceOnlyCoal(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = new BlockPos(1, 1, 1);
        BlockPos absolute = helper.absolutePos(pos);
        long yes = 0, no = 0;
        while (RandomSource.create(yes).nextInt(10) != 0) yes++;
        while (RandomSource.create(no).nextInt(10) == 0) no++;
        for (var coal : new net.minecraft.world.level.block.Block[] {Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE}) {
            helper.setBlock(pos, coal);
            require(helper, !CoalPlantFossilsFeature.tryReplace(level, absolute, RandomSource.create(no)), "all coal was replaced");
            require(helper, level.getBlockState(absolute).is(coal), "coal lost on failed roll");
            require(helper, CoalPlantFossilsFeature.tryReplace(level, absolute, RandomSource.create(yes)), "coal was not replaced on successful roll");
            require(helper, level.getBlockState(absolute).is(JurassicContent.PLANT_FOSSIL_BLOCK.get()), "wrong fossil block");
        }
        helper.setBlock(pos, Blocks.STONE);
        require(helper, !CoalPlantFossilsFeature.tryReplace(level, absolute, RandomSource.create(yes)), "stone replaced");
        require(helper, level.getBlockState(absolute).is(Blocks.STONE), "non-coal changed");
        // A different block need only opt into the shared coal-ore tags that jurassicraft:coal_ores includes.
        var forgeCoal = Registry.BLOCK.getTag(Tags.Blocks.ORES_COAL);
        require(helper, forgeCoal.isPresent() && forgeCoal.get().size() > 0 && forgeCoal.get().stream()
                .allMatch(block -> CoalPlantFossilsFeature.isCoal(block.value().defaultBlockState())), "Forge-tagged coal ores were not recognized");
        helper.succeed();
    }

    @GameTest(template = "plant_culture_test", templateNamespace = PortMod.MOD_ID)
    public static void unsupportedAquaticPlantDropsAndRestoresWater(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos.below(), Blocks.SAND);
        helper.setBlock(pos.above(), Blocks.WATER);
        helper.setBlock(pos, PlantRegistry.GRACILARIA_SEAWEED.get());
        var previous = helper.getEntities(EntityType.ITEM, pos, 2).stream()
                .map(item -> item.getUUID()).collect(java.util.stream.Collectors.toSet());
        helper.setBlock(pos.below(), Blocks.AIR);
        require(helper, helper.getBlockState(pos).is(Blocks.WATER), "underwater plant must leave water behind");
        require(helper, helper.getEntities(EntityType.ITEM, pos, 2).stream()
                .anyMatch(item -> !previous.contains(item.getUUID()) && item.getItem().is(PlantRegistry.GRACILARIA.get())),
                "unsupported aquatic plant must drop its item");
        helper.succeed();
    }

    @GameTest(template = "plant_culture_test", templateNamespace = PortMod.MOD_ID)
    public static void peatSpreadsOnlyToDirtBelowGrass(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = new BlockPos(1, 0, 1);
        helper.setBlock(pos, Blocks.DIRT);
        helper.setBlock(pos.above(), Blocks.GRASS_BLOCK);
        require(helper, PeatBlock.spreadTo(level, helper.absolutePos(pos)), "dirt under grass should become peat");
        require(helper, helper.getBlockState(pos).is(PlantRegistry.PEAT.get()), "peat spread produced the wrong block");
        helper.setBlock(pos, Blocks.STONE);
        require(helper, !PeatBlock.spreadTo(level, helper.absolutePos(pos)), "peat must not replace stone");
        helper.setBlock(pos, Blocks.DIRT);
        helper.setBlock(pos.above(), Blocks.STONE);
        helper.setBlock(pos.above(2), Blocks.AIR);
        helper.setBlock(pos.above(3), Blocks.AIR);
        require(helper, !PeatBlock.spreadTo(level, helper.absolutePos(pos)), "peat must require nearby grass above");
        helper.succeed();
    }
}

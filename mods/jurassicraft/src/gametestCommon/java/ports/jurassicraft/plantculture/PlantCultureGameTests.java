package ports.jurassicraft.plantculture;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import ports.jurassicraft.genetics.DNAData;
import ports.jurassicraft.genetics.Genetics;
import ports.jurassicraft.genetics.GeneticsRegistry;
import ports.jurassicraft.plants.PlantRegistry;
import ports.jurassicraft.plants.PlantSpecies;

/** Opt-in server integration checks, without accepting the Minecraft EULA. */
public final class PlantCultureGameTests {
    private static final BlockPos GROUND = new BlockPos(1, 1, 1);
    private PlantCultureGameTests() {}
    private static void require(GameTestHelper helper, boolean condition, String message) { if (!condition) helper.fail(message); }

    @GameTest(template = "jurassicraft:plant_culture_test")
    public static void callusPreservesDna(GameTestHelper helper) {
        for (PlantSpecies species : PlantSpecies.values()) {
            if (!species.isPrehistoric()) continue;
            require(helper, PlantRegistry.plant(species) != null, "missing plant mapping for " + species);
            ItemStack tube = new ItemStack(GeneticsRegistry.plantDna(species).get());
            Genetics.write(tube.getOrCreateTag(), DNAData.plant(species, 100));
            tube.getOrCreateTag().putString("OtherModData", "retained");
            ItemStack output = PlantCulture.createCallus(tube);
            require(helper, output.is(PlantCultureRegistry.callus(species).get()), "wrong callus for " + species);
            require(helper, output.getTag().getString("OtherModData").equals("retained"), "sample metadata lost");
            require(helper, Genetics.read(output.getTag()).filter(DNAData.plant(species, 100)::equals).isPresent(), "DNA data lost");
            Genetics.write(tube.getOrCreateTag(), DNAData.plant(species, 50));
            require(helper, PlantCulture.createCallus(tube).isEmpty(), "incomplete DNA must not produce a callus");
        }
        helper.succeed();
    }

    /**
     * Test plots sit underground with no sky, so crops need block light. The threaded light engine applies
     * new light sources later; right after server start that took up to 157 ticks on one CPU.
     */
    @GameTest(template = "jurassicraft:plant_culture_test", timeoutTicks = 400)
    public static void plantsOnFarmland(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos ground = helper.absolutePos(GROUND), target = ground.above();
        helper.setBlock(GROUND, Blocks.FARMLAND);
        helper.setBlock(GROUND.above().west(), Blocks.GLOWSTONE);
        helper.startSequence()
                .thenWaitUntil(() -> require(helper, level.getRawBrightness(target, 0) >= 8, "crop plot is not lit yet"))
                .thenExecute(() -> {
                    require(helper, PlantCallusItem.plant(level, ground, PlantSpecies.WILD_ONION) == InteractionResult.CONSUME, "crop should plant");
                    require(helper, level.getBlockState(ground).is(Blocks.FARMLAND), "crop must retain farmland");
                    require(helper, level.getBlockState(target).is(PlantRegistry.plant(PlantSpecies.WILD_ONION).get()), "wrong crop");
                    level.setBlock(target, Blocks.AIR.defaultBlockState(), 3);
                    require(helper, PlantCallusItem.plant(level, ground, PlantSpecies.SMALL_ROYAL_FERN) == InteractionResult.CONSUME, "fern should plant");
                    require(helper, level.getBlockState(ground).is(Blocks.DIRT), "non-crop should turn farmland into dirt");
                    require(helper, level.getBlockState(target).is(PlantRegistry.plant(PlantSpecies.SMALL_ROYAL_FERN).get()), "wrong fern");
                })
                .thenSucceed();
    }

    @GameTest(template = "jurassicraft:plant_culture_test")
    public static void tallAndAquaticPlanting(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos ground = helper.absolutePos(GROUND), target = ground.above();
        helper.setBlock(GROUND, Blocks.FARMLAND);
        level.setBlock(target.above(), Blocks.STONE.defaultBlockState(), 3);
        require(helper, PlantCallusItem.plant(level, ground, PlantSpecies.DICKSONIA) == InteractionResult.PASS, "blocked tall plant must fail");
        require(helper, level.getBlockState(ground).is(Blocks.FARMLAND), "blocked planting must not change soil");
        level.setBlock(target.above(), Blocks.AIR.defaultBlockState(), 3);
        require(helper, PlantCallusItem.plant(level, ground, PlantSpecies.DICKSONIA) == InteractionResult.CONSUME, "tall plant should plant");
        require(helper, level.getBlockState(target).getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER, "missing lower half");
        require(helper, level.getBlockState(target.above()).getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER, "missing upper half");
        level.setBlock(target, Blocks.WATER.defaultBlockState(), 3);
        level.setBlock(target.above(), Blocks.WATER.defaultBlockState(), 3);
        level.setBlock(ground, Blocks.SAND.defaultBlockState(), 3);
        require(helper, PlantCallusItem.plant(level, ground, PlantSpecies.AULOPORA) == InteractionResult.CONSUME, "aquatic callus should plant in source water");
        require(helper, level.getBlockState(target).is(PlantRegistry.plant(PlantSpecies.AULOPORA).get()), "wrong aquatic plant");
        helper.succeed();
    }
}

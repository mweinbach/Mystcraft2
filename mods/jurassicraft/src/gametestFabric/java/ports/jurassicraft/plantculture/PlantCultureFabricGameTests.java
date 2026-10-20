package ports.jurassicraft.plantculture;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;
import ports.jurassicraft.plants.PlantRegistry;
import ports.jurassicraft.plants.PlantSpecies;

public final class PlantCultureFabricGameTests implements FabricGameTest {
    private static final BlockPos GROUND = new BlockPos(1, 1, 1);
    public PlantCultureFabricGameTests() {}
    private static void require(GameTestHelper helper, boolean condition, String message) { if (!condition) helper.fail(message); }

    /**
     * Test plots sit underground with no sky, so crops need block light. The threaded light engine applies
     * new light sources later; right after server start that took up to 157 ticks on one CPU.
     */
    @GameTest(template = "jurassicraft:plant_culture_test", timeoutTicks = 400)
    public void plantsOnFarmland(GameTestHelper helper) {
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
}

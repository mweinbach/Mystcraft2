package ports.jurassicraft.plants;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import ports.jurassicraft.PortMod;

public final class CoralGameTests {
    public CoralGameTests() {}
    private static void require(GameTestHelper helper, boolean value, String message) { if (!value) helper.fail(message); }

    @GameTest(template = "jurassicraft:plant_culture_test")
    public static void coralUsesSharedDensityAcrossSpecies(GameTestHelper helper) {
        Map<BlockPos, BlockState> cells = new HashMap<>();
        BlockGetter world = new BlockGetter() {
            @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
            @Override public BlockState getBlockState(BlockPos pos) { return cells.getOrDefault(pos, Blocks.AIR.defaultBlockState()); }
            @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
            @Override public int getHeight() { return 384; }
            @Override public int getMinBuildHeight() { return -64; }
        };
        PlantSpecies[] species = {PlantSpecies.ENALLHELIA, PlantSpecies.AULOPORA, PlantSpecies.CLADOCHONUS,
                PlantSpecies.LITHOSTROTION, PlantSpecies.STYLOPHYLLOPSIS, PlantSpecies.HIPPURITES_RADIOSUS};
        for (PlantSpecies coral : species) require(helper, PlantRegistry.plant(coral).get() instanceof AncientCoralBlock,
                "coral species missing shared growth rules: " + coral);
        require(helper, !(PlantRegistry.GRACILARIA_SEAWEED.get() instanceof AncientCoralBlock), "gracilaria is not a coral");
        BlockPos[] positions = {BlockPos.ZERO, new BlockPos(6, 0, 0), new BlockPos(-6, 1, 0),
                new BlockPos(0, -3, -6), new BlockPos(0, 3, 6)};
        for (int i = 0; i < 4; i++) cells.put(positions[i], PlantRegistry.plant(species[i]).get().defaultBlockState());
        cells.put(positions[4], PlantRegistry.GRACILARIA_SEAWEED.get().defaultBlockState());
        cells.put(new BlockPos(7, 0, 0), PlantRegistry.plant(species[5]).get().defaultBlockState());
        require(helper, AncientCoralBlock.hasRoom(world, BlockPos.ZERO), "gracilaria and outside colonies must not count");
        cells.put(positions[4], PlantRegistry.plant(species[4]).get().defaultBlockState());
        require(helper, !AncientCoralBlock.hasRoom(world, BlockPos.ZERO), "five mixed-species coral blocks must stop spreading");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:plant_culture_test")
    public static void coralSpreadingRequiresLight(GameTestHelper helper) {
        long brightSeed = 0;
        while (RandomSource.create(brightSeed).nextInt(10) != 0) brightSeed++;
        require(helper, AncientCoralBlock.passesSpreadRoll(15, RandomSource.create(brightSeed)), "bright coral should have a valid growth roll");
        require(helper, !AncientCoralBlock.passesSpreadRoll(4, RandomSource.create(brightSeed)), "coral must not spread in darkness");
        long dimSeed = 0;
        while (RandomSource.create(dimSeed).nextInt(15) != 0) dimSeed++;
        require(helper, AncientCoralBlock.passesSpreadRoll(5, RandomSource.create(dimSeed)), "light level five should allow spreading");
        long failedSeed = 0;
        while (RandomSource.create(failedSeed).nextInt(10) == 0) failedSeed++;
        require(helper, !AncientCoralBlock.passesSpreadRoll(15, RandomSource.create(failedSeed)), "coral must not spread on every tick");
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:plant_culture_test")
    public static void coralSpreadsOnlyIntoSourceWater(GameTestHelper helper) {
        BlockPos source = new BlockPos(1, 1, 1), target = source.east();
        helper.setBlock(source.below(), Blocks.SAND);
        helper.setBlock(source.above(), Blocks.WATER);
        helper.setBlock(source, PlantRegistry.plant(PlantSpecies.ENALLHELIA).get());
        helper.setBlock(target.below(), Blocks.SAND);
        helper.setBlock(target.above(), Blocks.WATER);
        var coral = helper.getBlockState(source);
        long seed = 0;
        while (true) {
            RandomSource candidate = RandomSource.create(seed);
            if (candidate.nextInt(12) == 7 && candidate.nextInt(12) == 6) break;
            seed++;
        }
        helper.setBlock(target, Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 1));
        require(helper, !AncientCoralBlock.tryPlaceNearby(helper.getLevel(), helper.absolutePos(source), coral, RandomSource.create(seed)),
                "coral must not turn flowing water into source water");
        helper.setBlock(target, Blocks.WATER);
        require(helper, AncientCoralBlock.tryPlaceNearby(helper.getLevel(), helper.absolutePos(source), coral, RandomSource.create(seed)),
                "coral should spread into suitable source water");
        require(helper, helper.getBlockState(target).is(coral.getBlock()), "spread must preserve the coral species");
        helper.succeed();
    }
}

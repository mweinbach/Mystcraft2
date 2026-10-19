package ports.jurassicraft.plantculture;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.genetics.DNAData;
import ports.jurassicraft.genetics.Genetics;
import ports.jurassicraft.genetics.GeneticsRegistry;
import ports.jurassicraft.plants.PlantRegistry;
import ports.jurassicraft.plants.PlantSpecies;

/** Opt-in server integration checks, without accepting the Minecraft EULA. */
@GameTestHolder(PortMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlantCultureGameTests {
    private static final BlockPos GROUND = new BlockPos(1, 1, 1);
    private PlantCultureGameTests() {}
    private static void require(GameTestHelper helper, boolean condition, String message) { if (!condition) helper.fail(message); }

    @GameTest(template = "plant_culture_test", templateNamespace = PortMod.MOD_ID)
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

    @GameTest(template = "plant_culture_test", templateNamespace = PortMod.MOD_ID)
    public static void plantsOnFarmland(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos ground = helper.absolutePos(GROUND), target = ground.above();
        helper.setBlock(GROUND, Blocks.FARMLAND);
        require(helper, PlantCallusItem.plant(level, ground, PlantSpecies.WILD_ONION) == InteractionResult.CONSUME, "crop should plant");
        require(helper, level.getBlockState(ground).is(Blocks.FARMLAND), "crop must retain farmland");
        require(helper, level.getBlockState(target).is(PlantRegistry.plant(PlantSpecies.WILD_ONION).get()), "wrong crop");
        level.setBlock(target, Blocks.AIR.defaultBlockState(), 3);
        require(helper, PlantCallusItem.plant(level, ground, PlantSpecies.SMALL_ROYAL_FERN) == InteractionResult.CONSUME, "fern should plant");
        require(helper, level.getBlockState(ground).is(Blocks.DIRT), "non-crop should turn farmland into dirt");
        require(helper, level.getBlockState(target).is(PlantRegistry.plant(PlantSpecies.SMALL_ROYAL_FERN).get()), "wrong fern");
        helper.succeed();
    }

    @GameTest(template = "plant_culture_test", templateNamespace = PortMod.MOD_ID)
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

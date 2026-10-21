/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import static mekanism.gametest.CoreMachineGameTests.TEMPLATE;
import static mekanism.gametest.CoreMachineGameTests.check;

import java.util.ArrayList;
import java.util.List;
import mekanism.api.heat.HeatAPI;
import mekanism.common.Mekanism;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.evaporation.EvaporationMultiblockData;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.registries.MekanismFluids;
import mekanism.common.tile.multiblock.TileEntityThermalEvaporationBlock;
import mekanism.common.tile.multiblock.TileEntityThermalEvaporationValve;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;

/**
 * A 4×3×4 thermal evaporation plant: frame and floor of thermal evaporation blocks, structural glass in the walls, one
 * valve and one controller, and the 2×2 roof opening the validator leaves as air. Water and brine go in through the
 * valve's fluid handler. Heat goes in through the valve's heat handler. The input fluid stays in the plant; the output
 * fluid (brine, then lithium) is what the valve gives back.
 */
public class ThermalEvaporationGameTests {

    private static final BlockPos ORIGIN = new BlockPos(1, 1, 1);
    private static final int SIZE = 4;
    private static final int HEIGHT = 3;
    /** Floor, the middle wall ring, and the top ring. The inner column and the roof opening stay air. */
    private static final int SHELL = SIZE * SIZE + (SIZE * SIZE - (SIZE - 2) * (SIZE - 2)) * 2;
    /** A 3×3×3 uses the same shape and is below the 4×3×4 minimum. */
    private static final int SMALL_SHELL = 9 + (9 - 1) * 2;
    private static final double TARGET_TEMPERATURE = 1_000;
    private static final long WATER = 200_000;
    private static final long BRINE = 1_000;

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void thermalEvaporationPlantForms(GameTestHelper helper) {
        Plant plant = place(helper, ORIGIN, SIZE, HEIGHT, true, true);
        helper.runAtTickTime(40, () -> {
            TileEntityThermalEvaporationValve valve = valve(helper, plant);
            EvaporationMultiblockData data = formed(valve);
            check(plant.blocks().size() == SHELL, "The shell placed " + plant.blocks().size() + " blocks");
            check(data.length() == SIZE && data.width() == SIZE && data.height() == HEIGHT,
                  "Formed plant bounds are " + data.length() + "x" + data.height() + "x" + data.width());
            int volume = SIZE * HEIGHT * SIZE;
            check(data.getVolume() == volume, "Formed plant volume is " + data.getVolume());
            int inputCapacity = volume / 4 * MekanismConfig.general.evaporationFluidPerTank.get();
            check(data.inputTank.getCapacity() == inputCapacity, "Input capacity is " + data.inputTank.getCapacity());
            check(data.outputTank.getCapacity() == MekanismConfig.general.evaporationOutputTankCapacity.get(),
                  "Output capacity is " + data.outputTank.getCapacity());
            check(data.heatCapacitor.getHeatCapacity() == MekanismConfig.general.evaporationHeatCapacity.get() * HEIGHT,
                  "Heat capacity is " + data.heatCapacitor.getHeatCapacity());
            check(data.locations.size() == SHELL, "The shell has " + data.locations.size() + " blocks");
            check(data.getValveData().size() == 1, "Formed plant has " + data.getValveData().size() + " valves");
            check(TestPlatform.heatCapacitors(valve, plant.valveSide()) == 1, "The valve exposes no heat handler");
            BlockEntity casing = helper.getBlockEntity(plant.origin());
            check(TestPlatform.heatCapacitors(casing, Direction.WEST) == 0, "A casing block exposes heat");
            check(TestPlatform.insertBlockFluid(casing, Direction.WEST, Fluids.WATER, 1_000, true) == 0, "A casing block accepted water");
            check(isActive(helper.getBlockState(plant.controller())), "The formed controller is not active");
            TileEntityThermalEvaporationBlock master = master(helper, plant);
            BlockPos min = helper.absolutePos(plant.origin());
            BlockPos max = min.offset(SIZE - 1, HEIGHT - 1, SIZE - 1);
            AABB bounds = TestPlatform.renderBounds(master);
            AABB structure = new AABB(min, max.offset(1, 1, 1));
            check(bounds.equals(structure), "Master render bounds " + bounds + " are not the structure " + structure);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 160)
    public static void invalidShapesStayApart(GameTestHelper helper) {
        // One block of air between shells. Face-adjacent evaporation blocks merge into one structure, and that
        // assembly fails the cuboid check before the missing-block and minimum-size rules run.
        Plant holed = place(helper, new BlockPos(0, 1, 0), SIZE, HEIGHT, true, true);
        helper.setBlock(holed.origin(), Blocks.AIR);
        Plant small = place(helper, new BlockPos(5, 1, 0), 3, 3, true, true);
        helper.runAtTickTime(40, () -> {
            TileEntityThermalEvaporationValve valve = valve(helper, holed);
            checkSeparate(valve, holed.blocks().size() - 1, "A plant with a missing corner");
            check(TestPlatform.insertBlockFluid(valve, holed.valveSide(), Fluids.WATER, 1_000, true) == 0,
                  "An unformed valve accepted water");
            check(TestPlatform.heatCapacitors(valve, holed.valveSide()) == 0, "An unformed valve exposes a heat handler");
            check(small.blocks().size() == SMALL_SHELL, "The small shell placed " + small.blocks().size() + " blocks");
            checkSeparate(valve(helper, small), small.blocks().size(), "A 3x3x3 plant");
            helper.setBlock(holed.origin(), MekanismBlocks.THERMAL_EVAPORATION_BLOCK.getBlock());
        });
        helper.runAtTickTime(80, () -> {
            TileEntityThermalEvaporationValve valve = valve(helper, holed);
            EvaporationMultiblockData data = formed(valve);
            check(data.length() == SIZE && data.width() == SIZE && data.height() == HEIGHT,
                  "Repairing the corner formed a " + data.length() + "x" + data.height() + "x" + data.width());
            check(valve.getStructure().size() == holed.blocks().size(),
                  "The repaired plant shares a structure of " + valve.getStructure().size() + " blocks");
            checkSeparate(valve(helper, small), small.blocks().size(), "The 3x3x3 after its neighbour formed");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void heatedWaterBecomesBrine(GameTestHelper helper) {
        Plant plant = place(helper, ORIGIN, SIZE, HEIGHT, true, true);
        long[] filled = new long[1];
        helper.runAtTickTime(40, () -> {
            TileEntityThermalEvaporationValve valve = valve(helper, plant);
            EvaporationMultiblockData data = formed(valve);
            Direction side = plant.valveSide();
            BlockEntity casing = helper.getBlockEntity(plant.origin());
            check(data.inputTank.getCapacity() > WATER, "The plant holds " + data.inputTank.getCapacity() + " mB");
            check(TestPlatform.insertBlockFluid(casing, Direction.WEST, Fluids.WATER, 1_000, true) == 0, "A casing block accepted water");
            check(TestPlatform.insertBlockFluid(valve, side, Fluids.LAVA, 1_000, true) == 0, "The valve accepted lava");
            long offered = TestPlatform.insertBlockFluid(valve, side, Fluids.WATER, WATER, false);
            check(offered == WATER, "A simulated fill offered " + offered + " of " + WATER);
            check(data.inputTank.isEmpty(), "A simulated fill stored water");
            filled[0] = TestPlatform.insertBlockFluid(valve, side, Fluids.WATER, WATER, true);
            check(filled[0] == WATER, "The valve accepted " + filled[0] + " of " + WATER);
            check(TestPlatform.blockFluid(valve, side) == WATER, "The fluid lookup reports " + TestPlatform.blockFluid(valve, side));
            check(data.inputTank.getFluid().getFluid() == Fluids.WATER, "The input tank holds " + data.inputTank.getFluid());
            double temperature = data.getTemperature();
            double capacity = data.heatCapacitor.getHeatCapacity();
            TestPlatform.handleBlockHeat(valve, side, (TARGET_TEMPERATURE - temperature) * capacity);
            check(data.getTemperature() == temperature, "Queued heat changed the temperature before the plant ticked");
            check(TestPlatform.heatCapacitors(casing, Direction.WEST) == 0, "A casing block exposes heat");
        });
        helper.succeedWhen(() -> {
            check(filled[0] > 0, "Water has not been inserted");
            TileEntityThermalEvaporationValve valve = valve(helper, plant);
            EvaporationMultiblockData data = formed(valve);
            Direction side = plant.valveSide();
            double multiplier = tempMultiplier(data);
            check(data.getTemperature() > HeatAPI.AMBIENT_TEMP + 50, "The plant is at " + data.getTemperature());
            check(TestPlatform.blockTemperature(valve, side) == data.getTemperature(),
                  "The heat lookup reports " + TestPlatform.blockTemperature(valve, side));
            check(data.lastGain > 0 && Math.abs(data.lastGain - multiplier) < 1.0e-6,
                  "Production " + data.lastGain + " is not the multiplier " + multiplier);
            long water = data.inputTank.getFluidAmount();
            long brine = data.outputTank.getFluidAmount();
            check(data.inputTank.getFluid().getFluid() == Fluids.WATER && water > 0 && water < filled[0],
                  "The input tank holds " + data.inputTank.getFluid());
            check(data.outputTank.getFluid().getFluid() == MekanismFluids.BRINE.getFluid() && brine > 100,
                  "The output tank holds " + data.outputTank.getFluid());
            check(filled[0] - water == brine * 10, "Water consumed " + (filled[0] - water) + " for " + brine + " brine");
            check(TestPlatform.blockFluid(valve, side) == water + brine, "The fluid lookup reports " + TestPlatform.blockFluid(valve, side));
            check(TestPlatform.extractBlockFluid(valve, side, Fluids.WATER, 1_000, true) == 0, "The valve gave water back");
            long drained = TestPlatform.extractBlockFluid(valve, side, MekanismFluids.BRINE.getFluid(), 100, true);
            check(drained == 100, "The valve returned " + drained + " brine");
            check(data.outputTank.getFluidAmount() == brine - 100, "The output tank holds " + data.outputTank.getFluidAmount() + " after the drain");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void heatedBrineBecomesLithium(GameTestHelper helper) {
        Plant plant = place(helper, ORIGIN, SIZE, HEIGHT, true, true);
        long[] filled = new long[1];
        helper.runAtTickTime(40, () -> {
            TileEntityThermalEvaporationValve valve = valve(helper, plant);
            EvaporationMultiblockData data = formed(valve);
            Direction side = plant.valveSide();
            filled[0] = TestPlatform.insertBlockFluid(valve, side, MekanismFluids.BRINE.getFluid(), BRINE, true);
            check(filled[0] == BRINE, "The valve accepted " + filled[0] + " of " + BRINE + " brine");
            check(data.inputTank.getFluid().getFluid() == MekanismFluids.BRINE.getFluid(), "The input tank holds " + data.inputTank.getFluid());
            double temperature = data.getTemperature();
            TestPlatform.handleBlockHeat(valve, side, (TARGET_TEMPERATURE - temperature) * data.heatCapacitor.getHeatCapacity());
        });
        helper.succeedWhen(() -> {
            check(filled[0] > 0, "Brine has not been inserted");
            TileEntityThermalEvaporationValve valve = valve(helper, plant);
            EvaporationMultiblockData data = formed(valve);
            Direction side = plant.valveSide();
            long brine = data.inputTank.isEmpty() ? 0 : data.inputTank.getFluidAmount();
            long lithium = data.outputTank.getFluidAmount();
            check(brine < filled[0], "The plant did not consume brine: " + brine);
            check(data.outputTank.getFluid().getFluid() == MekanismFluids.LITHIUM.getFluid() && lithium > 0,
                  "The output tank holds " + data.outputTank.getFluid());
            check(filled[0] - brine == lithium * 10, "Brine consumed " + (filled[0] - brine) + " for " + lithium + " lithium");
            long drained = TestPlatform.extractBlockFluid(valve, side, MekanismFluids.LITHIUM.getFluid(), lithium, true);
            check(drained == lithium, "The valve returned " + drained + " lithium");
            check(data.outputTank.isEmpty(), "The output tank still holds " + data.outputTank.getFluid());
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void plantContentsSurviveSaveAndLoad(GameTestHelper helper) {
        Plant plant = place(helper, ORIGIN, SIZE, HEIGHT, true, true);
        long[] water = new long[1];
        long[] brine = new long[1];
        double[] temperature = new double[1];
        helper.runAtTickTime(40, () -> {
            TileEntityThermalEvaporationValve valve = valve(helper, plant);
            EvaporationMultiblockData data = formed(valve);
            Direction side = plant.valveSide();
            long filled = TestPlatform.insertBlockFluid(valve, side, Fluids.WATER, WATER, true);
            check(filled == WATER, "The valve accepted " + filled + " before the save");
            TestPlatform.handleBlockHeat(valve, side, (TARGET_TEMPERATURE - data.getTemperature()) * data.heatCapacitor.getHeatCapacity());
        });
        helper.runAtTickTime(70, () -> {
            TileEntityThermalEvaporationValve valve = valve(helper, plant);
            EvaporationMultiblockData data = formed(valve);
            check(data.outputTank.getFluid().getFluid() == MekanismFluids.BRINE.getFluid() && data.outputTank.getFluidAmount() > 0,
                  "The plant produced no brine before the save");
            check(data.inputTank.getFluidAmount() > 0, "The plant consumed all of its water before the save");
            check(data.getTemperature() > HeatAPI.AMBIENT_TEMP + 50, "The plant was at " + data.getTemperature());
            water[0] = data.inputTank.getFluidAmount();
            brine[0] = data.outputTank.getFluidAmount();
            temperature[0] = data.getTemperature();
            List<SavedBlock> saved = new ArrayList<>();
            for (BlockPos pos : plant.blocks()) {
                saved.add(save(helper, pos));
            }
            Mekanism.evaporationManager.inventories.remove(data.inventoryID);
            for (SavedBlock block : saved) {
                helper.setBlock(block.pos, Blocks.AIR);
            }
            for (SavedBlock block : saved) {
                helper.setBlock(block.pos, block.state);
                helper.getBlockEntity(block.pos).load(block.tag);
            }
        });
        helper.runAtTickTime(120, () -> {
            TileEntityThermalEvaporationValve restored = valve(helper, plant);
            EvaporationMultiblockData data = formed(restored);
            long waterNow = data.inputTank.getFluidAmount();
            long brineNow = data.outputTank.getFluidAmount();
            check(data.inputTank.getFluid().getFluid() == Fluids.WATER && waterNow > 0, "Reloaded plant water is " + data.inputTank.getFluid());
            check(data.outputTank.getFluid().getFluid() == MekanismFluids.BRINE.getFluid(), "Reloaded plant brine is " + data.outputTank.getFluid());
            check(waterNow + brineNow * 10 == water[0] + brine[0] * 10,
                  "Reloaded plant has " + waterNow + " water and " + brineNow + " brine from " + water[0] + " and " + brine[0]);
            check(data.getTemperature() > HeatAPI.AMBIENT_TEMP + 50, "Reloaded plant temperature is " + data.getTemperature());
            check(TestPlatform.blockTemperature(restored, plant.valveSide()) == data.getTemperature(),
                  "The heat lookup reports " + TestPlatform.blockTemperature(restored, plant.valveSide()) + " after reload");
            check(TestPlatform.blockFluid(restored, plant.valveSide()) == waterNow + brineNow,
                  "The fluid lookup reports " + TestPlatform.blockFluid(restored, plant.valveSide()) + " after reload");
            helper.succeed();
        });
    }

    /**
     * The multiplier {@link EvaporationMultiblockData} applies to the recipe: temperature above the heat API's ambient,
     * the configured ratio, and height against the 18-block maximum. The temperature is capped at
     * {@link EvaporationMultiblockData#MAX_MULTIPLIER_TEMP}.
     */
    private static double tempMultiplier(EvaporationMultiblockData data) {
        double temperature = Math.min(EvaporationMultiblockData.MAX_MULTIPLIER_TEMP, data.getTemperature());
        return (temperature - HeatAPI.AMBIENT_TEMP) * MekanismConfig.general.evaporationTempMultiplier.get()
              * ((double) data.height() / EvaporationMultiblockData.MAX_HEIGHT);
    }

    /**
     * Edges are thermal evaporation blocks. The floor is too. Wall centres are structural glass, except the north-face
     * centre, which is the valve, and the south-face centre, which is the controller when {@code controller} is set.
     * The roof's centre stays air when {@code openTop} is set; that opening is what the plant requires.
     */
    private static Plant place(GameTestHelper helper, BlockPos origin, int size, int height, boolean controller, boolean openTop) {
        Block frame = MekanismBlocks.THERMAL_EVAPORATION_BLOCK.getBlock();
        Block glass = MekanismBlocks.STRUCTURAL_GLASS.getBlock();
        Block valveBlock = MekanismBlocks.THERMAL_EVAPORATION_VALVE.getBlock();
        BlockPos valve = origin.offset(1, 1, 0);
        BlockPos controllerPos = origin.offset(1, 1, size - 1);
        List<BlockPos> blocks = new ArrayList<>();
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < size; z++) {
                    int matches = 0;
                    if (x == 0 || x == size - 1) {
                        matches++;
                    }
                    if (y == 0 || y == height - 1) {
                        matches++;
                    }
                    if (z == 0 || z == size - 1) {
                        matches++;
                    }
                    if (matches == 0 || (openTop && y == height - 1 && matches == 1)) {
                        continue;
                    }
                    BlockPos pos = origin.offset(x, y, z);
                    if (pos.equals(valve)) {
                        helper.setBlock(pos, valveBlock);
                    } else if (controller && pos.equals(controllerPos)) {
                        BlockState state = MekanismBlocks.THERMAL_EVAPORATION_CONTROLLER.getBlock().defaultBlockState()
                              .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
                        helper.setBlock(pos, state);
                    } else if (matches >= 2 || y == 0) {
                        helper.setBlock(pos, frame);
                    } else {
                        helper.setBlock(pos, glass);
                    }
                    blocks.add(pos);
                }
            }
        }
        return new Plant(origin, valve, Direction.NORTH, controllerPos, List.copyOf(blocks));
    }

    private static SavedBlock save(GameTestHelper helper, BlockPos pos) {
        return new SavedBlock(pos, helper.getBlockState(pos), helper.getBlockEntity(pos).saveWithFullMetadata());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean isActive(BlockState state) {
        Property active = state.getBlock().getStateDefinition().getProperty("active");
        return active != null && Boolean.TRUE.equals(state.getValue(active));
    }

    /** The shell stayed unformed, and its blocks were not absorbed by a neighbour. */
    private static void checkSeparate(TileEntityThermalEvaporationBlock tile, int blocks, String name) {
        check(!tile.getMultiblock().isFormed(), name + " formed");
        check(tile.getStructure().size() == blocks, name + " shares a structure of " + tile.getStructure().size() + " blocks");
    }

    private static TileEntityThermalEvaporationValve valve(GameTestHelper helper, Plant plant) {
        BlockEntity blockEntity = helper.getBlockEntity(plant.valve());
        check(blockEntity instanceof TileEntityThermalEvaporationValve, "The valve is " + blockEntity);
        return (TileEntityThermalEvaporationValve) blockEntity;
    }

    private static EvaporationMultiblockData formed(TileEntityThermalEvaporationBlock tile) {
        EvaporationMultiblockData data = tile.getMultiblock();
        check(data.isFormed(), "The thermal evaporation plant is not formed");
        return data;
    }

    private static TileEntityThermalEvaporationBlock master(GameTestHelper helper, Plant plant) {
        TileEntityThermalEvaporationBlock master = null;
        for (BlockPos pos : plant.blocks()) {
            BlockEntity blockEntity = helper.getBlockEntity(pos);
            if (blockEntity instanceof TileEntityThermalEvaporationBlock tile && tile.isMaster()) {
                check(master == null, "The plant has two masters");
                master = tile;
            }
        }
        check(master != null, "The formed plant has no master");
        return master;
    }

    private record Plant(BlockPos origin, BlockPos valve, Direction valveSide, BlockPos controller, List<BlockPos> blocks) {
    }

    private record SavedBlock(BlockPos pos, BlockState state, CompoundTag tag) {
    }
}

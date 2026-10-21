/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import static mekanism.gametest.CoreMachineGameTests.TEMPLATE;
import static mekanism.gametest.CoreMachineGameTests.check;

import java.util.ArrayList;
import java.util.List;
import mekanism.api.chemical.gas.GasStack;
import mekanism.common.Mekanism;
import mekanism.common.block.attribute.AttributeStateBoilerValveMode;
import mekanism.common.block.attribute.AttributeStateBoilerValveMode.BoilerValveMode;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.boiler.BoilerMultiblockData;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.registries.MekanismGases;
import mekanism.common.tile.multiblock.TileEntityBoilerCasing;
import mekanism.common.tile.multiblock.TileEntityBoilerValve;
import mekanism.common.util.HeatUtils;
import mekanism.gametest.MultiblockStructures.Shell;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;

/**
 * A 3×5×3 thermoelectric boiler: one superheating element under a one-block disperser plane, one valve. Water and
 * superheated sodium go in through the valve, and the boil rate is the heat the sodium deposited. External heat through
 * the casing's heat handler boils water on its own. Steam comes back out through the gas handler once the valve is set
 * to output steam.
 */
public class ThermoelectricBoilerGameTests {

    private static final BlockPos ORIGIN = new BlockPos(1, 1, 1);
    private static final int SIZE_XZ = 3;
    private static final int SIZE_Y = 5;
    /** Shell blocks of a 3×5×3 minus the three interior cells. */
    private static final int SHELL_BLOCKS = SIZE_XZ * SIZE_Y * SIZE_XZ - (SIZE_XZ - 2) * (SIZE_Y - 2) * (SIZE_XZ - 2);
    /** Frame below the disperser, plus the casing ring around the superheating element. */
    private static final int WATER_VOLUME = SIZE_XZ * SIZE_XZ + (SIZE_XZ * SIZE_XZ - 1);
    /** The air row above the disperser and the top casing row the steam tank counts. */
    private static final int STEAM_VOLUME = SIZE_XZ * SIZE_XZ * 2;
    private static final long HEATED_SODIUM = 50_000;
    private static final double EXTERNAL_HEAT = 1_000_000;

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void thermoelectricBoilerForms(GameTestHelper helper) {
        Boiler boiler = place(helper, ORIGIN);
        helper.runAtTickTime(40, () -> {
            TileEntityBoilerValve valve = valve(helper, boiler);
            BoilerMultiblockData data = formed(valve);
            check(data.length() == SIZE_XZ && data.width() == SIZE_XZ && data.height() == SIZE_Y,
                  "Formed boiler bounds are " + data.length() + "x" + data.height() + "x" + data.width());
            check(data.superheatingElements == 1, "Formed boiler has " + data.superheatingElements + " superheating elements");
            check(data.getWaterVolume() == WATER_VOLUME, "Water volume is " + data.getWaterVolume());
            check(data.getSteamVolume() == STEAM_VOLUME, "Steam volume is " + data.getSteamVolume());
            check(data.waterTank.getCapacity() == WATER_VOLUME * MekanismConfig.general.boilerWaterPerTank.get(),
                  "Water capacity is " + data.waterTank.getCapacity());
            check(data.steamTank.getCapacity() == STEAM_VOLUME * MekanismConfig.general.boilerSteamPerTank.get(),
                  "Steam capacity is " + data.steamTank.getCapacity());
            check(data.superheatedCoolantTank.getCapacity() == (long) WATER_VOLUME * MekanismConfig.general.boilerHeatedCoolantPerTank.get(),
                  "Heated coolant capacity is " + data.superheatedCoolantTank.getCapacity());
            check(data.cooledCoolantTank.getCapacity() == (long) STEAM_VOLUME * MekanismConfig.general.boilerCooledCoolantPerTank.get(),
                  "Cooled coolant capacity is " + data.cooledCoolantTank.getCapacity());
            check(data.heatCapacitor.getHeatCapacity() == BoilerMultiblockData.CASING_HEAT_CAPACITY * SHELL_BLOCKS,
                  "Heat capacity is " + data.heatCapacitor.getHeatCapacity());
            check(data.locations.size() == SHELL_BLOCKS, "The shell has " + data.locations.size() + " blocks");
            check(data.getValveData().size() == 1, "Formed boiler has " + data.getValveData().size() + " valves");
            BlockPos upper = helper.absolutePos(boiler.shell().origin().offset(0, 3, 0));
            check(upper.equals(data.upperRenderLocation), "Steam render origin is " + data.upperRenderLocation + ", expected " + upper);
            check(helper.getBlockState(boiler.shell().valve()).getValue(AttributeStateBoilerValveMode.modeProperty) == BoilerValveMode.INPUT,
                  "A new valve is not in input mode");
            TileEntityBoilerCasing master = master(helper, boiler.shell());
            BlockPos min = helper.absolutePos(boiler.shell().origin());
            BlockPos max = min.offset(SIZE_XZ - 1, SIZE_Y - 1, SIZE_XZ - 1);
            AABB bounds = TestPlatform.renderBounds(master);
            AABB structure = new AABB(min, max.offset(1, 1, 1));
            check(bounds.equals(structure), "Master render bounds " + bounds + " are not the structure " + structure);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void invalidBoilerShapesDoNotForm(GameTestHelper helper) {
        Boiler holed = place(helper, ORIGIN);
        helper.setBlock(holed.shell().origin(), Blocks.AIR);
        BlockPos missingDisperser = new BlockPos(4, 1, 1);
        helper.setBlock(missingDisperser.offset(1, 1, 1), MekanismBlocks.SUPERHEATING_ELEMENT.getBlock());
        Shell noDisperser = shell(helper, missingDisperser);
        BlockPos elementAbove = new BlockPos(1, 1, 4);
        helper.setBlock(elementAbove.offset(1, 1, 1), MekanismBlocks.PRESSURE_DISPERSER.getBlock());
        helper.setBlock(elementAbove.offset(1, 2, 1), MekanismBlocks.SUPERHEATING_ELEMENT.getBlock());
        Shell above = shell(helper, elementAbove);
        Shell small = MultiblockStructures.cuboid(helper, new BlockPos(4, 1, 4), 2, 2, 2, MekanismBlocks.BOILER_CASING.getBlock(),
              MekanismBlocks.BOILER_CASING.getBlock(), MekanismBlocks.BOILER_VALVE.getBlock());
        helper.runAtTickTime(40, () -> {
            TileEntityBoilerValve valve = valve(helper, holed);
            check(!valve.getMultiblock().isFormed(), "A boiler with a missing corner formed");
            check(TestPlatform.insertBlockFluid(valve, holed.shell().valveSide(), Fluids.WATER, 1_000, true) == 0,
                  "An unformed valve accepted water");
            check(TestPlatform.insertBlockGas(valve, holed.shell().valveSide(), MekanismGases.SUPERHEATED_SODIUM.get(), 1_000, true) == 0,
                  "An unformed valve accepted heated coolant");
            check(TestPlatform.heatCapacitors(valve, holed.shell().valveSide()) == 0, "An unformed valve exposes a heat handler");
            check(!valve(helper, noDisperser).getMultiblock().isFormed(), "A boiler with no disperser plane formed");
            check(!valve(helper, above).getMultiblock().isFormed(), "A boiler with its superheating element above the disperser formed");
            BlockEntity casing = helper.getBlockEntity(small.origin());
            check(casing instanceof TileEntityBoilerCasing, "The undersized shell has no boiler block");
            check(!((TileEntityBoilerCasing) casing).getMultiblock().isFormed(), "A 2x2x2 boiler formed");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void heatedSodiumBoilsWaterAtTheConfiguredRate(GameTestHelper helper) {
        Boiler boiler = place(helper, ORIGIN);
        long[] water = new long[1];
        boolean[] outputting = new boolean[1];
        helper.runAtTickTime(40, () -> {
            TileEntityBoilerValve valve = valve(helper, boiler);
            BoilerMultiblockData data = formed(valve);
            Direction side = boiler.shell().valveSide();
            BlockEntity casing = helper.getBlockEntity(boiler.shell().origin());
            int room = data.waterTank.getCapacity();
            check(room > HEATED_SODIUM, "The boiler holds " + room + " mB of water");
            check(TestPlatform.insertBlockFluid(casing, Direction.WEST, Fluids.WATER, 1_000, true) == 0, "A casing block accepted water");
            check(TestPlatform.insertBlockGas(casing, Direction.WEST, MekanismGases.SUPERHEATED_SODIUM.get(), 1_000, true) == 0,
                  "A casing block accepted heated coolant");
            check(TestPlatform.insertBlockFluid(valve, side, Fluids.LAVA, 1_000, true) == 0, "The valve accepted lava");
            check(TestPlatform.insertBlockGas(valve, side, MekanismGases.HYDROGEN.get(), 1_000, true) == 0, "The valve accepted hydrogen");
            check(TestPlatform.insertBlockGas(valve, side, MekanismGases.SODIUM.get(), 1_000, true) == 0, "The valve accepted cooled sodium");
            check(TestPlatform.insertBlockFluid(valve, side, Fluids.WATER, room, false) == room, "A simulated fill offered less than the water tank");
            check(data.waterTank.isEmpty(), "A simulated fill stored water");
            check(TestPlatform.insertBlockGas(valve, side, MekanismGases.SUPERHEATED_SODIUM.get(), HEATED_SODIUM, false) == HEATED_SODIUM,
                  "A simulated fill offered less than the heated coolant tank");
            check(data.superheatedCoolantTank.isEmpty(), "A simulated fill stored heated coolant");
            water[0] = TestPlatform.insertBlockFluid(valve, side, Fluids.WATER, room, true);
            check(water[0] == room, "The valve accepted " + water[0] + " of " + room + " mB");
            long sodium = TestPlatform.insertBlockGas(valve, side, MekanismGases.SUPERHEATED_SODIUM.get(), HEATED_SODIUM, true);
            check(sodium == HEATED_SODIUM, "The valve accepted " + sodium + " of " + HEATED_SODIUM + " heated sodium");
            check(data.superheatedCoolantTank.getStack().getType() == MekanismGases.SUPERHEATED_SODIUM.get(),
                  "The heated tank holds " + data.superheatedCoolantTank.getStack().getType());
            check(TestPlatform.blockFluid(valve, side) == room, "The fluid lookup reports " + TestPlatform.blockFluid(valve, side));
            check(TestPlatform.gasTanks(valve, side) == 3, "The gas lookup exposes " + TestPlatform.gasTanks(valve, side) + " tanks");
            check(TestPlatform.blockGas(valve, side) == HEATED_SODIUM, "The gas lookup reports " + TestPlatform.blockGas(valve, side));
        });
        helper.succeedWhen(() -> {
            check(water[0] > 0, "Water has not been inserted");
            TileEntityBoilerValve valve = valve(helper, boiler);
            BoilerMultiblockData data = formed(valve);
            Direction side = boiler.shell().valveSide();
            int expected = expectedBoil(data);
            check(data.lastBoilRate > 0 && data.lastBoilRate == expected && data.lastMaxBoil == expected,
                  "Boil rate " + data.lastBoilRate + " (max " + data.lastMaxBoil + ") is not the " + expected + " mB the heat allows");
            check(data.waterTank.getFluidAmount() > 0, "The boil was limited by the water tank");
            check(data.steamTank.getNeeded() > 0, "The boil was limited by the steam tank");
            check(data.steamTank.getStack().getType() == MekanismGases.STEAM.get() && data.steamTank.getStored() > 0,
                  "The steam tank holds " + data.steamTank.getStack());
            check(data.cooledCoolantTank.getStored() > 0 && data.cooledCoolantTank.getStack().getType() == MekanismGases.SODIUM.get(),
                  "Cooled coolant is " + data.cooledCoolantTank.getStack());
            check(data.steamTank.getStored() > 100, "The boiler has " + data.steamTank.getStored() + " mB of steam");
            if (!outputting[0]) {
                check(data.waterTank.getFluidAmount() + data.steamTank.getStored() == water[0],
                      "Water plus steam is " + (data.waterTank.getFluidAmount() + data.steamTank.getStored()) + " from " + water[0]);
                long reported = TestPlatform.blockGas(valve, side);
                long stored = data.steamTank.getStored() + data.superheatedCoolantTank.getStored() + data.cooledCoolantTank.getStored();
                check(reported == stored, "The gas lookup reports " + reported + " against " + stored + " stored");
                check(TestPlatform.extractGas(valve, side, 100, true).isEmpty(), "An input valve gave up steam");
                setMode(helper, boiler.shell().valve(), BoilerValveMode.OUTPUT_STEAM);
                outputting[0] = true;
            }
            GasStack steam = TestPlatform.extractGas(valve, side, 100, true);
            check(steam.getType() == MekanismGases.STEAM.get() && steam.getAmount() == 100, "The output valve returned " + steam);
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void externalHeatBoilsWater(GameTestHelper helper) {
        Boiler boiler = place(helper, ORIGIN);
        double[] ambient = new double[1];
        boolean[] outputting = new boolean[1];
        helper.runAtTickTime(40, () -> {
            TileEntityBoilerValve valve = valve(helper, boiler);
            BoilerMultiblockData data = formed(valve);
            Direction side = boiler.shell().valveSide();
            BlockEntity casing = helper.getBlockEntity(boiler.shell().origin());
            ambient[0] = data.heatCapacitor.getTemperature();
            check(TestPlatform.heatCapacitors(casing, Direction.WEST) == 1, "The casing exposes no heat handler");
            check(TestPlatform.blockTemperature(casing, Direction.WEST) == ambient[0],
                  "The heat lookup reports " + TestPlatform.blockTemperature(casing, Direction.WEST) + " against " + ambient[0]);
            long filled = TestPlatform.insertBlockFluid(valve, side, Fluids.WATER, 100_000, true);
            check(filled == 100_000, "The valve accepted " + filled + " mB");
            TestPlatform.handleBlockHeat(casing, Direction.WEST, EXTERNAL_HEAT);
            check(data.heatCapacitor.getTemperature() == ambient[0], "Queued heat changed the temperature before the boiler ticked");
            check(data.superheatedCoolantTank.isEmpty() && data.cooledCoolantTank.isEmpty(), "The heat test started with coolant");
        });
        helper.succeedWhen(() -> {
            check(!Double.isNaN(ambient[0]), "Heat has not been inserted");
            TileEntityBoilerValve valve = valve(helper, boiler);
            BoilerMultiblockData data = formed(valve);
            Direction side = boiler.shell().valveSide();
            double temperature = data.heatCapacitor.getTemperature();
            check(temperature > ambient[0] + 50, "External heat left the boiler at " + temperature);
            check(TestPlatform.blockTemperature(valve, side) == temperature, "The valve heat lookup reports " + TestPlatform.blockTemperature(valve, side));
            int expected = expectedBoil(data);
            check(data.lastBoilRate > 0 && data.lastBoilRate == expected,
                  "Boil rate " + data.lastBoilRate + " is not the " + expected + " mB the external heat allows");
            check(data.waterTank.getFluidAmount() > 0 && data.steamTank.getNeeded() > 0, "The boil was limited by a tank");
            check(data.cooledCoolantTank.isEmpty(), "External heat produced coolant");
            check(data.steamTank.getStack().getType() == MekanismGases.STEAM.get() && data.steamTank.getStored() > 100,
                  "The steam tank holds " + data.steamTank.getStack());
            if (!outputting[0]) {
                check(TestPlatform.extractGas(valve, side, 100, true).isEmpty(), "An input valve gave up steam");
                setMode(helper, boiler.shell().valve(), BoilerValveMode.OUTPUT_STEAM);
                outputting[0] = true;
            }
            GasStack steam = TestPlatform.extractGas(valve, side, 100, true);
            check(steam.getType() == MekanismGases.STEAM.get() && steam.getAmount() == 100, "The output valve returned " + steam);
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void boilerContentsSurviveSaveAndLoad(GameTestHelper helper) {
        Boiler boiler = place(helper, ORIGIN);
        long[] water = new long[1];
        long[] steam = new long[1];
        long[] heated = new long[1];
        long[] cooled = new long[1];
        helper.runAtTickTime(40, () -> {
            TileEntityBoilerValve valve = valve(helper, boiler);
            BoilerMultiblockData data = formed(valve);
            Direction side = boiler.shell().valveSide();
            int room = data.waterTank.getCapacity();
            long filled = TestPlatform.insertBlockFluid(valve, side, Fluids.WATER, room, true);
            check(filled == room, "The valve accepted " + filled + " before the save");
            long sodium = TestPlatform.insertBlockGas(valve, side, MekanismGases.SUPERHEATED_SODIUM.get(), HEATED_SODIUM, true);
            check(sodium == HEATED_SODIUM, "The valve accepted " + sodium + " heated sodium before the save");
        });
        helper.runAtTickTime(70, () -> {
            TileEntityBoilerValve valve = valve(helper, boiler);
            BoilerMultiblockData data = formed(valve);
            check(data.steamTank.getStored() > 0, "The boiler produced no steam before the save");
            check(data.cooledCoolantTank.getStored() > 0, "The boiler cooled no sodium before the save");
            check(data.heatCapacitor.getTemperature() > HeatUtils.BASE_BOIL_TEMP - 1, "The boiler was at " + data.heatCapacitor.getTemperature());
            setMode(helper, boiler.shell().valve(), BoilerValveMode.OUTPUT_STEAM);
            water[0] = data.waterTank.getFluidAmount();
            steam[0] = data.steamTank.getStored();
            heated[0] = data.superheatedCoolantTank.getStored();
            cooled[0] = data.cooledCoolantTank.getStored();
            List<SavedBlock> saved = new ArrayList<>();
            for (BlockPos pos : boiler.shell().blocks()) {
                saved.add(save(helper, pos));
            }
            saved.add(save(helper, boiler.element()));
            saved.add(save(helper, boiler.disperser()));
            Mekanism.boilerManager.inventories.remove(data.inventoryID);
            for (SavedBlock block : saved) {
                helper.setBlock(block.pos, Blocks.AIR);
            }
            for (SavedBlock block : saved) {
                helper.setBlock(block.pos, block.state);
                helper.getBlockEntity(block.pos).load(block.tag);
            }
        });
        helper.runAtTickTime(120, () -> {
            TileEntityBoilerValve restored = valve(helper, boiler);
            BoilerMultiblockData data = formed(restored);
            long fluids = data.waterTank.getFluidAmount() + data.steamTank.getStored();
            long coolants = data.superheatedCoolantTank.getStored() + data.cooledCoolantTank.getStored();
            check(data.waterTank.isEmpty() || data.waterTank.getFluid().getFluid() == Fluids.WATER, "Reloaded boiler water is " + data.waterTank.getFluid());
            check(data.steamTank.getStack().getType() == MekanismGases.STEAM.get(), "Reloaded boiler steam is " + data.steamTank.getStack());
            check(fluids == water[0] + steam[0], "Reloaded boiler has " + fluids + " mB of water and steam from " + (water[0] + steam[0]));
            check(coolants == heated[0] + cooled[0], "Reloaded boiler has " + coolants + " coolant from " + (heated[0] + cooled[0]));
            check(data.cooledCoolantTank.getStack().getType() == MekanismGases.SODIUM.get(), "Reloaded cooled coolant is " + data.cooledCoolantTank.getStack());
            check(data.heatCapacitor.getTemperature() > HeatUtils.BASE_BOIL_TEMP - 2, "Reloaded boiler temperature is " + data.heatCapacitor.getTemperature());
            check(TestPlatform.blockTemperature(restored, boiler.shell().valveSide()) == data.heatCapacitor.getTemperature(),
                  "The heat lookup reports " + TestPlatform.blockTemperature(restored, boiler.shell().valveSide()) + " after reload");
            check(helper.getBlockState(boiler.shell().valve()).getValue(AttributeStateBoilerValveMode.modeProperty) == BoilerValveMode.OUTPUT_STEAM,
                  "Reloaded valve mode is " + helper.getBlockState(boiler.shell().valve()).getValue(AttributeStateBoilerValveMode.modeProperty));
            helper.succeed();
        });
    }

    /**
     * The boil rate {@link BoilerMultiblockData} stored for the temperature it is at now. Heat queued for the next tick
     * is not part of that temperature, which is also how the boiler reads it.
     */
    private static int expectedBoil(BoilerMultiblockData data) {
        double heatAvailable = (data.heatCapacitor.getTemperature() - HeatUtils.BASE_BOIL_TEMP)
              * (data.heatCapacitor.getHeatCapacity() * MekanismConfig.general.boilerWaterConductivity.get());
        heatAvailable = Math.min(heatAvailable, MekanismConfig.general.superheatingHeatTransfer.get() * data.superheatingElements);
        return (int) Math.floor(HeatUtils.getSteamEnergyEfficiency() * heatAvailable / HeatUtils.getWaterThermalEnthalpy());
    }

    private static void setMode(GameTestHelper helper, BlockPos valve, BoilerValveMode mode) {
        BlockState state = helper.getBlockState(valve);
        helper.getLevel().setBlock(helper.absolutePos(valve), state.setValue(AttributeStateBoilerValveMode.modeProperty, mode), Block.UPDATE_ALL);
    }

    /**
     * The disperser is the middle interior row. The superheating element is the interior cell under it, and the valve is
     * the centre of the north face.
     */
    private static Boiler place(GameTestHelper helper, BlockPos origin) {
        BlockPos element = origin.offset(1, 1, 1);
        BlockPos disperser = origin.offset(1, 2, 1);
        helper.setBlock(element, MekanismBlocks.SUPERHEATING_ELEMENT.getBlock());
        helper.setBlock(disperser, MekanismBlocks.PRESSURE_DISPERSER.getBlock());
        return new Boiler(shell(helper, origin), element, disperser);
    }

    private static Shell shell(GameTestHelper helper, BlockPos origin) {
        return MultiblockStructures.cuboid(helper, origin, SIZE_XZ, SIZE_Y, SIZE_XZ, MekanismBlocks.BOILER_CASING.getBlock(),
              MekanismBlocks.BOILER_CASING.getBlock(), MekanismBlocks.BOILER_VALVE.getBlock());
    }

    private static SavedBlock save(GameTestHelper helper, BlockPos pos) {
        return new SavedBlock(pos, helper.getBlockState(pos), helper.getBlockEntity(pos).saveWithFullMetadata());
    }

    private static TileEntityBoilerValve valve(GameTestHelper helper, Boiler boiler) {
        return valve(helper, boiler.shell());
    }

    private static TileEntityBoilerValve valve(GameTestHelper helper, Shell shell) {
        check(shell.valve() != null, "The shell has no valve");
        BlockEntity blockEntity = helper.getBlockEntity(shell.valve());
        check(blockEntity instanceof TileEntityBoilerValve, "The valve is " + blockEntity);
        return (TileEntityBoilerValve) blockEntity;
    }

    private static BoilerMultiblockData formed(TileEntityBoilerCasing tile) {
        BoilerMultiblockData data = tile.getMultiblock();
        check(data.isFormed(), "The thermoelectric boiler is not formed");
        return data;
    }

    private static TileEntityBoilerCasing master(GameTestHelper helper, Shell shell) {
        TileEntityBoilerCasing master = null;
        for (BlockPos pos : shell.blocks()) {
            BlockEntity blockEntity = helper.getBlockEntity(pos);
            if (blockEntity instanceof TileEntityBoilerCasing casing && casing.isMaster()) {
                check(master == null, "The boiler has two masters");
                master = casing;
            }
        }
        check(master != null, "The formed boiler has no master");
        return master;
    }

    private record Boiler(Shell shell, BlockPos element, BlockPos disperser) {
    }

    private record SavedBlock(BlockPos pos, BlockState state, CompoundTag tag) {
    }
}

/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import static mekanism.gametest.CoreMachineGameTests.TEMPLATE;
import static mekanism.gametest.CoreMachineGameTests.check;

import java.util.ArrayList;
import java.util.List;
import mekanism.api.math.FloatingLong;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.registries.MekanismGases;
import mekanism.common.tile.TileEntityChemicalTank.GasMode;
import mekanism.gametest.MultiblockStructures.Shell;
import mekanism.generators.common.MekanismGenerators;
import mekanism.generators.common.config.MekanismGeneratorsConfig;
import mekanism.generators.common.content.turbine.TurbineMultiblockData;
import mekanism.generators.common.registries.GeneratorsBlocks;
import mekanism.generators.common.tile.turbine.TileEntityTurbineCasing;
import mekanism.generators.common.tile.turbine.TileEntityTurbineRotor;
import mekanism.generators.common.tile.turbine.TileEntityTurbineValve;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;

/**
 * A 5×6×5 industrial turbine: two rotors, the lower one holding two blades, the disperser ring, one coil, one
 * condenser, one valve and one vent. Steam goes in through the gas handler (Forge's capability, Fabric's
 * {@code mekanism:gas_handler} lookup, the same lookup Extreme Reactors uses to feed a neighbour). Energy comes out
 * through Forge Energy or Team Reborn Energy. The condenser leaves water in the vent.
 */
public class IndustrialTurbineGameTests {

    private static final BlockPos ORIGIN = new BlockPos(1, 1, 1);
    private static final int SIZE_XZ = 5;
    private static final int SIZE_Y = 6;
    /** Rotors below the complex. One vent caps flow, so the extra rotor is steam capacity for the save/load wait. */
    private static final int ROTORS = 2;
    private static final FloatingLong STORED_ENERGY = FloatingLong.createConst(50_000_000);

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void industrialTurbineForms(GameTestHelper helper) {
        Turbine turbine = place(helper);
        helper.runAtTickTime(40, () -> {
            TileEntityTurbineValve valve = valve(helper, turbine);
            TurbineMultiblockData data = formed(valve);
            check(data.length() == SIZE_XZ && data.width() == SIZE_XZ && data.height() == SIZE_Y,
                  "Formed turbine bounds are " + data.length() + "x" + data.height() + "x" + data.width());
            check(data.blades == 2, "Formed turbine has " + data.blades + " blades");
            check(data.coils == 1, "Formed turbine has " + data.coils + " coils");
            check(data.condensers == 1, "Formed turbine has " + data.condensers + " condensers");
            check(data.vents == 1, "Formed turbine has " + data.vents + " vents");
            check(data.getDispersers() == 8, "Formed turbine counts " + data.getDispersers() + " dispersers");
            check(data.lowerVolume == SIZE_XZ * SIZE_XZ * ROTORS, "Steam cavity is " + data.lowerVolume);
            check(data.getValveData().size() == 1, "Formed turbine has " + data.getValveData().size() + " valves");
            TileEntityTurbineRotor rotor = rotor(helper, turbine.rotor());
            check(rotor.getHousedBlades() == 2, "Rotor houses " + rotor.getHousedBlades() + " blades");
            BlockPos rotorWorld = rotor.getBlockPos();
            AABB blades = new AABB(rotorWorld.offset(-1, 0, -1), rotorWorld.offset(2, 1, 2));
            AABB rotorBounds = TestPlatform.renderBounds(rotor);
            check(rotorBounds.equals(blades), "Rotor render bounds " + rotorBounds + " are not the blade box " + blades);
            TileEntityTurbineCasing master = master(helper, turbine.shell());
            BlockPos min = helper.absolutePos(turbine.shell().origin());
            BlockPos max = min.offset(SIZE_XZ - 1, SIZE_Y - 1, SIZE_XZ - 1);
            AABB bounds = TestPlatform.renderBounds(master);
            AABB structure = new AABB(min, max.offset(1, 1, 1));
            check(bounds.equals(structure), "Master render bounds " + bounds + " are not the structure " + structure);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void shellWithoutInternalsDoesNotForm(GameTestHelper helper) {
        Shell shell = MultiblockStructures.cuboid(helper, ORIGIN, SIZE_XZ, SIZE_Y, SIZE_XZ, GeneratorsBlocks.TURBINE_CASING.getBlock(),
              GeneratorsBlocks.TURBINE_CASING.getBlock(), GeneratorsBlocks.TURBINE_VALVE.getBlock());
        helper.runAtTickTime(40, () -> {
            TileEntityTurbineValve valve = valve(helper, shell);
            check(!valve.getMultiblock().isFormed(), "A turbine shell with no complex formed");
            check(TestPlatform.insertBlockGas(valve, shell.valveSide(), MekanismGases.STEAM.get(), 1_000, true) == 0,
                  "An unformed valve accepted steam");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void turbineConsumesSteamAndEmitsEnergy(GameTestHelper helper) {
        Turbine turbine = place(helper);
        long[] filled = new long[1];
        helper.runAtTickTime(40, () -> {
            TileEntityTurbineValve valve = valve(helper, turbine);
            TurbineMultiblockData data = formed(valve);
            BlockEntity casing = helper.getBlockEntity(turbine.shell().origin());
            long capacity = data.gasTank.getCapacity();
            check(capacity > 0, "The formed turbine has no steam capacity");
            check(TestPlatform.insertBlockGas(casing, turbine.shell().valveSide(), MekanismGases.STEAM.get(), capacity, true) == 0,
                  "A casing block accepted steam");
            check(TestPlatform.insertBlockGas(valve, turbine.shell().valveSide(), MekanismGases.HYDROGEN.get(), 1_000, true) == 0,
                  "The valve accepted hydrogen");
            long offered = TestPlatform.insertBlockGas(valve, turbine.shell().valveSide(), MekanismGases.STEAM.get(), capacity, false);
            check(offered == capacity, "A simulated fill offered " + offered + " of " + capacity);
            check(TestPlatform.blockGas(valve, turbine.shell().valveSide()) == 0, "A simulated fill stored steam");
            filled[0] = TestPlatform.insertBlockGas(valve, turbine.shell().valveSide(), MekanismGases.STEAM.get(), capacity, true);
            check(filled[0] == capacity, "The valve accepted " + filled[0] + " of " + capacity);
            check(TestPlatform.blockGas(valve, turbine.shell().valveSide()) == capacity,
                  "The gas lookup reports " + TestPlatform.blockGas(valve, turbine.shell().valveSide()));
            check(data.gasTank.getStack().getType() == MekanismGases.STEAM.get(), "The tank holds " + data.gasTank.getStack().getType());
        });
        helper.succeedWhen(() -> {
            check(filled[0] > 0, "Steam has not been inserted");
            TileEntityTurbineValve valve = valve(helper, turbine);
            TurbineMultiblockData data = formed(valve);
            long stored = TestPlatform.blockGas(valve, turbine.shell().valveSide());
            check(stored < filled[0], "The turbine did not consume steam: " + stored);
            check(data.gasTank.getStack().getType() == MekanismGases.STEAM.get(), "The tank holds " + data.gasTank.getStack().getType());
            BlockEntity casing = helper.getBlockEntity(turbine.shell().origin());
            FloatingLong before = data.energyContainer.getEnergy().copy();
            check(before.greaterThan(FloatingLong.ZERO), "The turbine stored no energy");
            long refused = TestPlatform.extractEnergy(casing, turbine.shell().valveSide(), 100_000);
            check(refused == 0, "A casing block gave " + refused + " energy");
            check(data.energyContainer.getEnergy().equals(before), "A refused extraction changed stored energy");
            long extracted = TestPlatform.extractEnergy(valve, turbine.shell().valveSide(), 100_000);
            check(extracted > 0, "The valve gave no energy through the loader API; stored " + before);
            check(data.energyContainer.getEnergy().smallerThan(before), "Extracted energy is still stored");
            long water = TestPlatform.blockFluid(helper.getBlockEntity(turbine.vent()), turbine.ventSide());
            check(water > 0, "The condenser left no water in the vent");
            check(data.ventTank.getFluid().getFluid() == Fluids.WATER, "The vent tank holds " + data.ventTank.getFluid().getFluid());
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void turbineContentsSurviveSaveAndLoad(GameTestHelper helper) {
        Turbine turbine = place(helper);
        long[] steam = new long[1];
        helper.runAtTickTime(40, () -> {
            TileEntityTurbineValve valve = valve(helper, turbine);
            TurbineMultiblockData data = formed(valve);
            long capacity = data.gasTank.getCapacity();
            long filled = TestPlatform.insertBlockGas(valve, turbine.shell().valveSide(), MekanismGases.STEAM.get(), capacity, true);
            check(filled == capacity, "The valve accepted " + filled + " before the save");
            data.energyContainer.setEnergy(STORED_ENERGY);
            data.setDumpMode(GasMode.DUMPING_EXCESS);
            steam[0] = data.gasTank.getStored();
            List<SavedBlock> saved = new ArrayList<>();
            for (BlockPos pos : turbine.shell().blocks()) {
                saved.add(save(helper, pos));
            }
            for (BlockPos pos : turbine.internals()) {
                saved.add(save(helper, pos));
            }
            MekanismGenerators.turbineManager.inventories.remove(data.inventoryID);
            for (SavedBlock block : saved) {
                helper.setBlock(block.pos, Blocks.AIR);
            }
            for (SavedBlock block : saved) {
                helper.setBlock(block.pos, block.state);
                helper.getBlockEntity(block.pos).load(block.tag);
            }
        });
        helper.runAtTickTime(80, () -> {
            TileEntityTurbineValve restored = valve(helper, turbine);
            TurbineMultiblockData data = formed(restored);
            long stored = data.gasTank.getStored();
            long maxFlow = Math.round(MekanismGeneratorsConfig.generators.turbineVentGasFlow.get());
            // Dumping excess keeps 90%, and one vent burns at most its flow each tick of the wait.
            long burned = steam[0] / 10 + 50L * maxFlow;
            check(data.gasTank.getStack().getType() == MekanismGases.STEAM.get(), "Reloaded turbine holds " + data.gasTank.getStack().getType());
            check(stored > 0 && stored <= steam[0], "Reloaded turbine stores " + stored + " after saving " + steam[0]);
            check(stored >= steam[0] - burned, "Reloaded turbine lost more steam than it can burn: " + stored + " from " + steam[0]);
            check(data.dumpMode == GasMode.DUMPING_EXCESS, "Reloaded turbine dump mode is " + data.dumpMode);
            check(data.energyContainer.getEnergy().greaterOrEqual(STORED_ENERGY), "Reloaded turbine energy is " + data.energyContainer.getEnergy());
            check(data.blades == 2, "Reloaded turbine has " + data.blades + " blades");
            check(rotor(helper, turbine.rotor()).getHousedBlades() == 2, "Reloaded rotor lost its blades");
            check(TestPlatform.blockGas(restored, turbine.shell().valveSide()) == stored,
                  "The gas lookup reports " + TestPlatform.blockGas(restored, turbine.shell().valveSide()) + " after reload");
            helper.succeed();
        });
    }

    /**
     * Edges are casing. The north face centre is the valve and the south face centre is the vent, both level with the
     * complex. Rotors fill every block under the complex, and the blades sit on the bottom rotor.
     */
    private static Turbine place(GameTestHelper helper) {
        int center = SIZE_XZ / 2;
        int complexY = SIZE_Y / 2;
        BlockPos complex = ORIGIN.offset(center, complexY, center);
        List<BlockPos> internals = new ArrayList<>();
        BlockPos bladed = complex.below(ROTORS);
        for (int i = 1; i <= ROTORS; i++) {
            BlockPos rotor = complex.below(i);
            helper.setBlock(rotor, GeneratorsBlocks.TURBINE_ROTOR.getBlock());
            internals.add(rotor);
        }
        TileEntityTurbineRotor rotorTile = rotor(helper, bladed);
        check(rotorTile.addBlade(true), "The rotor refused the first blade");
        check(rotorTile.addBlade(true), "The rotor refused the second blade");
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) {
                    continue;
                }
                BlockPos disperser = complex.offset(x, 0, z);
                helper.setBlock(disperser, MekanismBlocks.PRESSURE_DISPERSER.getBlock());
                internals.add(disperser);
            }
        }
        helper.setBlock(complex, GeneratorsBlocks.ROTATIONAL_COMPLEX.getBlock());
        internals.add(complex);
        BlockPos coil = complex.above();
        helper.setBlock(coil, GeneratorsBlocks.ELECTROMAGNETIC_COIL.getBlock());
        internals.add(coil);
        BlockPos condenser = coil.offset(1, 0, 0);
        helper.setBlock(condenser, GeneratorsBlocks.SATURATING_CONDENSER.getBlock());
        internals.add(condenser);
        Shell shell = MultiblockStructures.cuboid(helper, ORIGIN, SIZE_XZ, SIZE_Y, SIZE_XZ, GeneratorsBlocks.TURBINE_CASING.getBlock(),
              GeneratorsBlocks.TURBINE_CASING.getBlock(), GeneratorsBlocks.TURBINE_VALVE.getBlock());
        BlockPos vent = ORIGIN.offset(center, complexY, SIZE_XZ - 1);
        helper.setBlock(vent, GeneratorsBlocks.TURBINE_VENT.getBlock());
        return new Turbine(shell, vent, Direction.SOUTH, bladed, List.copyOf(internals));
    }

    private static SavedBlock save(GameTestHelper helper, BlockPos pos) {
        return new SavedBlock(pos, helper.getBlockState(pos), helper.getBlockEntity(pos).saveWithFullMetadata());
    }

    private static TileEntityTurbineValve valve(GameTestHelper helper, Turbine turbine) {
        return valve(helper, turbine.shell());
    }

    private static TileEntityTurbineValve valve(GameTestHelper helper, Shell shell) {
        check(shell.valve() != null, "The shell has no valve");
        BlockEntity blockEntity = helper.getBlockEntity(shell.valve());
        check(blockEntity instanceof TileEntityTurbineValve, "The valve is " + blockEntity);
        return (TileEntityTurbineValve) blockEntity;
    }

    private static TileEntityTurbineRotor rotor(GameTestHelper helper, BlockPos pos) {
        BlockEntity blockEntity = helper.getBlockEntity(pos);
        check(blockEntity instanceof TileEntityTurbineRotor, "The rotor is " + blockEntity);
        return (TileEntityTurbineRotor) blockEntity;
    }

    private static TurbineMultiblockData formed(TileEntityTurbineCasing tile) {
        TurbineMultiblockData data = tile.getMultiblock();
        check(data.isFormed(), "The industrial turbine is not formed");
        return data;
    }

    private static TileEntityTurbineCasing master(GameTestHelper helper, Shell shell) {
        TileEntityTurbineCasing master = null;
        for (BlockPos pos : shell.blocks()) {
            BlockEntity blockEntity = helper.getBlockEntity(pos);
            if (blockEntity instanceof TileEntityTurbineCasing casing && casing.isMaster()) {
                check(master == null, "The turbine has two masters");
                master = casing;
            }
        }
        check(master != null, "The formed turbine has no master");
        return master;
    }

    private record Turbine(Shell shell, BlockPos vent, Direction ventSide, BlockPos rotor, List<BlockPos> internals) {
    }

    private record SavedBlock(BlockPos pos, BlockState state, CompoundTag tag) {
    }
}

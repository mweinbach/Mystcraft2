/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import static mekanism.gametest.CoreMachineGameTests.check;
import static mekanism.gametest.CoreMachineGameTests.succeedAfterScan;

import java.util.ArrayList;
import java.util.List;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.heat.HeatAPI;
import mekanism.api.math.FloatingLong;
import mekanism.common.lib.multiblock.IStructuralMultiblock;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.registries.MekanismGases;
import mekanism.common.tile.laser.TileEntityLaser;
import mekanism.common.tile.multiblock.TileEntityStructuralGlass;
import mekanism.common.util.HeatUtils;
import mekanism.gametest.MultiblockStructures.Shell;
import mekanism.generators.common.MekanismGenerators;
import mekanism.generators.common.config.MekanismGeneratorsConfig;
import mekanism.generators.common.content.fusion.FusionReactorMultiblockData;
import mekanism.generators.common.registries.GeneratorsBlocks;
import mekanism.generators.common.registries.GeneratorsGases;
import mekanism.generators.common.registries.GeneratorsItems;
import mekanism.generators.common.tile.fusion.TileEntityFusionReactorBlock;
import mekanism.generators.common.tile.fusion.TileEntityFusionReactorController;
import mekanism.generators.common.tile.fusion.TileEntityFusionReactorLogicAdapter;
import mekanism.generators.common.tile.fusion.TileEntityFusionReactorLogicAdapter.FusionReactorLogic;
import mekanism.generators.common.tile.fusion.TileEntityFusionReactorPort;
import mekanism.generators.common.tile.fusion.TileEntityLaserFocusMatrix;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;

/**
 * A 5×5×5 fusion reactor. The face grid matches {@code FusionReactorValidator}: corners and the twelve holes on a
 * face are air, frame slots are reactor frame, and the controller is the top centre. A laser focus matrix, one port
 * and a logic adapter fill other slots. Deuterium, tritium and D-T fuel enter through the port's gas handler. A full
 * hohlraum plus laser heat on the focus matrix ignites the burn. Water becomes steam and the casing's heat becomes
 * energy once the injection rate has sized those tanks.
 */
public class FusionReactorGameTests {

    private static final String TEMPLATE = "mekanism:fusion_empty";
    private static final BlockPos ORIGIN = new BlockPos(1, 1, 1);
    private static final int SIZE = 5;
    /** Same grid as {@code FusionReactorValidator}: 0 air, 1 frame, 2 port or other casing. */
    private static final int[][] GRID = {
          {0, 0, 1, 0, 0},
          {0, 1, 2, 1, 0},
          {1, 2, 2, 2, 1},
          {0, 1, 2, 1, 0},
          {0, 0, 1, 0, 0}
    };
    /** Complete shell: every frame and other slot, with the holes left empty. */
    private static final int SHELL_BLOCKS = 66;
    private static final long SAMPLE = 100;
    private static final long CASE_TEMPERATURE = 1_000_000L;
    /** Just above the burn temperature, so the hohlraum's 10 mB is not all consumed on the ignition tick. */
    private static final double IGNITION_PLASMA = 100_000_005D;
    private static final double BURN_TEMPERATURE = 100_000_000D;
    /** {@code UsageConfig#laser}: one laser tick. Ten thousand joules is one thousand degrees while the reactor is cold. */
    private static final long LASER_TICK = 10_000L;

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void fusionReactorForms(GameTestHelper helper) {
        Reactor reactor = place(helper, ORIGIN, Shape.VALID);
        BlockPos glass = reactor.port().relative(Direction.NORTH);
        helper.setBlock(glass, MekanismBlocks.STRUCTURAL_GLASS.getBlock());
        helper.succeedWhen(() -> {
            TileEntityFusionReactorPort port = port(helper, reactor);
            FusionReactorMultiblockData data = formed(port);
            check(data.length() == SIZE && data.width() == SIZE && data.height() == SIZE,
                  "Formed reactor bounds are " + data.length() + "x" + data.height() + "x" + data.width());
            check(data.locations.size() == SHELL_BLOCKS, "The shell has " + data.locations.size() + " blocks");
            check(!data.locations.contains(helper.absolutePos(glass)), "Structural glass was absorbed into the reactor");
            check(data.getValveData().size() == 1, "Formed reactor has " + data.getValveData().size() + " ports");
            check(data.fuelTank.getCapacity() == MekanismGeneratorsConfig.generators.fusionFuelCapacity.get(),
                  "Fuel capacity is " + data.fuelTank.getCapacity());
            // Upstream sizes the water and steam tanks only when the injection rate changes.
            check(data.getMaxWater() == 0, "A fresh reactor already has " + data.getMaxWater() + " mB of water capacity");
            check(data.getMaxSteam() == 0, "A fresh reactor already has " + data.getMaxSteam() + " mB of steam capacity");
            Direction side = reactor.portSide();
            check(TestPlatform.gasTanks(port, side) == 4, "The gas lookup exposes " + TestPlatform.gasTanks(port, side) + " tanks");
            check(TestPlatform.heatCapacitors(port, side) == 1, "The port exposes no heat handler");
            check(TestPlatform.blockTemperature(port, side) == data.heatCapacitor.getTemperature(),
                  "The heat lookup reports " + TestPlatform.blockTemperature(port, side));
            check(!logic(helper, reactor).isMaster(), "The logic adapter became the master");
            check(!matrix(helper, reactor).isMaster(), "The laser focus matrix became the master");
            TileEntityFusionReactorController master = master(helper, reactor);
            BlockPos min = helper.absolutePos(reactor.origin());
            BlockPos max = min.offset(SIZE - 1, SIZE - 1, SIZE - 1);
            AABB bounds = TestPlatform.renderBounds(master);
            AABB structure = new AABB(min, max.offset(1, 1, 1));
            check(bounds.equals(structure), "Master render bounds " + bounds + " are not the structure " + structure);
            BlockEntity glassEntity = helper.getBlockEntity(glass);
            check(glassEntity instanceof TileEntityStructuralGlass, "The neighbour is " + glassEntity);
            check(glassEntity instanceof IStructuralMultiblock structural && !structural.hasFormedMultiblock(),
                  "Structural glass joined the reactor");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void invalidFusionShapesStayApart(GameTestHelper helper) {
        Reactor missing = place(helper, new BlockPos(0, 1, 0), Shape.MISSING_FRAME);
        Reactor controller = place(helper, new BlockPos(6, 1, 0), Shape.BAD_CONTROLLER);
        Reactor stone = place(helper, new BlockPos(12, 1, 0), Shape.INTERIOR_STONE);
        Reactor glass = place(helper, new BlockPos(0, 1, 6), Shape.GLASS_CASING);
        Shell small = MultiblockStructures.cuboid(helper, new BlockPos(6, 1, 6), 2, 2, 2, GeneratorsBlocks.FUSION_REACTOR_FRAME.getBlock(),
              GeneratorsBlocks.FUSION_REACTOR_FRAME.getBlock(), GeneratorsBlocks.FUSION_REACTOR_PORT.getBlock());
        succeedAfterScan(helper, () -> {
            BlockEntity casing = helper.getBlockEntity(small.origin());
            return port(helper, missing).getStructure().size() == missing.blocks().size()
                  && port(helper, controller).getStructure().size() == controller.blocks().size()
                  && port(helper, stone).getStructure().size() == stone.blocks().size()
                  && port(helper, glass).getStructure().size() == glass.blocks().size()
                  && casing instanceof TileEntityFusionReactorBlock reactorBlock
                  && reactorBlock.getStructure().size() == small.blocks().size();
        }, () -> {
            TileEntityFusionReactorPort missingPort = port(helper, missing);
            checkSeparate(missingPort, missing.blocks().size(), "A reactor missing a frame block");
            check(TestPlatform.insertBlockGas(missingPort, missing.portSide(), GeneratorsGases.DEUTERIUM.get(), SAMPLE, true) == 0,
                  "An unformed port accepted deuterium");
            check(TestPlatform.insertBlockFluid(missingPort, missing.portSide(), Fluids.WATER, SAMPLE, true) == 0,
                  "An unformed port accepted water");
            check(TestPlatform.heatCapacitors(missingPort, missing.portSide()) == 0, "An unformed port exposes a heat handler");
            checkSeparate(port(helper, controller), controller.blocks().size(), "A reactor whose controller is off the top centre");
            checkSeparate(port(helper, stone), stone.blocks().size(), "A reactor with stone in its core");
            checkSeparate(port(helper, glass), glass.blocks().size(), "A reactor with structural glass in a casing slot");
            BlockEntity casing = helper.getBlockEntity(small.origin());
            check(casing instanceof TileEntityFusionReactorBlock, "The undersized shell has no reactor block");
            checkSeparate((TileEntityFusionReactorBlock) casing, small.blocks().size(), "A 2x2x2 reactor");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 300)
    public static void hohlraumAndLaserIgniteTheReactor(GameTestHelper helper) {
        Reactor reactor = place(helper, ORIGIN, Shape.VALID);
        BlockPos laserPos = reactor.matrix().below();
        helper.setBlock(laserPos, MekanismBlocks.LASER.getBlock().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
        double[] cold = new double[1];
        int[] phase = new int[1];
        helper.succeedWhen(() -> {
            TileEntityFusionReactorController controller = controller(helper, reactor);
            FusionReactorMultiblockData data = formed(controller);
            TileEntityLaserFocusMatrix matrix = matrix(helper, reactor);
            TileEntityLaser laser = laser(helper, laserPos);
            if (phase[0] == 0) {
                cold[0] = data.getPlasmaTemp();
                ItemStack partial = hohlraum(1);
                check(TestPlatform.insertItems(controller, Direction.UP, partial) == 1, "The controller refused a hohlraum");
                matrix.receiveLaserEnergy(FloatingLong.createConst((BURN_TEMPERATURE - cold[0] + 20) * 10));
                check(data.getPlasmaTemp() > BURN_TEMPERATURE, "The laser receptor left the plasma at " + data.getPlasmaTemp());
                phase[0] = 1;
                check(false, "Waiting to see a partial hohlraum fail to ignite");
            }
            if (phase[0] == 1) {
                check(!data.isBurning(), "A partial hohlraum ignited the reactor");
                check(!data.getInventorySlots(null).get(0).isEmpty(), "The partial hohlraum was consumed");
                data.getInventorySlots(null).get(0).setEmpty();
                data.setPlasmaTemp(cold[0]);
                data.heatCapacitor.setHeat(data.heatCapacitor.getHeatCapacity() * cold[0]);
                data.updateTemperatures();
                long capacity = MekanismGeneratorsConfig.generators.hohlraumMaxGas.get();
                ItemStack full = hohlraum(capacity);
                check(TestPlatform.itemGas(full) == capacity, "The hohlraum holds " + TestPlatform.itemGas(full) + " of " + capacity);
                check(TestPlatform.insertItems(controller, Direction.UP, full) == 1, "The controller refused a full hohlraum");
                long charged = TestPlatform.insertEnergy(laser, laser.getDirection().getOpposite(), 2_000_000);
                check(charged >= LASER_TICK, "The laser accepted " + charged + " through its back");
                phase[0] = 2;
                check(false, "Waiting for the laser to fire");
            }
            if (phase[0] == 2) {
                double heated = data.getPlasmaTemp();
                check(heated > cold[0] + 500 && heated < cold[0] + 5_000,
                      "One laser tick left the plasma at " + heated + " from " + cold[0]);
                double current = data.getPlasmaTemp();
                matrix.receiveLaserEnergy(FloatingLong.createConst((IGNITION_PLASMA - current) * 10));
                check(data.getPlasmaTemp() > BURN_TEMPERATURE, "The receptor left the plasma at " + data.getPlasmaTemp());
                phase[0] = 3;
                check(false, "Waiting for ignition");
            }
            check(data.isBurning(), "The full hohlraum did not ignite the reactor");
            check(data.getInventorySlots(null).get(0).isEmpty(), "Ignition left the hohlraum in the controller");
            long left = data.fuelTank.getStored();
            check(data.fuelTank.getStack().getType() == GeneratorsGases.FUSION_FUEL.get() && left > 0 && left < MekanismGeneratorsConfig.generators.hohlraumMaxGas.get(),
                  "Ignition left " + data.fuelTank.getStack() + " of the hohlraum's D-T fuel");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void reactorTakesDtFuelAndBoilsWater(GameTestHelper helper) {
        Reactor reactor = place(helper, ORIGIN, Shape.VALID);
        long[] waterBefore = new long[1];
        FloatingLong[] energyBefore = new FloatingLong[1];
        double[] caseTemperature = new double[1];
        double[] ambient = new double[1];
        int[] phase = new int[1];
        helper.succeedWhen(() -> {
            TileEntityFusionReactorPort input = port(helper, reactor);
            FusionReactorMultiblockData data = formed(input);
            Direction side = reactor.portSide();
            if (phase[0] == 0) {
                BlockEntity casing = helper.getBlockEntity(reactor.casing());
                logic(helper, reactor).setLogicTypeFromPacket(FusionReactorLogic.DEPLETED);
                check(signal(helper, reactor.logic()) == 15, "An empty reactor's depleted adapter outputs " + signal(helper, reactor.logic()));
                check(data.getMaxWater() == 0, "Water capacity is " + data.getMaxWater() + " before the injection rate changes");
                data.setInjectionRate(4);
                int waterCapacity = 4 * MekanismGeneratorsConfig.generators.fusionWaterPerInjection.get();
                long steamCapacity = 4L * MekanismGeneratorsConfig.generators.fusionSteamPerInjection.get();
                check(data.getMaxWater() == waterCapacity, "Water capacity is " + data.getMaxWater());
                check(data.getMaxSteam() == steamCapacity, "Steam capacity is " + data.getMaxSteam());
                check(TestPlatform.insertBlockGas(casing, Direction.DOWN, GeneratorsGases.DEUTERIUM.get(), SAMPLE, true) == 0,
                      "A casing block accepted deuterium");
                check(TestPlatform.insertBlockGas(casing, Direction.DOWN, GeneratorsGases.FUSION_FUEL.get(), SAMPLE, true) == 0,
                      "A casing block accepted D-T fuel");
                check(TestPlatform.insertBlockFluid(casing, Direction.DOWN, Fluids.WATER, SAMPLE, true) == 0, "A casing block accepted water");
                check(TestPlatform.insertBlockGas(input, side, MekanismGases.HYDROGEN.get(), SAMPLE, true) == 0, "The port accepted hydrogen");
                check(TestPlatform.insertBlockGas(input, side, MekanismGases.STEAM.get(), SAMPLE, true) == 0, "The port accepted steam");
                check(TestPlatform.insertBlockFluid(input, side, Fluids.LAVA, SAMPLE, true) == 0, "The port accepted lava");
                check(TestPlatform.insertBlockGas(input, side, GeneratorsGases.DEUTERIUM.get(), SAMPLE, false) == SAMPLE,
                      "A simulated deuterium fill was refused");
                check(data.deuteriumTank.isEmpty(), "A simulated fill stored deuterium");
                long deuterium = TestPlatform.insertBlockGas(input, side, GeneratorsGases.DEUTERIUM.get(), SAMPLE, true);
                long tritium = TestPlatform.insertBlockGas(input, side, GeneratorsGases.TRITIUM.get(), SAMPLE, true);
                long fuel = TestPlatform.insertBlockGas(input, side, GeneratorsGases.FUSION_FUEL.get(), SAMPLE, true);
                check(deuterium == SAMPLE && tritium == SAMPLE && fuel == SAMPLE,
                      "The port accepted " + deuterium + " deuterium, " + tritium + " tritium and " + fuel + " D-T fuel");
                check(data.deuteriumTank.getStack().getType() == GeneratorsGases.DEUTERIUM.get(), "Deuterium tank holds " + data.deuteriumTank.getStack());
                check(data.tritiumTank.getStack().getType() == GeneratorsGases.TRITIUM.get(), "Tritium tank holds " + data.tritiumTank.getStack());
                check(data.fuelTank.getStack().getType() == GeneratorsGases.FUSION_FUEL.get(), "Fuel tank holds " + data.fuelTank.getStack());
                check(TestPlatform.blockGas(input, side) == SAMPLE * 3, "The gas lookup reports " + TestPlatform.blockGas(input, side));
                check(TestPlatform.extractGas(input, side, 1, true).isEmpty(), "The port gave deuterium back");
                check(signal(helper, reactor.logic()) == 0, "A fuelled reactor still reports depleted");
                logic(helper, reactor).setLogicTypeFromPacket(FusionReactorLogic.READY);
                data.setPlasmaTemp(BURN_TEMPERATURE + 1_000);
                data.updateTemperatures();
                check(signal(helper, reactor.logic()) == 15, "A hot reactor's ready adapter outputs " + signal(helper, reactor.logic()));
                logic(helper, reactor).setLogicTypeFromPacket(FusionReactorLogic.DISABLED);
                check(signal(helper, reactor.logic()) == 0, "A disabled adapter outputs " + signal(helper, reactor.logic()));
                data.setPlasmaTemp(data.heatCapacitor.getTemperature());
                data.updateTemperatures();
                long water = TestPlatform.insertBlockFluid(input, side, Fluids.WATER, 50_000, true);
                check(water == 50_000, "The port accepted " + water + " mB of water");
                check(TestPlatform.blockFluid(input, side) == 50_000, "The fluid lookup reports " + TestPlatform.blockFluid(input, side));
                data.steamTank.setStack(GasStack.EMPTY);
                data.heatCapacitor.setHeat(data.heatCapacitor.getHeatCapacity() * CASE_TEMPERATURE);
                data.updateTemperatures();
                // The case boils against the average of its corners. Above y=80 that differs from the controller,
                // and this test's grid position is not stable between runs.
                ambient[0] = casingAmbient(helper, reactor);
                caseTemperature[0] = data.getLastCaseTemp();
                waterBefore[0] = data.waterTank.getFluidAmount();
                energyBefore[0] = data.energyContainer.getEnergy().copy();
                // One heating pass, then back to ambient. succeedWhen retries until the timeout, and each later
                // tick would boil again and hide this sample.
                data.tick(helper.getLevel());
                data.setPlasmaTemp(ambient[0]);
                data.heatCapacitor.setHeat(data.heatCapacitor.getHeatCapacity() * ambient[0]);
                data.updateTemperatures();
                phase[0] = 1;
            }
            int expectedSteam = expectedSteam(caseTemperature[0], ambient[0], waterBefore[0]);
            check(expectedSteam > SAMPLE, "The armed temperature only boils " + expectedSteam + " mB");
            check(data.steamTank.getStack().getType() == MekanismGases.STEAM.get() && data.steamTank.getStored() == expectedSteam,
                  "Steam is " + data.steamTank.getStack() + " from " + expectedSteam);
            check(data.waterTank.getFluidAmount() == waterBefore[0] - expectedSteam,
                  "Water is " + data.waterTank.getFluidAmount() + " after boiling " + expectedSteam);
            FloatingLong expectedEnergy = expectedEnergy(caseTemperature[0], ambient[0]);
            FloatingLong gained = data.energyContainer.getEnergy().subtract(energyBefore[0]);
            check(gained.equals(expectedEnergy), "The casing stored " + gained + " from " + expectedEnergy);
            BlockEntity casing = helper.getBlockEntity(reactor.casing());
            check(TestPlatform.extractEnergy(casing, Direction.DOWN, 100_000) == 0, "A casing block gave up energy");
            check(data.energyContainer.getEnergy().equals(energyBefore[0].add(expectedEnergy)), "A refused extraction changed stored energy");
            long extracted = TestPlatform.extractEnergy(input, side, 100_000);
            check(extracted > 0, "The port gave no energy through the loader API");
            check(data.energyContainer.getEnergy().smallerThan(energyBefore[0].add(expectedEnergy)), "Extracted energy is still stored");
            GasStack steam = TestPlatform.extractGas(input, side, SAMPLE, true);
            check(steam.getType() == MekanismGases.STEAM.get() && steam.getAmount() == SAMPLE, "The port returned " + steam);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void reactorContentsSurviveSaveAndLoad(GameTestHelper helper) {
        Reactor reactor = place(helper, ORIGIN, Shape.VALID);
        long[] deuterium = new long[1];
        long[] tritium = new long[1];
        long[] fuel = new long[1];
        double[] plasma = new double[1];
        FloatingLong[] energy = new FloatingLong[1];
        boolean[] reloaded = {false};
        helper.succeedWhen(() -> {
            if (!reloaded[0]) {
                TileEntityFusionReactorPort input = port(helper, reactor);
                FusionReactorMultiblockData data = formed(input);
                Direction side = reactor.portSide();
                data.setInjectionRate(4);
                long filledDeuterium = TestPlatform.insertBlockGas(input, side, GeneratorsGases.DEUTERIUM.get(), 40, true);
                long filledTritium = TestPlatform.insertBlockGas(input, side, GeneratorsGases.TRITIUM.get(), 30, true);
                long filledFuel = TestPlatform.insertBlockGas(input, side, GeneratorsGases.FUSION_FUEL.get(), 20, true);
                check(filledDeuterium == 40 && filledTritium == 30 && filledFuel == 20, "The port did not take the fuel before the save");
                data.setPlasmaTemp(50_000);
                data.heatCapacitor.setHeat(data.heatCapacitor.getHeatCapacity() * 50_000);
                data.updateTemperatures();
                data.energyContainer.setEnergy(FloatingLong.createConst(25_000));
                data.setBurning(false);
                logic(helper, reactor).setLogicTypeFromPacket(FusionReactorLogic.READY);
                logic(helper, reactor).nextMode();
                input.setActive(true);
                deuterium[0] = data.deuteriumTank.getStored();
                tritium[0] = data.tritiumTank.getStored();
                fuel[0] = data.fuelTank.getStored();
                plasma[0] = data.getPlasmaTemp();
                energy[0] = data.energyContainer.getEnergy().copy();
                List<SavedBlock> saved = new ArrayList<>();
                for (BlockPos pos : reactor.blocks()) {
                    saved.add(save(helper, pos));
                }
                MekanismGenerators.fusionReactorManager.inventories.remove(data.inventoryID);
                for (SavedBlock block : saved) {
                    helper.setBlock(block.pos, Blocks.AIR);
                }
                for (SavedBlock block : saved) {
                    helper.setBlock(block.pos, block.state);
                    helper.getBlockEntity(block.pos).load(block.tag);
                }
                reloaded[0] = true;
                check(false, "Waiting for the reloaded reactor to form");
            }
            TileEntityFusionReactorPort restored = port(helper, reactor);
            FusionReactorMultiblockData data = formed(restored);
            check(data.deuteriumTank.getStack().getType() == GeneratorsGases.DEUTERIUM.get() && data.deuteriumTank.getStored() == deuterium[0],
                  "Reloaded deuterium is " + data.deuteriumTank.getStack());
            check(data.tritiumTank.getStack().getType() == GeneratorsGases.TRITIUM.get() && data.tritiumTank.getStored() == tritium[0],
                  "Reloaded tritium is " + data.tritiumTank.getStack());
            check(data.fuelTank.getStack().getType() == GeneratorsGases.FUSION_FUEL.get() && data.fuelTank.getStored() == fuel[0],
                  "Reloaded D-T fuel is " + data.fuelTank.getStack());
            check(data.getInjectionRate() == 4, "Reloaded injection rate is " + data.getInjectionRate());
            check(data.getMaxWater() == 4 * MekanismGeneratorsConfig.generators.fusionWaterPerInjection.get(),
                  "Reloaded water capacity is " + data.getMaxWater());
            check(!data.isBurning(), "Reloaded reactor is burning");
            check(data.getPlasmaTemp() <= plasma[0] && data.getPlasmaTemp() > 10_000,
                  "Reloaded plasma is " + data.getPlasmaTemp() + " from " + plasma[0]);
            check(data.energyContainer.getEnergy().greaterOrEqual(energy[0]), "Reloaded energy is " + data.energyContainer.getEnergy());
            TileEntityFusionReactorLogicAdapter logic = logic(helper, reactor);
            check(logic.getMode() == FusionReactorLogic.READY, "Reloaded logic mode is " + logic.getMode());
            check(logic.isActiveCooled(), "Reloaded logic adapter is not on cooled logic");
            check(restored.getActive(), "Reloaded port is not in output mode");
            long reported = TestPlatform.blockGas(restored, reactor.portSide());
            check(reported == deuterium[0] + tritium[0] + fuel[0], "The gas lookup reports " + reported + " after reload");
        });
    }

    /**
     * Same average as {@code FusionReactorMultiblockData#onCreated}: the eight corners, converted to Kelvin.
     * The conversion is linear, so the mean of the corner temperatures matches the reactor's stored ambient.
     */
    private static double casingAmbient(GameTestHelper helper, Reactor reactor) {
        BlockPos min = helper.absolutePos(reactor.origin());
        BlockPos max = min.offset(SIZE - 1, SIZE - 1, SIZE - 1);
        double sum = 0;
        int count = 0;
        for (int x : new int[] {min.getX(), max.getX()}) {
            for (int y : new int[] {min.getY(), max.getY()}) {
                for (int z : new int[] {min.getZ(), max.getZ()}) {
                    sum += HeatAPI.getAmbientTemp(helper.getLevel(), new BlockPos(x, y, z));
                    count++;
                }
            }
        }
        return sum / count;
    }

    private static int expectedSteam(double caseTemperature, double ambient, long water) {
        double caseWaterHeat = MekanismGeneratorsConfig.generators.fusionWaterHeatingRatio.get() * (caseTemperature - ambient);
        int boiled = (int) (HeatUtils.getSteamEnergyEfficiency() * caseWaterHeat / HeatUtils.getWaterThermalEnthalpy());
        if (boiled < 0) {
            return 0;
        }
        return (int) Math.min(boiled, water);
    }

    private static FloatingLong expectedEnergy(double caseTemperature, double ambient) {
        double caseAirHeat = MekanismGeneratorsConfig.generators.fusionCasingThermalConductivity.get() * (caseTemperature - ambient);
        return FloatingLong.create(caseAirHeat * MekanismGeneratorsConfig.generators.fusionThermocoupleEfficiency.get());
    }

    private static ItemStack hohlraum(long amount) {
        ItemStack stack = new ItemStack(GeneratorsItems.HOHLRAUM.get());
        check(TestPlatform.insertItemGas(stack, GeneratorsGases.DEUTERIUM.get(), amount, true) == 0, "A hohlraum accepted deuterium");
        long stored = 0;
        int attempts = 0;
        while (stored < amount && attempts < amount) {
            long moved = TestPlatform.insertItemGas(stack, GeneratorsGases.FUSION_FUEL.get(), amount - stored, true);
            check(moved > 0, "The hohlraum stopped accepting D-T fuel at " + stored);
            stored += moved;
            attempts++;
        }
        check(stored == amount, "The hohlraum holds " + stored + " of " + amount);
        return stack;
    }

    private static int signal(GameTestHelper helper, BlockPos logic) {
        return helper.getLevel().getSignal(helper.absolutePos(logic), Direction.UP);
    }

    /**
     * Holes stay air. The controller is the top centre, the laser focus matrix the bottom centre, the port the north
     * centre and the logic adapter the west centre.
     */
    private static Reactor place(GameTestHelper helper, BlockPos origin, Shape shape) {
        BlockPos controller = origin.offset(2, SIZE - 1, 2);
        BlockPos matrix = origin.offset(2, 0, 2);
        BlockPos port = origin.offset(2, 2, 0);
        BlockPos logic = origin.offset(0, 2, 2);
        BlockPos frame = origin.offset(2, 0, 0);
        BlockPos casing = origin.offset(1, 0, 1);
        Direction portSide = Direction.NORTH;
        if (shape == Shape.GLASS_CASING) {
            port = origin.offset(2, 2, SIZE - 1);
            portSide = Direction.SOUTH;
        }
        List<BlockPos> blocks = new ArrayList<>();
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                for (int z = 0; z < SIZE; z++) {
                    if (x > 0 && x < SIZE - 1 && y > 0 && y < SIZE - 1 && z > 0 && z < SIZE - 1) {
                        continue;
                    }
                    if (requirement(x, y, z) == 0) {
                        continue;
                    }
                    BlockPos pos = origin.offset(x, y, z);
                    if (shape == Shape.MISSING_FRAME && pos.equals(frame)) {
                        continue;
                    }
                    if (shape == Shape.GLASS_CASING && pos.equals(origin.offset(2, 2, 0))) {
                        helper.setBlock(pos, MekanismBlocks.STRUCTURAL_GLASS.getBlock());
                        continue;
                    }
                    helper.setBlock(pos, blockAt(pos, controller, matrix, port, logic, shape));
                    blocks.add(pos);
                }
            }
        }
        if (shape == Shape.INTERIOR_STONE) {
            helper.setBlock(origin.offset(2, 2, 2), Blocks.STONE);
        }
        return new Reactor(origin, List.copyOf(blocks), controller, matrix, port, portSide, logic, casing);
    }

    private static Block blockAt(BlockPos pos, BlockPos controller, BlockPos matrix, BlockPos port, BlockPos logic, Shape shape) {
        if (shape == Shape.BAD_CONTROLLER) {
            if (pos.equals(logic)) {
                return GeneratorsBlocks.FUSION_REACTOR_CONTROLLER.getBlock();
            }
            if (pos.equals(controller)) {
                return GeneratorsBlocks.FUSION_REACTOR_FRAME.getBlock();
            }
        } else if (pos.equals(controller)) {
            return GeneratorsBlocks.FUSION_REACTOR_CONTROLLER.getBlock();
        }
        if (shape != Shape.BAD_CONTROLLER && pos.equals(logic)) {
            return GeneratorsBlocks.FUSION_REACTOR_LOGIC_ADAPTER.getBlock();
        }
        if (pos.equals(matrix)) {
            return GeneratorsBlocks.LASER_FOCUS_MATRIX.getBlock();
        }
        if (pos.equals(port)) {
            return GeneratorsBlocks.FUSION_REACTOR_PORT.getBlock();
        }
        return GeneratorsBlocks.FUSION_REACTOR_FRAME.getBlock();
    }

    /** Side priority matches {@code VoxelCuboid#getSide}: X, then Y, then Z. */
    private static int requirement(int x, int y, int z) {
        int horizontal;
        int vertical;
        if (x == 0 || x == SIZE - 1) {
            horizontal = z;
            vertical = y;
        } else if (y == 0 || y == SIZE - 1) {
            horizontal = x;
            vertical = z;
        } else {
            horizontal = x;
            vertical = y;
        }
        return GRID[horizontal][vertical];
    }

    private static SavedBlock save(GameTestHelper helper, BlockPos pos) {
        return new SavedBlock(pos, helper.getBlockState(pos), helper.getBlockEntity(pos).saveWithFullMetadata());
    }

    /** The shell stayed unformed, and its blocks were not absorbed by a neighbour. */
    private static void checkSeparate(TileEntityFusionReactorBlock tile, int blocks, String name) {
        check(!tile.getMultiblock().isFormed(), name + " formed");
        check(tile.getStructure().size() == blocks, name + " shares a structure of " + tile.getStructure().size() + " blocks");
    }

    private static TileEntityFusionReactorPort port(GameTestHelper helper, Reactor reactor) {
        BlockEntity blockEntity = helper.getBlockEntity(reactor.port());
        check(blockEntity instanceof TileEntityFusionReactorPort, "The port is " + blockEntity);
        return (TileEntityFusionReactorPort) blockEntity;
    }

    private static TileEntityFusionReactorController controller(GameTestHelper helper, Reactor reactor) {
        BlockEntity blockEntity = helper.getBlockEntity(reactor.controller());
        check(blockEntity instanceof TileEntityFusionReactorController, "The controller is " + blockEntity);
        return (TileEntityFusionReactorController) blockEntity;
    }

    private static TileEntityFusionReactorLogicAdapter logic(GameTestHelper helper, Reactor reactor) {
        BlockEntity blockEntity = helper.getBlockEntity(reactor.logic());
        check(blockEntity instanceof TileEntityFusionReactorLogicAdapter, "The logic adapter is " + blockEntity);
        return (TileEntityFusionReactorLogicAdapter) blockEntity;
    }

    private static TileEntityLaserFocusMatrix matrix(GameTestHelper helper, Reactor reactor) {
        BlockEntity blockEntity = helper.getBlockEntity(reactor.matrix());
        check(blockEntity instanceof TileEntityLaserFocusMatrix, "The laser focus matrix is " + blockEntity);
        return (TileEntityLaserFocusMatrix) blockEntity;
    }

    private static TileEntityLaser laser(GameTestHelper helper, BlockPos pos) {
        BlockEntity blockEntity = helper.getBlockEntity(pos);
        check(blockEntity instanceof TileEntityLaser, "The laser is " + blockEntity);
        return (TileEntityLaser) blockEntity;
    }

    private static FusionReactorMultiblockData formed(TileEntityFusionReactorBlock tile) {
        FusionReactorMultiblockData data = tile.getMultiblock();
        check(data.isFormed(), "The fusion reactor is not formed");
        return data;
    }

    private static TileEntityFusionReactorController master(GameTestHelper helper, Reactor reactor) {
        TileEntityFusionReactorController master = null;
        for (BlockPos pos : reactor.blocks()) {
            BlockEntity blockEntity = helper.getBlockEntity(pos);
            if (blockEntity instanceof TileEntityFusionReactorController controller && controller.isMaster()) {
                check(master == null, "The reactor has two masters");
                master = controller;
            }
        }
        check(master != null, "The formed reactor has no master");
        return master;
    }

    private enum Shape {
        VALID,
        MISSING_FRAME,
        BAD_CONTROLLER,
        INTERIOR_STONE,
        GLASS_CASING
    }

    private record Reactor(BlockPos origin, List<BlockPos> blocks, BlockPos controller, BlockPos matrix, BlockPos port, Direction portSide,
                           BlockPos logic, BlockPos casing) {
    }

    private record SavedBlock(BlockPos pos, BlockState state, CompoundTag tag) {
    }
}

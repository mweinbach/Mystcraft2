/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import static mekanism.gametest.CoreMachineGameTests.TEMPLATE;
import static mekanism.gametest.CoreMachineGameTests.check;
import static mekanism.gametest.CoreMachineGameTests.succeedAfterScan;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mekanism.api.Chunk3D;
import mekanism.api.Coord4D;
import mekanism.api.NBTConstants;
import mekanism.api.chemical.attribute.ChemicalAttribute;
import mekanism.api.chemical.gas.Gas;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.gas.attribute.GasAttributes;
import mekanism.api.chemical.gas.attribute.GasAttributes.CooledCoolant;
import mekanism.api.radiation.IRadiationSource;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.config.MekanismConfig;
import mekanism.common.lib.multiblock.IStructuralMultiblock;
import mekanism.common.lib.radiation.Meltdown;
import mekanism.common.lib.radiation.RadiationManager;
import mekanism.common.registries.MekanismGases;
import mekanism.common.registries.MekanismItems;
import mekanism.common.util.HeatUtils;
import mekanism.common.util.UnitDisplayUtils;
import mekanism.common.util.UnitDisplayUtils.RadiationUnit;
import mekanism.gametest.MultiblockStructures.Shell;
import mekanism.generators.common.MekanismGenerators;
import mekanism.generators.common.block.attribute.AttributeStateFissionPortMode;
import mekanism.generators.common.block.attribute.AttributeStateFissionPortMode.FissionPortMode;
import mekanism.generators.common.config.MekanismGeneratorsConfig;
import mekanism.generators.common.content.fission.FissionReactorMultiblockData;
import mekanism.generators.common.registries.GeneratorsBlocks;
import mekanism.generators.common.tile.TileEntityReactorGlass;
import mekanism.generators.common.tile.fission.TileEntityFissionReactorCasing;
import mekanism.generators.common.tile.fission.TileEntityFissionReactorLogicAdapter;
import mekanism.generators.common.tile.fission.TileEntityFissionReactorLogicAdapter.FissionReactorLogic;
import mekanism.generators.common.tile.fission.TileEntityFissionReactorPort;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fluids.FluidStack;

/**
 * A 3×4×3 fission reactor: one fuel assembly, its control rod, reactor glass walls, an input port, a waste port, a
 * coolant port and a logic adapter. Fissile fuel and sodium or water go in through the input port. Waste and heated
 * coolant come back out through the matching ports. Damage builds only above {@code 1,200} K and, at the cap, either
 * scrams the reactor or queues a meltdown. The queued meltdown explodes at the configured radius, breaks the shell
 * and dumps radioactive tanks at the core. A player outside that field accumulates a dose the dosimeter and Geiger
 * counter report.
 */
public class FissionReactorGameTests {

    private static final BlockPos ORIGIN = new BlockPos(1, 1, 1);
    private static final int SIZE_XZ = 3;
    private static final int SIZE_Y = 4;
    /** One interior column, so the shell is the cuboid minus those two blocks. */
    private static final int SHELL_BLOCKS = SIZE_XZ * SIZE_Y * SIZE_XZ - (SIZE_XZ - 2) * (SIZE_Y - 2) * (SIZE_XZ - 2);
    private static final int VOLUME = SIZE_XZ * SIZE_Y * SIZE_XZ;
    /** Matches {@code FissionReactorMultiblockData}'s water conductivity. The field is not visible from here. */
    private static final double WATER_CONDUCTIVITY = 0.5;
    private static final long SAMPLE = 100;
    private static final double HOT = FissionReactorMultiblockData.MAX_DAMAGE_TEMPERATURE;

    private static boolean remembered;
    private static boolean savedMeltdowns;
    private static double savedChance;
    private static float savedRadius;

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void fissionReactorForms(GameTestHelper helper) {
        Reactor reactor = place(helper, ORIGIN);
        helper.succeedWhen(() -> {
            TileEntityFissionReactorPort input = input(helper, reactor);
            FissionReactorMultiblockData data = formed(input);
            check(data.length() == SIZE_XZ && data.width() == SIZE_XZ && data.height() == SIZE_Y,
                  "Formed reactor bounds are " + data.length() + "x" + data.height() + "x" + data.width());
            check(data.assemblies.size() == 1, "Formed reactor has " + data.assemblies.size() + " fuel columns");
            check(data.surfaceArea == 6, "Fuel surface area is " + data.surfaceArea);
            check(data.getBoilEfficiency() == 1, "Boil efficiency is " + data.getBoilEfficiency());
            check(data.getMaxBurnRate() == MekanismGeneratorsConfig.generators.burnPerAssembly.get(),
                  "Max burn rate is " + data.getMaxBurnRate());
            check(data.getVolume() == VOLUME, "Reactor volume is " + data.getVolume());
            check(data.locations.size() == SHELL_BLOCKS, "The shell has " + data.locations.size() + " blocks");
            check(data.fuelTank.getCapacity() == MekanismGeneratorsConfig.generators.maxFuelPerAssembly.get(),
                  "Fuel capacity is " + data.fuelTank.getCapacity());
            int cooled = VOLUME * MekanismGeneratorsConfig.generators.fissionCooledCoolantPerTank.get();
            check(data.fluidCoolantTank.getCapacity() == cooled, "Water capacity is " + data.fluidCoolantTank.getCapacity());
            check(data.gasCoolantTank.getCapacity() == cooled, "Sodium capacity is " + data.gasCoolantTank.getCapacity());
            long heated = (long) VOLUME * MekanismGeneratorsConfig.generators.fissionHeatedCoolantPerTank.get();
            check(data.heatedCoolantTank.getCapacity() == heated, "Heated coolant capacity is " + data.heatedCoolantTank.getCapacity());
            check(data.wasteTank.getCapacity() == data.fuelTank.getCapacity(), "Waste capacity is " + data.wasteTank.getCapacity());
            check(data.heatCapacitor.getHeatCapacity() == MekanismGeneratorsConfig.generators.fissionCasingHeatCapacity.get() * SHELL_BLOCKS,
                  "Heat capacity is " + data.heatCapacitor.getHeatCapacity());
            check(data.getValveData().size() == 3, "Formed reactor has " + data.getValveData().size() + " ports");
            check(helper.getBlockState(reactor.shell().valve()).getValue(AttributeStateFissionPortMode.modeProperty) == FissionPortMode.INPUT,
                  "The north port is not an input");
            check(helper.getBlockState(reactor.waste()).getValue(AttributeStateFissionPortMode.modeProperty) == FissionPortMode.OUTPUT_WASTE,
                  "The south port is not a waste output");
            check(helper.getBlockState(reactor.coolant()).getValue(AttributeStateFissionPortMode.modeProperty) == FissionPortMode.OUTPUT_COOLANT,
                  "The east port is not a coolant output");
            Direction side = reactor.shell().valveSide();
            check(TestPlatform.gasTanks(input, side) == 4, "The gas lookup exposes " + TestPlatform.gasTanks(input, side) + " tanks");
            check(TestPlatform.heatCapacitors(input, side) == 1, "The input port exposes no heat handler");
            check(TestPlatform.blockTemperature(input, side) == data.heatCapacitor.getTemperature(),
                  "The heat lookup reports " + TestPlatform.blockTemperature(input, side));
            BlockEntity glass = helper.getBlockEntity(reactor.shell().origin().offset(1, 1, 0));
            check(glass instanceof TileEntityReactorGlass, "The north wall is " + glass);
            check(glass instanceof IStructuralMultiblock structural && structural.hasFormedMultiblock(),
                  "Reactor glass did not join the reactor");
            check(!logic(helper, reactor).isMaster(), "The logic adapter became the master");
            TileEntityFissionReactorCasing master = master(helper, reactor.shell());
            BlockPos min = helper.absolutePos(reactor.shell().origin());
            BlockPos max = min.offset(SIZE_XZ - 1, SIZE_Y - 1, SIZE_XZ - 1);
            AABB bounds = TestPlatform.renderBounds(master);
            AABB structure = new AABB(min, max.offset(1, 1, 1));
            check(bounds.equals(structure), "Master render bounds " + bounds + " are not the structure " + structure);
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void invalidFissionShapesStayApart(GameTestHelper helper) {
        // One block of air between shells. Face-adjacent reactor casings merge into one structure, and that assembly
        // fails the cuboid check before the fuel-column rules run.
        Shell gapped = gappedColumn(helper, new BlockPos(0, 1, 0));
        Shell holed = casingShell(helper, new BlockPos(4, 1, 0), SIZE_Y);
        helper.setBlock(holed.origin(), Blocks.AIR);
        Shell missingRod = casingShell(helper, new BlockPos(0, 1, 4), SIZE_Y);
        helper.setBlock(missingRod.origin().offset(1, 1, 1), GeneratorsBlocks.FISSION_FUEL_ASSEMBLY.getBlock());
        Shell small = MultiblockStructures.cuboid(helper, new BlockPos(4, 1, 5), 2, 2, 2, GeneratorsBlocks.FISSION_REACTOR_CASING.getBlock(),
              GeneratorsBlocks.FISSION_REACTOR_CASING.getBlock(), GeneratorsBlocks.FISSION_REACTOR_PORT.getBlock());
        succeedAfterScan(helper, () -> {
            BlockEntity casing = helper.getBlockEntity(small.origin());
            return port(helper, gapped).getStructure().size() == gapped.blocks().size()
                  && port(helper, holed).getStructure().size() == holed.blocks().size() - 1
                  && port(helper, missingRod).getStructure().size() == missingRod.blocks().size()
                  && casing instanceof TileEntityFissionReactorCasing reactorCasing
                  && reactorCasing.getStructure().size() == small.blocks().size();
        }, () -> {
            TileEntityFissionReactorPort gapPort = port(helper, gapped);
            checkSeparate(gapPort, gapped.blocks().size(), "A reactor with a gap in its fuel column");
            check(TestPlatform.insertBlockGas(gapPort, gapped.valveSide(), MekanismGases.FISSILE_FUEL.get(), SAMPLE, true) == 0,
                  "An unformed port accepted fissile fuel");
            check(TestPlatform.insertBlockFluid(gapPort, gapped.valveSide(), Fluids.WATER, SAMPLE, true) == 0,
                  "An unformed port accepted water");
            check(TestPlatform.heatCapacitors(gapPort, gapped.valveSide()) == 0, "An unformed port exposes a heat handler");
            checkSeparate(port(helper, holed), holed.blocks().size() - 1, "A reactor with a missing corner");
            checkSeparate(port(helper, missingRod), missingRod.blocks().size(), "A reactor with no control rod");
            BlockEntity casing = helper.getBlockEntity(small.origin());
            check(casing instanceof TileEntityFissionReactorCasing, "The undersized shell has no reactor block");
            checkSeparate((TileEntityFissionReactorCasing) casing, small.blocks().size(), "A 2x2x2 reactor");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void reactorBurnsFuelAndEmitsHeatedSodium(GameTestHelper helper) {
        Reactor reactor = place(helper, ORIGIN);
        boolean[] ready = new boolean[1];
        double[] armed = new double[] {Double.NaN};
        long[] sodiumBefore = new long[1];
        long[] heatedBefore = new long[1];
        long[] wasteBefore = new long[1];
        helper.succeedWhen(() -> {
            TileEntityFissionReactorPort input = input(helper, reactor);
            FissionReactorMultiblockData data = formed(input);
            if (!ready[0]) {
                Direction side = reactor.shell().valveSide();
                BlockEntity casing = helper.getBlockEntity(reactor.shell().origin());
                TileEntityFissionReactorLogicAdapter logic = logic(helper, reactor);
                logic.setLogicTypeFromPacket(FissionReactorLogic.DEPLETED);
                check(signal(helper, reactor.logic()) == 15, "An empty reactor's depleted adapter outputs " + signal(helper, reactor.logic()));
                check(TestPlatform.insertBlockGas(casing, Direction.DOWN, MekanismGases.FISSILE_FUEL.get(), SAMPLE, true) == 0,
                      "A casing block accepted fissile fuel");
                check(TestPlatform.insertBlockGas(casing, Direction.DOWN, MekanismGases.SODIUM.get(), SAMPLE, true) == 0,
                      "A casing block accepted sodium");
                check(TestPlatform.insertBlockGas(port(helper, reactor.waste()), reactor.wasteSide(), MekanismGases.FISSILE_FUEL.get(), SAMPLE, true) == 0,
                      "The waste port accepted fissile fuel");
                check(TestPlatform.insertBlockGas(port(helper, reactor.coolant()), reactor.coolantSide(), MekanismGases.SODIUM.get(), SAMPLE, true) == 0,
                      "The coolant port accepted sodium");
                check(TestPlatform.insertBlockGas(input, side, MekanismGases.HYDROGEN.get(), SAMPLE, true) == 0, "The input port accepted hydrogen");
                long fuelRoom = data.fuelTank.getCapacity();
                long sodiumRoom = data.gasCoolantTank.getCapacity();
                check(TestPlatform.insertBlockGas(input, side, MekanismGases.FISSILE_FUEL.get(), fuelRoom, false) == fuelRoom,
                      "A simulated fill offered less than the fuel tank");
                check(data.fuelTank.isEmpty(), "A simulated fill stored fissile fuel");
                long fuel = TestPlatform.insertBlockGas(input, side, MekanismGases.FISSILE_FUEL.get(), fuelRoom, true);
                check(fuel == fuelRoom, "The input port accepted " + fuel + " of " + fuelRoom + " fissile fuel");
                check(signal(helper, reactor.logic()) == 0, "A fuelled reactor still reports depleted");
                long sodium = TestPlatform.insertBlockGas(input, side, MekanismGases.SODIUM.get(), sodiumRoom, true);
                check(sodium == sodiumRoom, "The input port accepted " + sodium + " of " + sodiumRoom + " sodium");
                check(data.fuelTank.getStack().getType() == MekanismGases.FISSILE_FUEL.get(), "The fuel tank holds " + data.fuelTank.getStack().getType());
                check(data.gasCoolantTank.getStack().getType() == MekanismGases.SODIUM.get(), "The coolant tank holds " + data.gasCoolantTank.getStack().getType());
                check(TestPlatform.blockGas(input, side) == fuel + sodium, "The gas lookup reports " + TestPlatform.blockGas(input, side));
                data.setRateLimit(data.getMaxBurnRate());
                activate(helper, reactor);
                ready[0] = true;
            }
            // lastBoilRate is taken from the temperature before this tick applies burn heat, so arm a known temperature
            // and read the rate on the following tick.
            if (Double.isNaN(armed[0])) {
                check(data.isActive(), "The reactor stopped");
                data.heatCapacitor.setHeat(data.heatCapacitor.getHeatCapacity() * (HeatUtils.BASE_BOIL_TEMP + 50));
                armed[0] = data.heatCapacitor.getTemperature();
                sodiumBefore[0] = data.gasCoolantTank.getStored();
                heatedBefore[0] = data.heatedCoolantTank.getStored();
                wasteBefore[0] = data.wasteTank.getStored();
                check(sodiumBefore[0] > 0, "The sodium tank is empty");
                check(false, "Waiting for one heating pass");
            }
            long expected = expectedSodiumBoil(armed[0], data.heatCapacitor.getHeatCapacity(), data.getBoilEfficiency(), sodiumBefore[0]);
            check(data.lastBoilRate > SAMPLE && data.lastBoilRate == expected,
                  "Heating rate " + data.lastBoilRate + " is not the " + expected + " mB the armed temperature allows");
            check(data.lastBurnRate == data.getMaxBurnRate(), "Burned " + data.lastBurnRate + " mB");
            check(data.gasCoolantTank.getStored() == sodiumBefore[0] - data.lastBoilRate,
                  "Sodium is " + data.gasCoolantTank.getStored() + " after boiling " + data.lastBoilRate);
            check(data.heatedCoolantTank.getStack().getType() == MekanismGases.SUPERHEATED_SODIUM.get()
                        && data.heatedCoolantTank.getStored() == heatedBefore[0] + data.lastBoilRate,
                  "Heated coolant is " + data.heatedCoolantTank.getStack());
            check(data.wasteTank.getStack().getType() == MekanismGases.NUCLEAR_WASTE.get()
                        && data.wasteTank.getStored() == wasteBefore[0] + (long) data.lastBurnRate,
                  "Waste is " + data.wasteTank.getStack() + " from " + wasteBefore[0]);
            check(TestPlatform.extractGas(input(helper, reactor), reactor.shell().valveSide(), SAMPLE, true).isEmpty(),
                  "The input port gave up gas");
            GasStack waste = TestPlatform.extractGas(port(helper, reactor.waste()), reactor.wasteSide(), 1, true);
            check(waste.getType() == MekanismGases.NUCLEAR_WASTE.get() && waste.getAmount() == 1, "The waste port returned " + waste);
            GasStack heated = TestPlatform.extractGas(port(helper, reactor.coolant()), reactor.coolantSide(), SAMPLE, true);
            check(heated.getType() == MekanismGases.SUPERHEATED_SODIUM.get() && heated.getAmount() == SAMPLE,
                  "The coolant port returned " + heated);
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void reactorBoilsWaterIntoSteam(GameTestHelper helper) {
        Reactor reactor = place(helper, ORIGIN);
        boolean[] ready = new boolean[1];
        double[] armed = new double[] {Double.NaN};
        long[] waterBefore = new long[1];
        long[] steamBefore = new long[1];
        long[] wasteBefore = new long[1];
        helper.succeedWhen(() -> {
            TileEntityFissionReactorPort input = input(helper, reactor);
            FissionReactorMultiblockData data = formed(input);
            if (!ready[0]) {
                Direction side = reactor.shell().valveSide();
                BlockEntity casing = helper.getBlockEntity(reactor.shell().origin());
                int room = data.fluidCoolantTank.getCapacity();
                check(TestPlatform.insertBlockFluid(casing, Direction.DOWN, Fluids.WATER, SAMPLE, true) == 0, "A casing block accepted water");
                check(TestPlatform.insertBlockFluid(input, side, Fluids.LAVA, SAMPLE, true) == 0, "The input port accepted lava");
                check(TestPlatform.insertBlockFluid(input, side, Fluids.WATER, room, false) == room, "A simulated fill offered less than the water tank");
                check(data.fluidCoolantTank.isEmpty(), "A simulated fill stored water");
                long water = TestPlatform.insertBlockFluid(input, side, Fluids.WATER, room, true);
                check(water == room, "The input port accepted " + water + " of " + room + " mB");
                check(TestPlatform.insertBlockGas(input, side, MekanismGases.SODIUM.get(), SAMPLE, true) == 0,
                      "The input port accepted sodium while it held water");
                long fuel = TestPlatform.insertBlockGas(input, side, MekanismGases.FISSILE_FUEL.get(), data.fuelTank.getCapacity(), true);
                check(fuel == data.fuelTank.getCapacity(), "The input port accepted " + fuel + " fissile fuel");
                data.setRateLimit(data.getMaxBurnRate());
                activate(helper, reactor);
                ready[0] = true;
            }
            if (Double.isNaN(armed[0])) {
                check(data.isActive(), "The reactor stopped");
                data.heatCapacitor.setHeat(data.heatCapacitor.getHeatCapacity() * (HeatUtils.BASE_BOIL_TEMP + 50));
                armed[0] = data.heatCapacitor.getTemperature();
                waterBefore[0] = data.fluidCoolantTank.getFluidAmount();
                steamBefore[0] = data.heatedCoolantTank.getStored();
                wasteBefore[0] = data.wasteTank.getStored();
                check(waterBefore[0] > 0, "The water tank is empty");
                check(false, "Waiting for one heating pass");
            }
            long expected = expectedWaterBoil(armed[0], data.heatCapacitor.getHeatCapacity(), data.getBoilEfficiency(), waterBefore[0]);
            check(data.lastBoilRate > SAMPLE && data.lastBoilRate == expected,
                  "Heating rate " + data.lastBoilRate + " is not the " + expected + " mB the armed temperature allows");
            check(data.fluidCoolantTank.getFluidAmount() == waterBefore[0] - data.lastBoilRate,
                  "Water is " + data.fluidCoolantTank.getFluidAmount() + " after boiling " + data.lastBoilRate);
            check(data.heatedCoolantTank.getStack().getType() == MekanismGases.STEAM.get()
                        && data.heatedCoolantTank.getStored() == steamBefore[0] + data.lastBoilRate,
                  "The steam tank holds " + data.heatedCoolantTank.getStack());
            check(data.wasteTank.getStack().getType() == MekanismGases.NUCLEAR_WASTE.get()
                        && data.wasteTank.getStored() == wasteBefore[0] + (long) data.lastBurnRate,
                  "The waste tank holds " + data.wasteTank.getStack());
            check(TestPlatform.extractGas(port(helper, reactor.coolant()), reactor.coolantSide(), SAMPLE, false).getType() == MekanismGases.STEAM.get(),
                  "A simulated pull from the coolant port was not steam");
            GasStack steam = TestPlatform.extractGas(port(helper, reactor.coolant()), reactor.coolantSide(), SAMPLE, true);
            check(steam.getType() == MekanismGases.STEAM.get() && steam.getAmount() == SAMPLE, "The coolant port returned " + steam);
            GasStack waste = TestPlatform.extractGas(port(helper, reactor.waste()), reactor.wasteSide(), 1, true);
            check(waste.getType() == MekanismGases.NUCLEAR_WASTE.get() && waste.getAmount() == 1, "The waste port returned " + waste);
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void reactorDamageCrossesTheMeltdownThreshold(GameTestHelper helper) {
        restoreMeltdownConfig();
        clearMeltdowns(helper);
        Reactor reactor = place(helper, ORIGIN);
        double[] mark = new double[1];
        int[] phase = new int[1];
        helper.succeedWhen(() -> {
            try {
                FissionReactorMultiblockData data = formed(input(helper, reactor));
                TileEntityFissionReactorLogicAdapter logic = logic(helper, reactor);
                if (phase[0] == 0) {
                    data.setActive(false);
                    data.heatCapacitor.setHeat(data.heatCapacitor.getHeatCapacity() * HeatUtils.BASE_BOIL_TEMP);
                    data.reactorDamage = 30;
                    mark[0] = data.reactorDamage;
                    phase[0] = 1;
                    check(false, "Waiting for damage to fall");
                }
                if (phase[0] == 1) {
                    check(data.heatCapacitor.getTemperature() < FissionReactorMultiblockData.MIN_DAMAGE_TEMPERATURE,
                          "The reactor is already at " + data.heatCapacitor.getTemperature());
                    check(data.reactorDamage < mark[0], "Damage did not fall while cool: " + data.reactorDamage);
                    data.heatCapacitor.setHeat(data.heatCapacitor.getHeatCapacity() * HOT);
                    mark[0] = data.reactorDamage;
                    phase[0] = 2;
                    check(false, "Waiting for damage to rise");
                }
                if (phase[0] == 2) {
                    check(data.reactorDamage > mark[0], "Damage did not rise above " + mark[0] + ": " + data.reactorDamage);
                    logic.setLogicTypeFromPacket(FissionReactorLogic.TEMPERATURE);
                    check(signal(helper, reactor.logic()) == 15, "A hot reactor's temperature adapter outputs " + signal(helper, reactor.logic()));
                    logic.setLogicTypeFromPacket(FissionReactorLogic.DAMAGED);
                    check(signal(helper, reactor.logic()) == 0, "Damage " + data.reactorDamage + " already trips the damaged adapter");
                    logic.setLogicTypeFromPacket(FissionReactorLogic.DEPLETED);
                    check(data.fuelTank.isEmpty() && signal(helper, reactor.logic()) == 15, "An empty fuel tank outputs " + signal(helper, reactor.logic()));
                    data.wasteTank.setStack(MekanismGases.NUCLEAR_WASTE.getStack(data.wasteTank.getCapacity()));
                    logic.setLogicTypeFromPacket(FissionReactorLogic.EXCESS_WASTE);
                    check(signal(helper, reactor.logic()) == 15, "A full waste tank outputs " + signal(helper, reactor.logic()));
                    data.wasteTank.setEmpty();
                    check(signal(helper, reactor.logic()) == 0, "An empty waste tank still outputs");
                    rememberMeltdownConfig();
                    MekanismGeneratorsConfig.generators.fissionMeltdownsEnabled.set(false);
                    MekanismGeneratorsConfig.generators.fissionMeltdownChance.set(1);
                    data.reactorDamage = FissionReactorMultiblockData.MAX_DAMAGE;
                    phase[0] = 3;
                    check(false, "Waiting for the disabled threshold");
                }
                if (phase[0] == 3) {
                    check(data.isForceDisabled(), "Damage at the cap left the reactor enabled");
                    check(!data.isActive(), "A force-disabled reactor is still active");
                    data.setActive(true);
                    check(!data.isActive(), "A force-disabled reactor accepted activation");
                    logic.setLogicTypeFromPacket(FissionReactorLogic.DAMAGED);
                    check(signal(helper, reactor.logic()) == 15, "The damaged adapter outputs " + signal(helper, reactor.logic()));
                    check(meltdowns(helper).isEmpty(), "Disabled meltdowns still queued one");
                    MekanismGeneratorsConfig.generators.fissionMeltdownsEnabled.set(true);
                    MekanismGeneratorsConfig.generators.fissionMeltdownChance.set(1);
                    MekanismGeneratorsConfig.generators.fissionMeltdownRadius.set(0);
                    phase[0] = 4;
                    check(false, "Waiting for the meltdown");
                }
                check(!meltdowns(helper).isEmpty(), "Crossing the damage threshold queued no meltdown");
                clearMeltdowns(helper);
                Coord4D center = new Coord4D(data.getBounds().getCenter(), helper.getLevel());
                double before = RadiationManager.INSTANCE.getRadiationLevel(center);
                data.fuelTank.setStack(MekanismGases.FISSILE_FUEL.getStack(500));
                data.wasteTank.setStack(MekanismGases.NUCLEAR_WASTE.getStack(1_000));
                data.meltdownHappened(helper.getLevel());
                // Fissile fuel has no radiation attribute, so the meltdown leaves it. Nuclear waste is dumped.
                check(data.fuelTank.getStack().getType() == MekanismGases.FISSILE_FUEL.get() && data.fuelTank.getStored() == 500,
                      "The meltdown changed fissile fuel to " + data.fuelTank.getStack());
                check(data.wasteTank.isEmpty(), "The meltdown left nuclear waste in the tank");
                check(data.heatedCoolantTank.isEmpty(), "The meltdown left heated coolant");
                check(!data.isActive(), "The meltdown left the reactor active");
                check(data.burnRemaining == 0 && data.partialWaste == 0, "The meltdown left burn remainder");
                check(data.reactorDamage == MekanismGeneratorsConfig.generators.fissionPostMeltdownDamage.get(),
                      "Post-meltdown damage is " + data.reactorDamage);
                double after = RadiationManager.INSTANCE.getRadiationLevel(center);
                check(after > before + 100, "Meltdown radiation rose from " + before + " to " + after);
                RadiationManager.INSTANCE.removeRadiationSource(center);
                restoreMeltdownConfig();
                helper.succeed();
            } catch (RuntimeException e) {
                if (!(e instanceof GameTestAssertException)) {
                    restoreMeltdownConfig();
                    clearMeltdowns(helper);
                }
                throw e;
            }
        });
    }

    /**
     * The damage cap queues a meltdown at the configured radius. Its explosion breaks the shell and
     * {@code meltdownHappened} releases the waste. A block well outside that radius stays put.
     */
    @GameTest(template = "mekanism:fission_yard", timeoutTicks = 400, batch = "mekanismFissionMeltdown")
    public static void meltdownExplosionBreaksTheReactor(GameTestHelper helper) {
        BlockPos origin = new BlockPos(22, 20, 22);
        Reactor reactor = place(helper, origin);
        BlockPos sentinel = new BlockPos(2, 20, 2);
        helper.setBlock(sentinel, Blocks.DIRT);
        Coord4D[] center = new Coord4D[1];
        BlockPos[] min = new BlockPos[1];
        double[] expected = new double[1];
        int[] phase = new int[1];
        helper.succeedWhen(() -> {
            try {
                if (phase[0] == 0) {
                    FissionReactorMultiblockData data = formed(input(helper, reactor));
                    rememberMeltdownConfig();
                    data.setActive(false);
                    data.heatCapacitor.setHeat(data.heatCapacitor.getHeatCapacity() * HOT);
                    data.fuelTank.setStack(MekanismGases.FISSILE_FUEL.getStack(500));
                    data.heatedCoolantTank.setStack(MekanismGases.SUPERHEATED_SODIUM.getStack(200));
                    data.wasteTank.setStack(MekanismGases.NUCLEAR_WASTE.getStack(1_000));
                    data.partialWaste = 0.5;
                    checkNotRadioactive(MekanismGases.FISSILE_FUEL.get(), "Fissile fuel");
                    checkNotRadioactive(MekanismGases.SUPERHEATED_SODIUM.get(), "Superheated sodium");
                    center[0] = new Coord4D(data.getBounds().getCenter(), helper.getLevel());
                    min[0] = data.getMinPos();
                    RadiationManager.INSTANCE.removeRadiationSource(center[0]);
                    MekanismGeneratorsConfig.generators.fissionMeltdownsEnabled.set(true);
                    MekanismGeneratorsConfig.generators.fissionMeltdownChance.set(1);
                    MekanismGeneratorsConfig.generators.fissionMeltdownRadius.set(8);
                    check(MekanismGeneratorsConfig.generators.fissionMeltdownRadiationMultiplier.get() == 50,
                          "Meltdown radiation multiplier is " + MekanismGeneratorsConfig.generators.fissionMeltdownRadiationMultiplier.get());
                    expected[0] = (1_000.5D) * wasteRadioactivity() * MekanismGeneratorsConfig.generators.fissionMeltdownRadiationMultiplier.get();
                    data.reactorDamage = FissionReactorMultiblockData.MAX_DAMAGE;
                    phase[0] = 1;
                    check(false, "Waiting for the meltdown");
                }
                if (!shellBroken(helper, reactor)) {
                    checkMeltdownRadius(helper, min[0]);
                    check(false, "Waiting for the blast");
                }
                checkMeltdownRadius(helper, min[0]);
                check(helper.getBlockState(sentinel).is(Blocks.DIRT), "The blast reached the sentinel at " + sentinel);
                IRadiationSource source = sourceAt(center[0]);
                check(source != null, "The meltdown released no radiation");
                double magnitude = source.getMagnitude();
                double decay = MekanismConfig.general.radiationSourceDecayRate.get();
                check(magnitude <= expected[0], "Meltdown radiation " + magnitude + " exceeds " + expected[0]);
                check(magnitude >= expected[0] * Math.pow(decay, 5), "Meltdown radiation " + magnitude + " is below " + expected[0]);
                check(RadiationManager.INSTANCE.getRadiationLevel(center[0]) >= RadiationManager.BASELINE + magnitude,
                      "Exposure at the core ignores the released radiation");
                clearMeltdowns(helper);
                RadiationManager.INSTANCE.removeRadiationSource(center[0]);
                restoreMeltdownConfig();
                helper.succeed();
            } catch (RuntimeException e) {
                if (!(e instanceof GameTestAssertException)) {
                    restoreMeltdownConfig();
                    clearMeltdowns(helper);
                    if (center[0] != null) {
                        RadiationManager.INSTANCE.removeRadiationSource(center[0]);
                    }
                }
                throw e;
            }
        });
    }

    /** A full waste tank leaks one mB of burned fuel, and a player outside the shell accumulates that field. */
    @GameTest(template = TEMPLATE, timeoutTicks = 400, batch = "mekanismFissionLeakDose")
    public static void leakingReactorDosesANearbyPlayer(GameTestHelper helper) {
        Reactor reactor = place(helper, ORIGIN);
        ListeningPlayer[] player = new ListeningPlayer[1];
        double[] startDose = new double[1];
        int[] phase = new int[1];
        helper.succeedWhen(() -> {
            Coord4D center = null;
            try {
                FissionReactorMultiblockData data = formed(input(helper, reactor));
                center = new Coord4D(data.getBounds().getCenter(), helper.getLevel());
                if (phase[0] == 0) {
                    RadiationManager.INSTANCE.removeRadiationSource(center);
                    data.setActive(false);
                    data.wasteTank.setStack(MekanismGases.NUCLEAR_WASTE.getStack(data.wasteTank.getCapacity()));
                    data.fuelTank.setStack(MekanismGases.FISSILE_FUEL.getStack(1));
                    data.burnRemaining = 0;
                    data.partialWaste = 0;
                    data.setRateLimit(1);
                    data.setActive(true);
                    phase[0] = 1;
                    check(false, "Waiting for the leak");
                }
                if (phase[0] == 1) {
                    IRadiationSource source = sourceAt(center);
                    double perMilliBucket = wasteRadioactivity();
                    check(source != null, "The full waste tank has not leaked");
                    double magnitude = source.getMagnitude();
                    double decay = MekanismConfig.general.radiationSourceDecayRate.get();
                    check(magnitude <= perMilliBucket, "Leak magnitude " + magnitude + " exceeds one mB of waste");
                    check(magnitude >= perMilliBucket * Math.pow(decay, 5), "Leak magnitude " + magnitude + " is below one mB of waste");
                    data.setActive(false);
                    player[0] = listening(helper, ORIGIN.offset(SIZE_XZ, 1, 1));
                    player[0].setNoGravity(true);
                    startDose[0] = dose(player[0]);
                    phase[0] = 2;
                    check(false, "Waiting for the dose");
                }
                player[0].doTick();
                double current = dose(player[0]);
                check(current > startDose[0], "Dose stayed at " + current);
                double level = RadiationManager.INSTANCE.getRadiationLevelAndMaxMagnitude(player[0]).level();
                IRadiationSource leak = sourceAt(center);
                check(leak != null, "The leak disappeared");
                double exposure = leak.getMagnitude() / Math.max(1, center.distanceToSquared(new Coord4D(player[0])));
                check(level >= RadiationManager.BASELINE + exposure * MekanismConfig.general.radiationSourceDecayRate.get(),
                      "The field at the player is " + level + " from an exposure of " + exposure);
                reportDose(player[0], current, level);
                RadiationManager.INSTANCE.removeRadiationSource(center);
                helper.succeed();
            } catch (RuntimeException e) {
                if (!(e instanceof GameTestAssertException) && center != null) {
                    RadiationManager.INSTANCE.removeRadiationSource(center);
                }
                throw e;
            }
        });
    }

    /** Waste dumped by a meltdown doses a player outside the shell, and both instruments report the live values. */
    @GameTest(template = TEMPLATE, timeoutTicks = 400, batch = "mekanismFissionMeltdownDose")
    public static void meltedDownReactorDosesANearbyPlayer(GameTestHelper helper) {
        Reactor reactor = place(helper, ORIGIN);
        ListeningPlayer[] player = new ListeningPlayer[1];
        double[] startDose = new double[1];
        int[] phase = new int[1];
        helper.succeedWhen(() -> {
            Coord4D center = null;
            try {
                FissionReactorMultiblockData data = formed(input(helper, reactor));
                center = new Coord4D(data.getBounds().getCenter(), helper.getLevel());
                if (phase[0] == 0) {
                    RadiationManager.INSTANCE.removeRadiationSource(center);
                    data.setActive(false);
                    data.fuelTank.setStack(MekanismGases.FISSILE_FUEL.getStack(500));
                    data.heatedCoolantTank.setStack(MekanismGases.SUPERHEATED_SODIUM.getStack(200));
                    data.wasteTank.setStack(MekanismGases.NUCLEAR_WASTE.getStack(1_000));
                    data.partialWaste = 0.5;
                    checkNotRadioactive(MekanismGases.FISSILE_FUEL.get(), "Fissile fuel");
                    checkNotRadioactive(MekanismGases.SUPERHEATED_SODIUM.get(), "Superheated sodium");
                    data.meltdownHappened(helper.getLevel());
                    double expected = (1_000.5D) * wasteRadioactivity() * MekanismGeneratorsConfig.generators.fissionMeltdownRadiationMultiplier.get();
                    IRadiationSource source = sourceAt(center);
                    check(source != null, "The meltdown released no radiation");
                    double magnitude = source.getMagnitude();
                    check(Math.abs(magnitude - expected) < 1.0E-6, "Meltdown radiation is " + magnitude + " instead of " + expected);
                    player[0] = listening(helper, ORIGIN.offset(SIZE_XZ, 1, 1));
                    player[0].setNoGravity(true);
                    startDose[0] = dose(player[0]);
                    phase[0] = 1;
                    check(false, "Waiting for the dose");
                }
                player[0].doTick();
                double current = dose(player[0]);
                check(current > startDose[0], "Dose stayed at " + current);
                double level = RadiationManager.INSTANCE.getRadiationLevelAndMaxMagnitude(player[0]).level();
                IRadiationSource source = sourceAt(center);
                check(source != null, "The meltdown radiation disappeared");
                double exposure = source.getMagnitude() / Math.max(1, center.distanceToSquared(new Coord4D(player[0])));
                check(level >= RadiationManager.BASELINE + exposure * MekanismConfig.general.radiationSourceDecayRate.get(),
                      "The field at the player is " + level + " from an exposure of " + exposure);
                reportDose(player[0], current, level);
                RadiationManager.INSTANCE.removeRadiationSource(center);
                helper.succeed();
            } catch (RuntimeException e) {
                if (!(e instanceof GameTestAssertException) && center != null) {
                    RadiationManager.INSTANCE.removeRadiationSource(center);
                }
                throw e;
            }
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void reactorContentsSurviveSaveAndLoad(GameTestHelper helper) {
        Reactor reactor = place(helper, ORIGIN);
        long[] fuel = new long[1];
        long[] sodium = new long[1];
        long[] waste = new long[1];
        long[] heated = new long[1];
        double[] damage = new double[1];
        boolean[] reloaded = {false};
        helper.succeedWhen(() -> {
            if (!reloaded[0]) {
                TileEntityFissionReactorPort input = input(helper, reactor);
                FissionReactorMultiblockData data = formed(input);
                Direction side = reactor.shell().valveSide();
                long filled = TestPlatform.insertBlockGas(input, side, MekanismGases.FISSILE_FUEL.get(), 400, true);
                check(filled == 400, "The input port accepted " + filled + " fissile fuel before the save");
                long coolant = TestPlatform.insertBlockGas(input, side, MekanismGases.SODIUM.get(), 800, true);
                check(coolant == 800, "The input port accepted " + coolant + " sodium before the save");
                data.wasteTank.setStack(MekanismGases.NUCLEAR_WASTE.getStack(80));
                data.heatedCoolantTank.setStack(MekanismGases.SUPERHEATED_SODIUM.getStack(120));
                data.reactorDamage = 25;
                data.setRateLimit(0.4);
                data.setActive(false);
                logic(helper, reactor).setLogicTypeFromPacket(FissionReactorLogic.TEMPERATURE);
                fuel[0] = data.fuelTank.getStored();
                sodium[0] = data.gasCoolantTank.getStored();
                waste[0] = data.wasteTank.getStored();
                heated[0] = data.heatedCoolantTank.getStored();
                damage[0] = data.reactorDamage;
                List<SavedBlock> saved = new ArrayList<>();
                for (BlockPos pos : reactor.shell().blocks()) {
                    saved.add(save(helper, pos));
                }
                saved.add(save(helper, reactor.fuel()));
                saved.add(save(helper, reactor.rod()));
                MekanismGenerators.fissionReactorManager.inventories.remove(data.inventoryID);
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
            TileEntityFissionReactorPort restored = input(helper, reactor);
            FissionReactorMultiblockData data = formed(restored);
            check(data.fuelTank.getStack().getType() == MekanismGases.FISSILE_FUEL.get() && data.fuelTank.getStored() == fuel[0],
                  "Reloaded reactor holds " + data.fuelTank.getStack());
            check(data.gasCoolantTank.getStack().getType() == MekanismGases.SODIUM.get() && data.gasCoolantTank.getStored() == sodium[0],
                  "Reloaded sodium is " + data.gasCoolantTank.getStack());
            check(data.wasteTank.getStack().getType() == MekanismGases.NUCLEAR_WASTE.get() && data.wasteTank.getStored() == waste[0],
                  "Reloaded waste is " + data.wasteTank.getStack());
            check(data.heatedCoolantTank.getStack().getType() == MekanismGases.SUPERHEATED_SODIUM.get()
                        && data.heatedCoolantTank.getStored() == heated[0],
                  "Reloaded heated coolant is " + data.heatedCoolantTank.getStack());
            check(!data.isActive(), "Reloaded reactor is active");
            check(data.rateLimit == 0.4, "Reloaded burn rate is " + data.rateLimit);
            check(data.reactorDamage <= damage[0] && data.reactorDamage > damage[0] - 1,
                  "Reloaded damage is " + data.reactorDamage + " from " + damage[0]);
            check(logic(helper, reactor).getMode() == FissionReactorLogic.TEMPERATURE, "Reloaded logic mode is " + logic(helper, reactor).getMode());
            check(helper.getBlockState(reactor.waste()).getValue(AttributeStateFissionPortMode.modeProperty) == FissionPortMode.OUTPUT_WASTE,
                  "Reloaded waste port changed mode");
            // The input port's gas lookup exposes every tank. Extracting from it stays blocked.
            long reported = TestPlatform.blockGas(restored, reactor.shell().valveSide());
            check(reported == fuel[0] + sodium[0] + waste[0] + heated[0],
                  "The gas lookup reports " + reported + " after reload");
            check(TestPlatform.blockTemperature(restored, reactor.shell().valveSide()) == data.heatCapacitor.getTemperature(),
                  "The heat lookup reports " + TestPlatform.blockTemperature(restored, reactor.shell().valveSide()) + " after reload");
        });
    }

    /** The heated-coolant amount {@code handleCoolant} produces from the temperature it actually read. */
    private static long expectedSodiumBoil(double temperature, double heatCapacity, double efficiency, long stored) {
        CooledCoolant coolant = sodiumCoolant();
        double heat = efficiency * (temperature - HeatUtils.BASE_BOIL_TEMP) * heatCapacity;
        return clampHeated(heat * coolant.getConductivity() / coolant.getThermalEnthalpy(), stored);
    }

    private static long expectedWaterBoil(double temperature, double heatCapacity, double efficiency, long stored) {
        double caseCoolantHeat = efficiency * (temperature - HeatUtils.BASE_BOIL_TEMP) * heatCapacity * WATER_CONDUCTIVITY;
        double heated = HeatUtils.getSteamEnergyEfficiency() * caseCoolantHeat / HeatUtils.getWaterThermalEnthalpy();
        return clampHeated(heated, stored);
    }

    private static long clampHeated(double heated, long stored) {
        long value = heated < Long.MAX_VALUE ? (long) heated : Long.MAX_VALUE;
        if (value < 0) {
            return 0;
        }
        return Math.min(value, stored);
    }

    private static CooledCoolant sodiumCoolant() {
        for (ChemicalAttribute attribute : MekanismGases.SODIUM.get().getAttributes()) {
            if (attribute instanceof CooledCoolant coolant) {
                return coolant;
            }
        }
        throw new IllegalStateException("Sodium has no cooled coolant attribute");
    }

    private static void activate(GameTestHelper helper, Reactor reactor) {
        TileEntityFissionReactorLogicAdapter logic = logic(helper, reactor);
        logic.setLogicTypeFromPacket(FissionReactorLogic.ACTIVATION);
        helper.setBlock(reactor.logic().relative(Direction.WEST), Blocks.REDSTONE_BLOCK);
        check(logic.getMultiblock().isActive(), "Redstone on the activation adapter did not start the reactor");
    }

    private static int signal(GameTestHelper helper, BlockPos logic) {
        return helper.getLevel().getSignal(helper.absolutePos(logic), Direction.UP);
    }

    private static void rememberMeltdownConfig() {
        if (!remembered) {
            savedMeltdowns = MekanismGeneratorsConfig.generators.fissionMeltdownsEnabled.get();
            savedChance = MekanismGeneratorsConfig.generators.fissionMeltdownChance.get();
            savedRadius = MekanismGeneratorsConfig.generators.fissionMeltdownRadius.get();
            remembered = true;
        }
    }

    private static void restoreMeltdownConfig() {
        if (!remembered) {
            return;
        }
        MekanismGeneratorsConfig.generators.fissionMeltdownsEnabled.set(savedMeltdowns);
        MekanismGeneratorsConfig.generators.fissionMeltdownChance.set(savedChance);
        MekanismGeneratorsConfig.generators.fissionMeltdownRadius.set(savedRadius);
    }

    @SuppressWarnings("unchecked")
    private static List<Meltdown> meltdowns(GameTestHelper helper) {
        try {
            Field field = RadiationManager.class.getDeclaredField("meltdowns");
            field.setAccessible(true);
            Map<ResourceLocation, List<Meltdown>> map = (Map<ResourceLocation, List<Meltdown>>) field.get(RadiationManager.INSTANCE);
            return map.computeIfAbsent(helper.getLevel().dimension().location(), id -> new ArrayList<>());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void clearMeltdowns(GameTestHelper helper) {
        meltdowns(helper).clear();
    }

    /**
     * Edges are casing and the faces are reactor glass. The north face centre is the input, the south face holds the
     * waste port, the east face holds the coolant port and the west face holds the logic adapter. The fuel assembly and
     * its control rod fill the interior column.
     */
    private static Reactor place(GameTestHelper helper, BlockPos origin) {
        BlockPos fuel = origin.offset(1, 1, 1);
        BlockPos rod = origin.offset(1, 2, 1);
        helper.setBlock(fuel, GeneratorsBlocks.FISSION_FUEL_ASSEMBLY.getBlock());
        helper.setBlock(rod, GeneratorsBlocks.CONTROL_ROD_ASSEMBLY.getBlock());
        Shell shell = MultiblockStructures.cuboid(helper, origin, SIZE_XZ, SIZE_Y, SIZE_XZ, GeneratorsBlocks.FISSION_REACTOR_CASING.getBlock(),
              GeneratorsBlocks.REACTOR_GLASS.getBlock(), GeneratorsBlocks.FISSION_REACTOR_PORT.getBlock());
        BlockPos waste = origin.offset(1, 1, SIZE_XZ - 1);
        BlockPos coolant = origin.offset(SIZE_XZ - 1, 1, 1);
        BlockPos logic = origin.offset(0, 1, 1);
        helper.setBlock(waste, portState(FissionPortMode.OUTPUT_WASTE));
        helper.setBlock(coolant, portState(FissionPortMode.OUTPUT_COOLANT));
        helper.setBlock(logic, GeneratorsBlocks.FISSION_REACTOR_LOGIC_ADAPTER.getBlock());
        return new Reactor(shell, fuel, rod, waste, Direction.SOUTH, coolant, Direction.EAST, logic);
    }

    /** Fuel, an air gap, then the control rod, inside a 3×5×3 shell. */
    private static Shell gappedColumn(GameTestHelper helper, BlockPos origin) {
        helper.setBlock(origin.offset(1, 1, 1), GeneratorsBlocks.FISSION_FUEL_ASSEMBLY.getBlock());
        helper.setBlock(origin.offset(1, 3, 1), GeneratorsBlocks.CONTROL_ROD_ASSEMBLY.getBlock());
        return casingShell(helper, origin, 5);
    }

    private static Shell casingShell(GameTestHelper helper, BlockPos origin, int sizeY) {
        return MultiblockStructures.cuboid(helper, origin, SIZE_XZ, sizeY, SIZE_XZ, GeneratorsBlocks.FISSION_REACTOR_CASING.getBlock(),
              GeneratorsBlocks.FISSION_REACTOR_CASING.getBlock(), GeneratorsBlocks.FISSION_REACTOR_PORT.getBlock());
    }

    private static BlockState portState(FissionPortMode mode) {
        return GeneratorsBlocks.FISSION_REACTOR_PORT.getBlock().defaultBlockState().setValue(AttributeStateFissionPortMode.modeProperty, mode);
    }

    private static SavedBlock save(GameTestHelper helper, BlockPos pos) {
        return new SavedBlock(pos, helper.getBlockState(pos), helper.getBlockEntity(pos).saveWithFullMetadata());
    }

    /** The shell stayed unformed, and its casings were not absorbed by a neighbour. */
    private static void checkSeparate(TileEntityFissionReactorCasing tile, int blocks, String name) {
        check(!tile.getMultiblock().isFormed(), name + " formed");
        check(tile.getStructure().size() == blocks, name + " shares a structure of " + tile.getStructure().size() + " blocks");
    }

    private static TileEntityFissionReactorPort input(GameTestHelper helper, Reactor reactor) {
        return port(helper, reactor.shell());
    }

    private static TileEntityFissionReactorPort port(GameTestHelper helper, Shell shell) {
        check(shell.valve() != null, "The shell has no port");
        return port(helper, shell.valve());
    }

    private static TileEntityFissionReactorPort port(GameTestHelper helper, BlockPos pos) {
        BlockEntity blockEntity = helper.getBlockEntity(pos);
        check(blockEntity instanceof TileEntityFissionReactorPort, "The port is " + blockEntity);
        return (TileEntityFissionReactorPort) blockEntity;
    }

    private static TileEntityFissionReactorLogicAdapter logic(GameTestHelper helper, Reactor reactor) {
        BlockEntity blockEntity = helper.getBlockEntity(reactor.logic());
        check(blockEntity instanceof TileEntityFissionReactorLogicAdapter, "The logic adapter is " + blockEntity);
        return (TileEntityFissionReactorLogicAdapter) blockEntity;
    }

    private static FissionReactorMultiblockData formed(TileEntityFissionReactorCasing tile) {
        FissionReactorMultiblockData data = tile.getMultiblock();
        check(data.isFormed(), "The fission reactor is not formed");
        return data;
    }

    private static TileEntityFissionReactorCasing master(GameTestHelper helper, Shell shell) {
        TileEntityFissionReactorCasing master = null;
        for (BlockPos pos : shell.blocks()) {
            BlockEntity blockEntity = helper.getBlockEntity(pos);
            if (blockEntity instanceof TileEntityFissionReactorCasing casing && casing.isMaster()) {
                check(master == null, "The reactor has two masters");
                master = casing;
            }
        }
        check(master != null, "The formed reactor has no master");
        return master;
    }

    /** Fills a formed reactor so a client can draw the burn glow, the water and the fuel gauge. */
    public static void primeRunningReactor(FissionReactorMultiblockData data) {
        data.setActive(false);
        data.fuelTank.setStack(MekanismGases.FISSILE_FUEL.getStack(Math.max(1L, data.fuelTank.getCapacity() / 2)));
        data.fluidCoolantTank.setStack(new FluidStack(Fluids.WATER, Math.max(1, data.fluidCoolantTank.getCapacity() / 5)));
        data.setRateLimit(Math.min(1.0D, data.getMaxBurnRate()));
        data.setActive(true);
    }

    private static void checkNotRadioactive(Gas gas, String name) {
        check(!gas.has(GasAttributes.Radiation.class), name + " is radioactive");
    }

    private static double wasteRadioactivity() {
        GasAttributes.Radiation radiation = MekanismGases.NUCLEAR_WASTE.get().get(GasAttributes.Radiation.class);
        check(radiation != null && radiation.getRadioactivity() == 0.01D, "Nuclear waste radioactivity is " + radiation);
        return radiation.getRadioactivity();
    }

    private static IRadiationSource sourceAt(Coord4D center) {
        return RadiationManager.INSTANCE.getRadiationSources().get(new Chunk3D(center), center);
    }

    private static void checkMeltdownRadius(GameTestHelper helper, BlockPos min) {
        Meltdown meltdown = meltdownAt(helper, min);
        check(meltdown != null, "Crossing the damage threshold queued no meltdown");
        CompoundTag tag = new CompoundTag();
        meltdown.write(tag);
        check(tag.getFloat(NBTConstants.RADIUS) == 8, "Meltdown radius is " + tag.getFloat(NBTConstants.RADIUS));
    }

    private static Meltdown meltdownAt(GameTestHelper helper, BlockPos min) {
        for (Meltdown meltdown : meltdowns(helper)) {
            CompoundTag tag = new CompoundTag();
            meltdown.write(tag);
            if (NbtUtils.readBlockPos(tag.getCompound(NBTConstants.MIN)).equals(min)) {
                return meltdown;
            }
        }
        return null;
    }

    private static boolean shellBroken(GameTestHelper helper, Reactor reactor) {
        Block casing = GeneratorsBlocks.FISSION_REACTOR_CASING.getBlock();
        Block glass = GeneratorsBlocks.REACTOR_GLASS.getBlock();
        Block port = GeneratorsBlocks.FISSION_REACTOR_PORT.getBlock();
        Block logic = GeneratorsBlocks.FISSION_REACTOR_LOGIC_ADAPTER.getBlock();
        for (BlockPos pos : reactor.shell().blocks()) {
            Block block = helper.getBlockState(pos).getBlock();
            if (block != casing && block != glass && block != port && block != logic) {
                return true;
            }
        }
        return false;
    }

    private static double dose(ServerPlayer player) {
        var capability = player.getCapability(Capabilities.RADIATION_ENTITY);
        check(capability.isPresent(), "The player has no radiation capability");
        return capability.orElseThrow(IllegalStateException::new).getRadiation();
    }

    private static void reportDose(ListeningPlayer player, double dose, double level) {
        check(dose > RadiationManager.BASELINE, "Dose " + dose + " is still the baseline");
        check(level > RadiationManager.BASELINE, "The field " + level + " is still the baseline");
        player.messages.clear();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MekanismItems.DOSIMETER.get()));
        MekanismItems.DOSIMETER.get().use(player.getLevel(), player, InteractionHand.MAIN_HAND);
        String doseText = UnitDisplayUtils.getDisplayShort(dose, RadiationUnit.SV, 3).getString();
        check(player.messages.stream().anyMatch(message -> message.contains(doseText)),
              "Dosimeter said " + player.messages + " for " + doseText);
        player.messages.clear();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(MekanismItems.GEIGER_COUNTER.get()));
        MekanismItems.GEIGER_COUNTER.get().use(player.getLevel(), player, InteractionHand.MAIN_HAND);
        String fieldText = UnitDisplayUtils.getDisplayShort(level, RadiationUnit.SVH, 3).getString();
        check(player.messages.stream().anyMatch(message -> message.contains(fieldText)),
              "Geiger counter said " + player.messages + " for " + fieldText);
    }

    private static ListeningPlayer listening(GameTestHelper helper, BlockPos relative) {
        ListeningPlayer player = new ListeningPlayer(helper, new GameProfile(UUID.randomUUID(), "fission-dose"));
        new ServerGamePacketListenerImpl(helper.getLevel().getServer(), new Connection(PacketFlow.SERVERBOUND), player);
        player.setGameMode(GameType.SURVIVAL);
        BlockPos absolute = helper.absolutePos(relative);
        player.moveTo(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5, 0, 0);
        return player;
    }

    private record Reactor(Shell shell, BlockPos fuel, BlockPos rod, BlockPos waste, Direction wasteSide, BlockPos coolant, Direction coolantSide,
                           BlockPos logic) {
    }

    private static final class ListeningPlayer extends ServerPlayer {

        private final List<String> messages = new ArrayList<>();

        private ListeningPlayer(GameTestHelper helper, GameProfile profile) {
            super(helper.getLevel().getServer(), helper.getLevel(), profile, null);
        }

        @Override
        public void sendSystemMessage(Component component) {
            messages.add(component.getString());
        }
    }

    private record SavedBlock(BlockPos pos, BlockState state, CompoundTag tag) {
    }
}

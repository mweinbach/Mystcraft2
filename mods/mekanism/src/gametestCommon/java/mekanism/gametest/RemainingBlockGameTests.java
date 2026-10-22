/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import static mekanism.gametest.CoreMachineGameTests.TEMPLATE;
import static mekanism.gametest.CoreMachineGameTests.check;

import java.util.UUID;
import com.mojang.authlib.GameProfile;
import mekanism.api.Coord4D;
import mekanism.api.RelativeSide;
import mekanism.api.math.FloatingLong;
import mekanism.api.security.SecurityMode;
import mekanism.common.Mekanism;
import mekanism.common.content.gear.IModuleContainerItem;
import mekanism.common.entity.EntityRobit;
import mekanism.common.item.ItemCraftingFormula;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.registries.MekanismEntityTypes;
import mekanism.common.registries.MekanismItems;
import mekanism.common.registries.MekanismModules;
import mekanism.common.tile.TileEntityChargepad;
import mekanism.common.tile.TileEntityIndustrialAlarm;
import mekanism.common.tile.TileEntityModificationStation;
import mekanism.common.tile.TileEntityPersonalBarrel;
import mekanism.common.tile.TileEntityPersonalChest;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.tile.machine.TileEntityDimensionalStabilizer;
import mekanism.common.tile.machine.TileEntityElectricPump;
import mekanism.common.tile.machine.TileEntityFluidicPlenisher;
import mekanism.common.tile.machine.TileEntityFormulaicAssemblicator;
import mekanism.common.tile.machine.TileEntityFuelwoodHeater;
import mekanism.common.tile.machine.TileEntityResistiveHeater;
import mekanism.common.tile.machine.TileEntitySeismicVibrator;
import mekanism.common.util.StorageUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SculkSensorPhase;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;

/**
 * The blocks that are not part of a multiblock or a machine family: the dimensional stabilizer keeps ticking chunk
 * tickets, the seismic vibrator emits its vibration, the alarm answers redstone, the heaters make heat, the chargepad
 * fills a Robit and an item, the pump and plenisher move water, the assemblicator crafts from a formula, the
 * modification station installs a module, and personal storage keeps a private inventory.
 */
public class RemainingBlockGameTests {

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void dimensionalStabilizerLoadsItsChunk(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, MekanismBlocks.DIMENSIONAL_STABILIZER.getBlock());
        TileEntityDimensionalStabilizer stabilizer = (TileEntityDimensionalStabilizer) helper.getBlockEntity(pos);
        BlockPos absolute = helper.absolutePos(pos);
        ServerLevel level = helper.getLevel();
        ChunkPos chunk = new ChunkPos(absolute);
        ChunkPos corner = new ChunkPos(chunk.x - TileEntityDimensionalStabilizer.MAX_LOAD_RADIUS,
              chunk.z - TileEntityDimensionalStabilizer.MAX_LOAD_RADIUS);
        FloatingLong oneChunk = stabilizer.getEnergyContainer().getEnergyPerTick();
        stabilizer.toggleChunkLoadingAt(TileEntityDimensionalStabilizer.MAX_LOAD_RADIUS, TileEntityDimensionalStabilizer.MAX_LOAD_RADIUS);
        check(stabilizer.getChunkSet().size() == 1, "The center chunk left the stabilizer's set");
        check(stabilizer.getEnergyContainer().getEnergyPerTick().equals(oneChunk), "Toggling the center chunk changed the energy use");
        stabilizer.toggleChunkLoadingAt(0, 0);
        check(stabilizer.getChunkSet().size() == 2, "The corner chunk was not added: " + stabilizer.getChunkSet());
        check(stabilizer.getChunkSet().contains(chunk) && stabilizer.getChunkSet().contains(corner),
              "The loaded chunks were " + stabilizer.getChunkSet());
        check(stabilizer.getEnergyContainer().getEnergyPerTick().equals(oneChunk.multiply(2)),
              "Two chunks used " + stabilizer.getEnergyContainer().getEnergyPerTick() + " instead of " + oneChunk.multiply(2));
        int[] phase = {0};
        helper.succeedWhen(() -> {
            if (phase[0] < 3) {
                check(!stabilizer.getActive(), "An unpowered stabilizer became active");
                check(stabilizer.getRedstoneLevel() == 0, "An unpowered stabilizer output redstone " + stabilizer.getRedstoneLevel());
                check(!TestPlatform.tickingChunkLoaded(level, absolute, chunk),
                      "An unpowered stabilizer loaded " + chunk + ": " + ChunkLoadingGameTests.chunkTicketDebug(level, chunk));
                phase[0]++;
                throw new GameTestAssertException("The stabilizer is still unpowered");
            }
            long inserted = fill(stabilizer, Direction.NORTH, 40_000);
            if (phase[0] == 3) {
                check(inserted > 0, "The stabilizer accepted no energy");
                phase[0] = 4;
                throw new GameTestAssertException("The stabilizer is charging");
            }
            check(stabilizer.getActive(), "The stabilizer did not stay active");
            check(stabilizer.getRedstoneLevel() == 15, "An active stabilizer output redstone " + stabilizer.getRedstoneLevel());
            check(TestPlatform.tickingChunkLoaded(level, absolute, chunk),
                  "The stabilizer's chunk was not kept ticking: " + ChunkLoadingGameTests.chunkTicketDebug(level, chunk));
            check(TestPlatform.tickingChunkLoaded(level, absolute, corner),
                  "The extra chunk was not kept ticking: " + ChunkLoadingGameTests.chunkTicketDebug(level, corner));
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void seismicVibratorVibratesWhilePowered(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        BlockPos idle = new BlockPos(5, 2, 2);
        BlockPos sensor = new BlockPos(3, 2, 2);
        helper.setBlock(sensor, Blocks.SCULK_SENSOR);
        helper.setBlock(pos, MekanismBlocks.SEISMIC_VIBRATOR.getBlock());
        helper.setBlock(idle, MekanismBlocks.SEISMIC_VIBRATOR.getBlock());
        TileEntitySeismicVibrator vibrator = (TileEntitySeismicVibrator) helper.getBlockEntity(pos);
        TileEntitySeismicVibrator quiet = (TileEntitySeismicVibrator) helper.getBlockEntity(idle);
        check(fill(vibrator, side(vibrator, RelativeSide.BACK), 20_000) > 0, "The seismic vibrator accepted no energy");
        Coord4D vibrating = vibrator.getTileCoord();
        helper.succeedWhen(() -> {
            check(vibrator.getActive(), "The powered vibrator stayed idle");
            check(Mekanism.activeVibrators.contains(vibrating), "The powered vibrator was not vibrating");
            check(!quiet.getActive() && !Mekanism.activeVibrators.contains(quiet.getTileCoord()), "The unpowered vibrator vibrated");
            SculkSensorPhase phase = helper.getBlockState(sensor).getValue(BlockStateProperties.SCULK_SENSOR_PHASE);
            check(phase != SculkSensorPhase.INACTIVE, "The sculk sensor stayed " + phase);
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void industrialAlarmActivatesFromRedstone(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos.below(), Blocks.STONE);
        helper.setBlock(pos, MekanismBlocks.INDUSTRIAL_ALARM.getBlock().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
        TileEntityIndustrialAlarm alarm = (TileEntityIndustrialAlarm) helper.getBlockEntity(pos);
        check(!alarm.getActive(), "The alarm started active");
        check(!fullBlock(TestPlatform.renderBounds(alarm)), "An idle alarm rendered the full block " + TestPlatform.renderBounds(alarm));
        helper.setBlock(pos.north(), Blocks.REDSTONE_BLOCK);
        helper.succeedWhen(() -> {
            check(alarm.getActive(), "Redstone did not turn the alarm on");
            AABB bounds = TestPlatform.renderBounds(alarm);
            check(fullBlock(bounds), "An active alarm rendered " + bounds);
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void fuelwoodHeaterBurnsFuel(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, MekanismBlocks.FUELWOOD_HEATER.getBlock());
        TileEntityFuelwoodHeater heater = (TileEntityFuelwoodHeater) helper.getBlockEntity(pos);
        heater.getInventorySlots(null).get(0).setStack(new ItemStack(Items.COAL));
        double start = TestPlatform.blockTemperature(heater, Direction.UP);
        helper.succeedWhen(() -> {
            check(heater.getInventorySlots(null).get(0).isEmpty(), "The coal was not burned");
            check(heater.getActive(), "The fuelwood heater stayed idle while burning");
            double temperature = TestPlatform.blockTemperature(heater, Direction.UP);
            check(temperature > start + 1, "The fuelwood heater rose from " + start + " to " + temperature);
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void resistiveHeaterTurnsEnergyIntoHeat(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, MekanismBlocks.RESISTIVE_HEATER.getBlock());
        TileEntityResistiveHeater heater = (TileEntityResistiveHeater) helper.getBlockEntity(pos);
        check(fill(heater, side(heater, RelativeSide.LEFT), 40_000) > 0, "The resistive heater accepted no energy");
        double start = TestPlatform.blockTemperature(heater, Direction.UP);
        helper.succeedWhen(() -> {
            check(heater.getActive(), "The resistive heater stayed idle");
            check(heater.getEnergyUsed().greaterThan(FloatingLong.ZERO), "The resistive heater used no energy");
            double temperature = TestPlatform.blockTemperature(heater, Direction.UP);
            check(temperature > start + 1, "The resistive heater rose from " + start + " to " + temperature);
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void chargepadChargesRobit(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos.below(), Blocks.STONE);
        helper.setBlock(pos, MekanismBlocks.CHARGEPAD.getBlock());
        TileEntityChargepad pad = (TileEntityChargepad) helper.getBlockEntity(pos);
        check(fill(pad, side(pad, RelativeSide.BOTTOM), 2_000_000) > 0, "The chargepad accepted no energy");
        EntityRobit robit = helper.spawnWithNoFreeWill(MekanismEntityTypes.ROBIT.get(), pos);
        robit.setNoGravity(true);
        robit.setHome(pad.getTileCoord());
        helper.succeedWhen(() -> {
            check(robit.getEnergyContainer().getEnergy().equals(EntityRobit.MAX_ENERGY),
                  "The Robit held " + robit.getEnergyContainer().getEnergy());
            check(pad.getActive(), "The chargepad stayed idle with a Robit on it");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void chargepadChargesAnEnergyTablet(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos.below(), Blocks.STONE);
        helper.setBlock(pos, MekanismBlocks.CHARGEPAD.getBlock());
        TileEntityChargepad pad = (TileEntityChargepad) helper.getBlockEntity(pos);
        check(fill(pad, side(pad, RelativeSide.BOTTOM), 2_000_000) > 0, "The chargepad accepted no energy");
        ServerPlayer player = survival(helper, pos);
        player.setNoGravity(true);
        player.getInventory().setItem(0, new ItemStack(MekanismItems.ENERGY_TABLET.get()));
        helper.getLevel().addDuringPortalTeleport(player);
        FloatingLong[] charged = {FloatingLong.ZERO};
        boolean[] revived = {false};
        helper.succeedWhen(() -> {
            ItemStack tablet = player.getInventory().getItem(0);
            check(tablet.is(MekanismItems.ENERGY_TABLET.get()), "The tablet left the player's hand");
            FloatingLong energy = StorageUtils.getStoredEnergyFromNBT(tablet);
            check(energy.greaterThan(FloatingLong.ZERO), "The tablet stayed empty");
            check(pad.getActive(), "The chargepad stayed idle with a player on it");
            if (!revived[0]) {
                charged[0] = energy;
                // A dimension change removes the player and then revives the same instance.
                player.invalidateCaps();
                player.reviveCaps();
                revived[0] = true;
                throw new GameTestAssertException("The player's capabilities were revived");
            }
            check(energy.greaterThan(charged[0]), "After the capabilities were revived the tablet stayed at " + energy);
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void electricPumpPullsWater(GameTestHelper helper) {
        BlockPos water = new BlockPos(1, 1, 1);
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(water, Blocks.WATER);
        helper.setBlock(pos, MekanismBlocks.ELECTRIC_PUMP.getBlock());
        TileEntityElectricPump pump = (TileEntityElectricPump) helper.getBlockEntity(pos);
        check(fill(pump, side(pump, RelativeSide.BACK), 40_000) > 0, "The electric pump accepted no energy");
        helper.succeedWhen(() -> {
            check(pump.fluidTank.getFluidAmount() >= 1_000, "The pump held " + pump.fluidTank.getFluidAmount() + " mB");
            check(pump.fluidTank.getFluid().getFluid() == Fluids.WATER, "The pump held " + pump.fluidTank.getFluid().getFluid());
            check(TestPlatform.blockFluid(pump, Direction.UP) >= 1_000, "The pump's top held " + TestPlatform.blockFluid(pump, Direction.UP) + " mB");
            BlockState state = helper.getBlockState(water);
            check(state.getFluidState().isSource() && state.getFluidState().getType() == Fluids.WATER,
                  "The water source became " + state);
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void fluidicPlenisherPlacesWater(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, MekanismBlocks.FLUIDIC_PLENISHER.getBlock());
        TileEntityFluidicPlenisher plenisher = (TileEntityFluidicPlenisher) helper.getBlockEntity(pos);
        long accepted = TestPlatform.insertBlockFluid(plenisher, Direction.UP, Fluids.WATER, 2_000, true);
        check(accepted >= 1_000, "The plenisher accepted " + accepted + " mB");
        check(fill(plenisher, side(plenisher, RelativeSide.BACK), 40_000) > 0, "The plenisher accepted no energy");
        int started = plenisher.fluidTank.getFluidAmount();
        helper.succeedWhen(() -> {
            BlockState below = helper.getBlockState(pos.below());
            check(below.getFluidState().isSource() && below.getFluidState().getType() == Fluids.WATER,
                  "The block below the plenisher was " + below);
            check(plenisher.fluidTank.getFluidAmount() <= started - 1_000,
                  "The plenisher tank went from " + started + " to " + plenisher.fluidTank.getFluidAmount());
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void formulaicAssemblicatorCraftsFromFormula(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, MekanismBlocks.FORMULAIC_ASSEMBLICATOR.getBlock());
        TileEntityFormulaicAssemblicator assembler = (TileEntityFormulaicAssemblicator) helper.getBlockEntity(pos);
        ItemStack formula = new ItemStack(MekanismItems.CRAFTING_FORMULA.get());
        NonNullList<ItemStack> grid = NonNullList.withSize(9, ItemStack.EMPTY);
        grid.set(0, new ItemStack(Items.OAK_PLANKS));
        grid.set(3, new ItemStack(Items.OAK_PLANKS));
        ((ItemCraftingFormula) formula.getItem()).setInventory(formula, grid);
        assembler.getFormulaSlot().setStack(formula);
        assembler.getInventorySlots(null).get(1).setStack(new ItemStack(Items.OAK_PLANKS, 8));
        check(fill(assembler, Direction.NORTH, 40_000) > 0, "The assemblicator accepted no energy");
        helper.succeedWhen(() -> {
            check(assembler.formula != null, "The formula was not read");
            if (!assembler.getAutoMode()) {
                assembler.nextMode();
            }
            check(assembler.getAutoMode(), "Auto mode stayed off with a formula loaded");
            check(sticks(assembler) == 4, "The assemblicator held " + sticks(assembler) + " sticks");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void modificationStationInstallsModule(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, MekanismBlocks.MODIFICATION_STATION.getBlock());
        TileEntityModificationStation station = (TileEntityModificationStation) helper.getBlockEntity(pos);
        station.getInventorySlots(null).get(0).setStack(new ItemStack(MekanismItems.MODULE_ENERGY.get()));
        station.getInventorySlots(null).get(1).setStack(new ItemStack(MekanismItems.MEKA_TOOL.get()));
        check(fill(station, side(station, RelativeSide.BACK), 40_000) > 0, "The modification station accepted no energy");
        helper.succeedWhen(() -> {
            check(station.getInventorySlots(null).get(0).isEmpty(), "The energy unit was not consumed");
            ItemStack tool = station.getInventorySlots(null).get(1).getStack();
            check(tool.getItem() instanceof IModuleContainerItem container && container.hasModule(tool, MekanismModules.ENERGY_UNIT),
                  "The Meka-Tool did not gain an energy unit");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void personalStorageRejectsAutomationWhenPrivate(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, MekanismBlocks.PERSONAL_BARREL.getBlock());
        TileEntityPersonalBarrel barrel = (TileEntityPersonalBarrel) helper.getBlockEntity(pos);
        check(barrel.getInventorySlots(null).size() == 54, "The barrel had " + barrel.getInventorySlots(null).size() + " slots");
        long inserted = TestPlatform.insertItems(barrel, Direction.NORTH, new ItemStack(Items.DIAMOND));
        check(inserted == 1, "A public barrel accepted " + inserted + " diamonds");
        check(TestPlatform.countItems(barrel, Direction.NORTH, Items.DIAMOND) == 1, "The public barrel did not show its diamond");
        barrel.setSecurityMode(SecurityMode.PRIVATE);
        long blocked = TestPlatform.insertItems(barrel, Direction.NORTH, new ItemStack(Items.EMERALD));
        check(blocked == 0, "A private barrel accepted " + blocked + " emeralds");
        barrel.getInventorySlots(null).get(1).setStack(new ItemStack(Items.EMERALD));
        check(barrel.getInventorySlots(null).get(1).getStack().is(Items.EMERALD), "A manual insert did not stay in the private barrel");
        check(TestPlatform.countItems(barrel, Direction.NORTH, Items.DIAMOND) == 1, "The private barrel lost its diamond");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void personalBarrelOpensAndCoveredChestStaysShut(GameTestHelper helper) {
        BlockPos barrelPos = new BlockPos(1, 2, 1);
        helper.setBlock(barrelPos, MekanismBlocks.PERSONAL_BARREL.getBlock().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
        TileEntityPersonalBarrel barrel = (TileEntityPersonalBarrel) helper.getBlockEntity(barrelPos);
        barrel.open(helper.makeMockPlayer());
        check(helper.getBlockState(barrelPos).getValue(BlockStateProperties.OPEN), "The personal barrel stayed shut");

        BlockPos chestPos = new BlockPos(4, 2, 1);
        helper.setBlock(chestPos.above(), Blocks.STONE);
        helper.setBlock(chestPos, MekanismBlocks.PERSONAL_CHEST.getBlock());
        TileEntityPersonalChest chest = (TileEntityPersonalChest) helper.getBlockEntity(chestPos);
        InteractionResult result = chest.openGui(helper.makeMockPlayer());
        check(result == InteractionResult.CONSUME, "A covered personal chest returned " + result);
        helper.succeed();
    }

    private static int sticks(TileEntityFormulaicAssemblicator assembler) {
        int count = 0;
        for (int slot = 28; slot <= 33; slot++) {
            ItemStack stack = assembler.getInventorySlots(null).get(slot).getStack();
            if (stack.is(Items.STICK)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static long fill(TileEntityMekanism tile, Direction side, long amount) {
        return TestPlatform.insertEnergy(tile, side, amount);
    }

    private static Direction side(TileEntityMekanism tile, RelativeSide relative) {
        return relative.getDirection(tile.getDirection());
    }

    private static boolean fullBlock(AABB bounds) {
        return bounds.getXsize() > 0.99 && bounds.getYsize() > 0.99 && bounds.getZsize() > 0.99;
    }

    private static ServerPlayer survival(GameTestHelper helper, BlockPos relative) {
        ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
              new GameProfile(UUID.randomUUID(), "chargepad-test"), null);
        new ServerGamePacketListenerImpl(helper.getLevel().getServer(), new Connection(PacketFlow.SERVERBOUND), player);
        player.setGameMode(GameType.SURVIVAL);
        BlockPos absolute = helper.absolutePos(relative);
        player.moveTo(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5, 0, 0);
        return player;
    }
}

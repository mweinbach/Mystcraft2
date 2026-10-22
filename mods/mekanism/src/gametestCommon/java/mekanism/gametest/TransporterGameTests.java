/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import static mekanism.gametest.CoreMachineGameTests.TEMPLATE;
import static mekanism.gametest.CoreMachineGameTests.check;

import mekanism.api.text.EnumColor;
import mekanism.common.content.network.transmitter.DiversionTransporter;
import mekanism.common.content.network.transmitter.DiversionTransporter.DiversionControl;
import mekanism.common.content.transporter.SorterItemStackFilter;
import mekanism.common.content.transporter.SorterTagFilter;
import mekanism.common.lib.transmitter.ConnectionType;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.registries.MekanismGases;
import mekanism.common.tile.TileEntityChemicalTank;
import mekanism.common.tile.TileEntityLogisticalSorter;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.transmitter.TileEntityDiversionTransporter;
import mekanism.common.tile.transmitter.TileEntityLogisticalTransporter;
import mekanism.common.tile.transmitter.TileEntityMechanicalPipe;
import mekanism.common.tile.transmitter.TileEntityPressurizedTube;
import mekanism.common.tile.transmitter.TileEntityTransmitter;
import mekanism.common.util.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;

/**
 * Logistical transporters, the sorter and its filters, diversion and restrictive transporters, mechanical pipes and
 * pressurized tubes. Items are read back through the loader's item API. Fluid and gas move between Mekanism blocks.
 */
public class TransporterGameTests {

    private static final int MOVED = 5;
    private static final int FLUID = 500;
    private static final long GAS = 1_000;
    /** Longer than a one-segment pull and delivery, so a closed diversion has had its chance to move an item. */
    private static final int CLOSED_TICKS = 100;

    /**
     * A basic transporter set to pull takes iron from a chest and delivers it to a second chest. The chests are the
     * loader's item inventory: Forge's item handler, and the Transfer API on Fabric.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void itemsRideATransporterIntoAnInventory(GameTestHelper helper) {
        BlockEntity source = chest(helper, new BlockPos(1, 1, 1));
        BlockPos transporterPos = new BlockPos(2, 1, 1);
        helper.setBlock(transporterPos, MekanismBlocks.BASIC_LOGISTICAL_TRANSPORTER.getBlock());
        BlockEntity destination = chest(helper, new BlockPos(3, 1, 1));
        TileEntityLogisticalTransporter transporter = (TileEntityLogisticalTransporter) helper.getBlockEntity(transporterPos);
        setConnection(transporter, Direction.WEST, ConnectionType.PULL);
        setConnection(transporter, Direction.EAST, ConnectionType.PUSH);
        give(source, Items.IRON_INGOT, MOVED);
        helper.succeedWhen(() -> {
            int arrived = count(destination, Items.IRON_INGOT);
            if (arrived < MOVED) {
                throw new GameTestAssertException("The destination holds " + arrived + " of " + MOVED
                      + " iron ingots; the source holds " + count(source, Items.IRON_INGOT));
            }
        });
    }

    /**
     * An item filter paints iron red and a tag filter paints gold ingots blue. Each colour only enters the matching
     * transporter. Dirt matches neither filter, and auto-eject is off, so it stays in the source.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void sorterFiltersRouteByColor(GameTestHelper helper) {
        BlockEntity source = chest(helper, new BlockPos(1, 1, 1));
        BlockPos sorterPos = new BlockPos(2, 1, 1);
        helper.setBlock(sorterPos, MekanismBlocks.LOGISTICAL_SORTER.getBlock().defaultBlockState()
              .setValue(BlockStateProperties.FACING, Direction.EAST));
        BlockPos trunkPos = new BlockPos(3, 1, 1);
        BlockPos redPos = new BlockPos(4, 1, 1);
        BlockPos bluePos = new BlockPos(3, 1, 2);
        helper.setBlock(trunkPos, MekanismBlocks.BASIC_LOGISTICAL_TRANSPORTER.getBlock());
        helper.setBlock(redPos, MekanismBlocks.BASIC_LOGISTICAL_TRANSPORTER.getBlock());
        helper.setBlock(bluePos, MekanismBlocks.BASIC_LOGISTICAL_TRANSPORTER.getBlock());
        BlockEntity redChest = chest(helper, new BlockPos(5, 1, 1));
        BlockEntity blueChest = chest(helper, new BlockPos(3, 1, 3));
        setColor((TileEntityLogisticalTransporter) helper.getBlockEntity(redPos), EnumColor.RED);
        setColor((TileEntityLogisticalTransporter) helper.getBlockEntity(bluePos), EnumColor.DARK_BLUE);
        TileEntityLogisticalSorter sorter = (TileEntityLogisticalSorter) helper.getBlockEntity(sorterPos);
        SorterItemStackFilter iron = new SorterItemStackFilter();
        iron.setItemStack(new ItemStack(Items.IRON_INGOT));
        iron.color = EnumColor.RED;
        SorterTagFilter gold = new SorterTagFilter();
        gold.setTagName("forge:ingots/gold");
        gold.color = EnumColor.DARK_BLUE;
        check(sorter.getFilterManager().addFilter(iron), "The iron filter was rejected");
        check(sorter.getFilterManager().addFilter(gold), "The gold tag filter was rejected");
        give(source, Items.IRON_INGOT, 1);
        give(source, Items.GOLD_INGOT, 1);
        give(source, Items.DIRT, 1);
        helper.succeedWhen(() -> {
            check(count(blueChest, Items.IRON_INGOT) == 0, "Iron entered the blue transporter");
            check(count(redChest, Items.GOLD_INGOT) == 0, "Gold entered the red transporter");
            check(count(source, Items.DIRT) == 1, "Dirt left the source without a matching filter");
            int ironArrived = count(redChest, Items.IRON_INGOT);
            int goldArrived = count(blueChest, Items.GOLD_INGOT);
            if (ironArrived < 1 || goldArrived < 1) {
                throw new GameTestAssertException("Red chest has " + ironArrived + " iron and blue chest has " + goldArrived + " gold");
            }
        });
    }

    /**
     * A diversion transporter in high mode does not deliver while it is unpowered. A basic transporter delivers one
     * item well inside {@link #CLOSED_TICKS}. After that the output side is still closed, a redstone block opens it,
     * and the diamond reaches the chest.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void diversionTransporterFollowsRedstone(GameTestHelper helper) {
        BlockEntity source = chest(helper, new BlockPos(1, 1, 2));
        BlockPos diversionPos = new BlockPos(2, 1, 2);
        helper.setBlock(diversionPos, MekanismBlocks.DIVERSION_TRANSPORTER.getBlock());
        BlockEntity destination = chest(helper, new BlockPos(3, 1, 2));
        TileEntityDiversionTransporter tile = (TileEntityDiversionTransporter) helper.getBlockEntity(diversionPos);
        DiversionTransporter diversion = (DiversionTransporter) tile.getTransmitter();
        setConnection(tile, Direction.WEST, ConnectionType.PULL);
        diversion.updateMode(Direction.EAST, DiversionControl.HIGH);
        give(source, Items.DIAMOND, 1);
        long openedAt = helper.getLevel().getGameTime() + CLOSED_TICKS;
        boolean[] powered = {false};
        helper.succeedWhen(() -> {
            int arrived = count(destination, Items.DIAMOND);
            if (!powered[0]) {
                check(arrived == 0, "The unpowered diversion delivered a diamond");
                if (helper.getLevel().getGameTime() < openedAt) {
                    throw new GameTestAssertException("The diversion transporter is still closed");
                }
                check(count(source, Items.DIAMOND) == 1, "The diamond left the source while the diversion was closed");
                helper.setBlock(new BlockPos(2, 1, 1), Blocks.REDSTONE_BLOCK);
                check(WorldUtils.isGettingPowered(tile.getLevel(), tile.getBlockPos()), "The redstone block did not power the diversion transporter");
                powered[0] = true;
                throw new GameTestAssertException("The diversion transporter is powered; waiting for the diamond");
            }
            if (arrived < 1) {
                throw new GameTestAssertException("The powered diversion has not delivered the diamond; the source holds "
                      + count(source, Items.DIAMOND));
            }
        });
    }

    /**
     * A restrictive transporter costs more than a normal one, so a diamond takes the open path. Once that chest is
     * full, an emerald takes the restrictive path.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 600)
    public static void restrictiveTransporterIsTheLastPath(GameTestHelper helper) {
        BlockEntity source = chest(helper, new BlockPos(1, 1, 1));
        BlockPos pullPos = new BlockPos(2, 1, 1);
        helper.setBlock(pullPos, MekanismBlocks.BASIC_LOGISTICAL_TRANSPORTER.getBlock());
        helper.setBlock(new BlockPos(3, 1, 1), MekanismBlocks.BASIC_LOGISTICAL_TRANSPORTER.getBlock());
        helper.setBlock(new BlockPos(4, 1, 1), MekanismBlocks.BASIC_LOGISTICAL_TRANSPORTER.getBlock());
        helper.setBlock(new BlockPos(3, 1, 2), MekanismBlocks.RESTRICTIVE_TRANSPORTER.getBlock());
        BlockEntity preferred = chest(helper, new BlockPos(5, 1, 1));
        BlockEntity fallback = chest(helper, new BlockPos(3, 1, 3));
        setConnection((TileEntityTransmitter) helper.getBlockEntity(pullPos), Direction.WEST, ConnectionType.PULL);
        give(source, Items.DIAMOND, 1);
        boolean[] filled = {false};
        helper.succeedWhen(() -> {
            if (!filled[0]) {
                check(count(fallback, Items.DIAMOND) == 0, "The diamond took the restrictive path while the normal path was open");
                if (count(preferred, Items.DIAMOND) < 1) {
                    throw new GameTestAssertException("Waiting for the diamond to take the open path");
                }
                fillRemaining(preferred);
                give(source, Items.EMERALD, 1);
                filled[0] = true;
                throw new GameTestAssertException("The open chest is full; waiting for the emerald");
            }
            check(count(preferred, Items.EMERALD) == 0, "The full chest accepted the emerald");
            if (count(fallback, Items.EMERALD) < 1) {
                throw new GameTestAssertException("Waiting for the emerald to take the restrictive path");
            }
        });
    }

    /**
     * A mechanical pipe pulls water from one fluid tank and pushes it into another.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void pipesMoveFluidBetweenTanks(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(1, 1, 1);
        BlockPos pipePos = new BlockPos(2, 1, 1);
        BlockPos destinationPos = new BlockPos(3, 1, 1);
        helper.setBlock(sourcePos, MekanismBlocks.BASIC_FLUID_TANK.getBlock());
        helper.setBlock(pipePos, MekanismBlocks.BASIC_MECHANICAL_PIPE.getBlock());
        helper.setBlock(destinationPos, MekanismBlocks.BASIC_FLUID_TANK.getBlock());
        TileEntityMechanicalPipe pipe = (TileEntityMechanicalPipe) helper.getBlockEntity(pipePos);
        setConnection(pipe, Direction.WEST, ConnectionType.PULL);
        setConnection(pipe, Direction.EAST, ConnectionType.PUSH);
        BlockEntity source = helper.getBlockEntity(sourcePos);
        BlockEntity destination = helper.getBlockEntity(destinationPos);
        long filled = TestPlatform.insertBlockFluid(source, Direction.UP, Fluids.WATER, FLUID, true);
        check(filled == FLUID, "The source tank accepted " + filled + " mB of " + FLUID);
        helper.succeedWhen(() -> {
            long arrived = TestPlatform.blockFluid(destination, Direction.UP);
            if (arrived < FLUID) {
                throw new GameTestAssertException("The destination tank holds " + arrived + " mB; the source holds "
                      + TestPlatform.blockFluid(source, Direction.UP));
            }
        });
    }

    /**
     * A pressurized tube pulls hydrogen from one chemical tank and pushes it into another. Eject is off, so the tank
     * does not push the gas itself.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void tubesMoveGasBetweenTanks(GameTestHelper helper) {
        BlockPos sourcePos = new BlockPos(1, 1, 1);
        BlockPos tubePos = new BlockPos(2, 1, 1);
        BlockPos destinationPos = new BlockPos(3, 1, 1);
        helper.setBlock(sourcePos, facing(MekanismBlocks.BASIC_CHEMICAL_TANK.getBlock().defaultBlockState(), Direction.EAST));
        helper.setBlock(tubePos, MekanismBlocks.BASIC_PRESSURIZED_TUBE.getBlock());
        helper.setBlock(destinationPos, facing(MekanismBlocks.BASIC_CHEMICAL_TANK.getBlock().defaultBlockState(), Direction.NORTH));
        stopEjecting((TileEntityChemicalTank) helper.getBlockEntity(sourcePos));
        stopEjecting((TileEntityChemicalTank) helper.getBlockEntity(destinationPos));
        TileEntityPressurizedTube tube = (TileEntityPressurizedTube) helper.getBlockEntity(tubePos);
        setConnection(tube, Direction.WEST, ConnectionType.PULL);
        setConnection(tube, Direction.EAST, ConnectionType.PUSH);
        BlockEntity source = helper.getBlockEntity(sourcePos);
        BlockEntity destination = helper.getBlockEntity(destinationPos);
        long filled = TestPlatform.insertBlockGas(source, Direction.WEST, MekanismGases.HYDROGEN.get(), GAS, true);
        check(filled == GAS, "The source tank accepted " + filled + " of " + GAS + " hydrogen");
        helper.succeedWhen(() -> {
            long arrived = TestPlatform.blockGas(destination, Direction.WEST);
            if (arrived < GAS) {
                throw new GameTestAssertException("The destination tank holds " + arrived + " hydrogen; the source holds "
                      + TestPlatform.blockGas(source, Direction.EAST));
            }
        });
    }

    private static BlockState facing(BlockState state, Direction direction) {
        return state.setValue(BlockStateProperties.HORIZONTAL_FACING, direction);
    }

    private static BlockEntity chest(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, Blocks.CHEST);
        return helper.getBlockEntity(pos);
    }

    private static void give(BlockEntity chest, Item item, int amount) {
        long moved = TestPlatform.insertItems(chest, Direction.UP, new ItemStack(item, amount));
        check(moved == amount, "Inserted " + moved + " of " + amount + " " + item);
    }

    private static int count(BlockEntity chest, Item item) {
        return TestPlatform.countItems(chest, Direction.UP, item);
    }

    private static void fillRemaining(BlockEntity chest) {
        for (int attempt = 0; attempt < 30; attempt++) {
            int moved = TestPlatform.insertThroughItemCapability(chest, Direction.UP, new ItemStack(Items.COBBLESTONE, 64));
            if (moved == 0) {
                break;
            }
        }
        check(TestPlatform.insertThroughItemCapability(chest, Direction.UP, new ItemStack(Items.EMERALD)) == 0,
              "The open chest still had room");
    }

    private static void setConnection(TileEntityTransmitter tile, Direction side, ConnectionType type) {
        tile.getTransmitter().setConnectionTypeRaw(side, type);
        tile.getTransmitter().onModeChange(side);
        tile.getTransmitter().refreshConnections();
        tile.getTransmitter().notifyTileChange();
    }

    private static void setColor(TileEntityLogisticalTransporter tile, EnumColor color) {
        tile.getTransmitter().setColor(color);
        tile.getTransmitter().refreshConnections();
        tile.getTransmitter().notifyTileChange();
    }

    private static void stopEjecting(TileEntityChemicalTank tank) {
        ConfigInfo config = tank.getConfig().getConfig(TransmissionType.GAS);
        check(config != null, "The chemical tank has no gas configuration");
        config.setEjecting(false);
    }
}

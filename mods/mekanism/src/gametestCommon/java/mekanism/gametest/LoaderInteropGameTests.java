/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import static mekanism.gametest.CoreMachineGameTests.TEMPLATE;
import static mekanism.gametest.CoreMachineGameTests.check;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.math.FloatingLong;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.registries.MekanismGases;
import mekanism.common.registries.MekanismItems;
import mekanism.common.tile.TileEntityChemicalTank;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.tile.machine.TileEntityElectrolyticSeparator;
import mekanism.common.util.StorageUtils;
import mekanism.common.util.UnitDisplayUtils.EnergyUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;

/**
 * Mekanism's machines and other blocks' storages meet through the loader's public APIs: Forge Energy, capabilities and
 * the chemical capabilities on Forge; Team Reborn Energy, the Transfer API and the chemical lookups on Fabric.
 */
public class LoaderInteropGameTests {

    @GameTest(template = TEMPLATE)
    public static void machinesTakeEnergyThroughTheLoaderApi(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, MekanismBlocks.ENRICHMENT_CHAMBER.getBlock());
        BlockEntity chamber = helper.getBlockEntity(pos);
        long accepted = 0;
        for (Direction side : Direction.values()) {
            accepted += TestPlatform.insertEnergy(chamber, side, 1_000);
        }
        check(accepted > 0, "The enrichment chamber took no energy through the loader's energy API");
        check(!((IStrictEnergyHandler) chamber).getEnergy(0).isZero(), "The energy the loader's API reported moved is not stored");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void machinesTakeItemsThroughTheLoaderApi(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, MekanismBlocks.CRUSHER.getBlock());
        TileEntityMekanism crusher = (TileEntityMekanism) helper.getBlockEntity(pos);
        long accepted = 0;
        for (Direction side : Direction.values()) {
            accepted += TestPlatform.insertItems(crusher, side, new ItemStack(Items.IRON_INGOT, 4));
        }
        IInventorySlot input = crusher.getInventorySlots(null).get(0);
        check(accepted > 0, "The crusher took no items through the loader's item API");
        check(input.getStack().is(Items.IRON_INGOT) && input.getCount() == accepted,
              "The crusher's input holds " + input.getStack() + " after the loader's API moved " + accepted + " iron ingots");
        helper.succeed();
    }

    /**
     * A water bucket empties into the separator through the item's fluid handler, Forge's bucket wrapper on Forge and
     * the Transfer API's bucket storage on Fabric, and the powered separator splits the water.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void separatorDrainsBucketsAndSplitsWater(GameTestHelper helper) {
        TileEntityMekanism separator = CoreMachineGameTests.poweredMachine(helper, MekanismBlocks.ELECTROLYTIC_SEPARATOR);
        IInventorySlot fluidSlot = separator.getInventorySlots(null).get(0);
        check(fluidSlot.isItemValid(new ItemStack(Items.WATER_BUCKET)), "The separator's fluid slot rejects water buckets");
        fluidSlot.setStack(new ItemStack(Items.WATER_BUCKET));
        helper.succeedWhen(() -> {
            check(fluidSlot.getStack().is(Items.BUCKET), "The water bucket was not emptied: " + fluidSlot.getStack());
            check(!((TileEntityElectrolyticSeparator) separator).leftTank.isEmpty(), "The separator made no hydrogen");
        });
    }

    /**
     * Mekanism reaches other blocks' inventories through Forge's item handler capability; a chest has one on both loaders.
     */
    @GameTest(template = TEMPLATE)
    public static void foreignInventoriesAnswerTheItemCapability(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, Blocks.CHEST);
        BlockEntity chest = helper.getBlockEntity(pos);
        int moved = TestPlatform.insertThroughItemCapability(chest, Direction.UP, new ItemStack(Items.IRON_INGOT, 5));
        check(moved == 5, "Inserted " + moved + " of 5 iron ingots through the chest's item capability");
        check(((Container) chest).countItem(Items.IRON_INGOT) == 5, "The chest does not hold the inserted iron ingots");
        helper.succeed();
    }

    /**
     * Another mod charges and discharges Mekanism's energy items through the loader's item energy API. The energy tablet
     * and the energy cube item accept extraction. The configurator is included because upstream gives it an energy
     * capability that external automation may fill but not empty. An uncommitted interaction leaves the stack unchanged.
     */
    @GameTest(template = TEMPLATE)
    public static void energyItemsMoveEnergyThroughTheLoaderApi(GameTestHelper helper) {
        chargeEnergyItem(new ItemStack(MekanismItems.ENERGY_TABLET.get()), true);
        chargeEnergyItem(new ItemStack(MekanismBlocks.BASIC_ENERGY_CUBE.asItem()), true);
        chargeEnergyItem(new ItemStack(MekanismItems.CONFIGURATOR.get()), false);
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        check(TestPlatform.itemEnergy(iron) == 0, "An iron ingot exposed an energy storage");
        check(TestPlatform.insertItemEnergy(iron, 1, true) == 0, "An iron ingot accepted energy");
        helper.succeed();
    }

    /**
     * Another mod fills and drains a fluid tank item through the loader's item fluid API. One millibucket is
     * {@link TestPlatform#DROPLETS_PER_MB} droplets on the Transfer API, and Forge's fluid capability is unchanged.
     */
    @GameTest(template = TEMPLATE)
    public static void fluidItemsMoveFluidThroughTheLoaderApi(GameTestHelper helper) {
        ItemStack tank = new ItemStack(MekanismBlocks.BASIC_FLUID_TANK.asItem());
        long capacity = TestPlatform.itemFluidCapacity(tank);
        check(capacity > 1, "The fluid tank item has no fluid capacity");
        long offered = TestPlatform.insertItemFluid(tank, Fluids.WATER, capacity, false);
        check(offered > 0 && offered < capacity, "Filling the tank's whole capacity offered " + offered + " of " + capacity);
        check(StorageUtils.getStoredFluidFromNBT(tank).isEmpty(), "An uncommitted fill changed the fluid tank");
        long filled = TestPlatform.insertItemFluid(tank, Fluids.WATER, 1, true);
        check(filled == 1, "Filling one millibucket moved " + filled);
        var stored = StorageUtils.getStoredFluidFromNBT(tank);
        check(!stored.isEmpty() && stored.getFluid() == Fluids.WATER && stored.getAmount() == 1,
              "The tank holds " + stored.getAmount() + " of " + stored.getFluid());
        check(TestPlatform.itemFluidDroplets(tank) == TestPlatform.DROPLETS_PER_MB,
              "The loader reports " + TestPlatform.itemFluidDroplets(tank) + " droplets for one millibucket");
        long drained = TestPlatform.extractItemFluid(tank, Fluids.WATER, 1, true);
        check(drained == 1, "Draining one millibucket moved " + drained);
        check(StorageUtils.getStoredFluidFromNBT(tank).isEmpty(), "The tank still holds fluid after it was drained");
        check(TestPlatform.itemFluidDroplets(tank) == 0, "The loader still reports fluid");
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        check(TestPlatform.insertItemFluid(iron, Fluids.WATER, 1, true) == 0, "An iron ingot accepted fluid");
        helper.succeed();
    }

    /**
     * Another mod drains a chemical tank through the loader's public gas API. The tank's front is its output; the other
     * sides accept gas and do not give it back. A simulated drain leaves the stored gas unchanged, and a chest has no
     * gas handler.
     */
    @GameTest(template = TEMPLATE)
    public static void foreignLookupDrainsGasFromAChemicalTank(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, MekanismBlocks.BASIC_CHEMICAL_TANK.getBlock());
        TileEntityChemicalTank tank = (TileEntityChemicalTank) helper.getBlockEntity(pos);
        long stored = 1_000;
        long drain = 400;
        GasStack remainder = tank.getGasTank().insert(new GasStack(MekanismGases.HYDROGEN, stored), Action.EXECUTE, AutomationType.MANUAL);
        check(remainder.isEmpty(), "The chemical tank refused hydrogen: " + remainder);
        Direction front = tank.getDirection();
        GasStack simulated = TestPlatform.extractGas(tank, front, drain, false);
        check(simulated.getAmount() == drain && simulated.getType() == MekanismGases.HYDROGEN.get(),
              "A simulated drain returned " + simulated.getAmount() + " of " + simulated.getType());
        check(tank.getGasTank().getStored() == stored, "A simulated drain left " + tank.getGasTank().getStored() + " hydrogen");
        GasStack drained = TestPlatform.extractGas(tank, front, drain, true);
        check(drained.getAmount() == drain && drained.getType() == MekanismGases.HYDROGEN.get(),
              "The public gas API drained " + drained.getAmount() + " of " + drained.getType());
        check(tank.getGasTank().getStored() == stored - drain, "The tank holds " + tank.getGasTank().getStored() + " after the drain");
        GasStack refused = TestPlatform.extractGas(tank, front.getClockWise(), drain, true);
        check(refused.isEmpty(), "An input side gave gas back: " + refused.getAmount());
        check(tank.getGasTank().getStored() == stored - drain, "The input side left " + tank.getGasTank().getStored() + " hydrogen");
        BlockPos chestPos = new BlockPos(3, 1, 1);
        helper.setBlock(chestPos, Blocks.CHEST);
        BlockEntity chest = helper.getBlockEntity(chestPos);
        check(TestPlatform.gasTanks(chest, Direction.UP) == 0, "A chest exposed a gas handler");
        check(TestPlatform.extractGas(chest, Direction.UP, drain, true).isEmpty(), "A chest gave gas back");
        helper.succeed();
    }

    /**
     * The same public API exposes the tank's other chemicals, a heater's heat, and a chemical tank item's gas. An
     * uncommitted item fill leaves the stack unchanged. An iron ingot has none of it.
     */
    @GameTest(template = TEMPLATE)
    public static void publicLookupsExposeChemicalsHeatAndGasItems(GameTestHelper helper) {
        BlockPos tankPos = new BlockPos(1, 1, 1);
        helper.setBlock(tankPos, MekanismBlocks.BASIC_CHEMICAL_TANK.getBlock());
        BlockEntity tank = helper.getBlockEntity(tankPos);
        Direction front = ((TileEntityChemicalTank) tank).getDirection();
        check(TestPlatform.infusionTanks(tank, front) == 1, "The chemical tank exposed " + TestPlatform.infusionTanks(tank, front) + " infusion tanks");
        check(TestPlatform.pigmentTanks(tank, front) == 1, "The chemical tank exposed " + TestPlatform.pigmentTanks(tank, front) + " pigment tanks");
        check(TestPlatform.slurryTanks(tank, front) == 1, "The chemical tank exposed " + TestPlatform.slurryTanks(tank, front) + " slurry tanks");
        check(TestPlatform.heatCapacitors(tank, front) == 0, "The chemical tank exposed a heat handler");
        BlockPos heaterPos = new BlockPos(3, 1, 1);
        helper.setBlock(heaterPos, MekanismBlocks.RESISTIVE_HEATER.getBlock());
        check(TestPlatform.heatCapacitors(helper.getBlockEntity(heaterPos), Direction.NORTH) > 0, "The resistive heater exposed no heat handler");

        ItemStack tankItem = new ItemStack(MekanismBlocks.BASIC_CHEMICAL_TANK.asItem());
        check(TestPlatform.itemGasTanks(tankItem) == 1, "The chemical tank item exposed " + TestPlatform.itemGasTanks(tankItem) + " gas tanks");
        check(TestPlatform.itemInfusionTanks(tankItem) == 1, "The chemical tank item has no infusion handler");
        check(TestPlatform.itemPigmentTanks(tankItem) == 1, "The chemical tank item has no pigment handler");
        check(TestPlatform.itemSlurryTanks(tankItem) == 1, "The chemical tank item has no slurry handler");
        long offered = TestPlatform.insertItemGas(tankItem, MekanismGases.HYDROGEN.get(), 500, false);
        check(offered == 500, "Filling the item offered " + offered + " of 500 hydrogen");
        check(TestPlatform.itemGas(tankItem) == 0, "An uncommitted fill stored " + TestPlatform.itemGas(tankItem) + " hydrogen");
        long filled = TestPlatform.insertItemGas(tankItem, MekanismGases.HYDROGEN.get(), 500, true);
        check(filled == 500, "Filling the item moved " + filled + " hydrogen");
        check(TestPlatform.itemGas(tankItem) == 500, "The item reports " + TestPlatform.itemGas(tankItem) + " hydrogen");
        long drained = TestPlatform.extractItemGas(tankItem, 200, true);
        check(drained == 200, "Draining the item moved " + drained + " hydrogen");
        check(TestPlatform.itemGas(tankItem) == 300, "The item reports " + TestPlatform.itemGas(tankItem) + " hydrogen after the drain");
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        check(TestPlatform.itemGasTanks(iron) == 0, "An iron ingot exposed a gas handler");
        check(TestPlatform.insertItemGas(iron, MekanismGases.HYDROGEN.get(), 1, true) == 0, "An iron ingot accepted gas");
        helper.succeed();
    }

    private static void chargeEnergyItem(ItemStack stack, boolean externalExtract) {
        long capacity = TestPlatform.itemEnergyCapacity(stack);
        check(capacity > 1, stack.getItem() + " has no energy capacity");
        long offered = TestPlatform.insertItemEnergy(stack, capacity, false);
        check(offered > 0 && offered < capacity, stack.getItem() + " accepted " + offered + " of " + capacity + " in one interaction");
        check(StorageUtils.getStoredEnergyFromNBT(stack).isZero(), "An uncommitted charge changed " + stack.getItem());
        long inserted = TestPlatform.insertItemEnergy(stack, 1, true);
        check(inserted == 1, stack.getItem() + " accepted " + inserted + " FE instead of 1");
        FloatingLong joules = StorageUtils.getStoredEnergyFromNBT(stack);
        check(joules.equals(EnergyUnit.FORGE_ENERGY.convertFrom(FloatingLong.createConst(inserted))),
              stack.getItem() + " stored " + joules + " joules for " + inserted + " FE");
        check(TestPlatform.itemEnergy(stack) == inserted, stack.getItem() + " reports " + TestPlatform.itemEnergy(stack) + " stored");
        long extracted = TestPlatform.extractItemEnergy(stack, inserted, true);
        if (externalExtract) {
            check(extracted == inserted, stack.getItem() + " returned " + extracted + " of " + inserted + " FE");
            check(StorageUtils.getStoredEnergyFromNBT(stack).isZero(), stack.getItem() + " still holds energy");
            check(TestPlatform.itemEnergy(stack) == 0, stack.getItem() + " still reports energy");
        } else {
            check(extracted == 0, stack.getItem() + " let external automation extract " + extracted + " FE");
            check(joules.equals(StorageUtils.getStoredEnergyFromNBT(stack)), stack.getItem() + " lost energy it should have kept");
        }
    }
}

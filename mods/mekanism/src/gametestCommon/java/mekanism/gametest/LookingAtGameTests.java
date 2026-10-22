/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import static mekanism.gametest.CoreMachineGameTests.TEMPLATE;
import static mekanism.gametest.CoreMachineGameTests.check;

import mekanism.api.Action;
import mekanism.api.NBTConstants;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.api.math.FloatingLong;
import mekanism.common.content.tank.TankMultiblockData;
import mekanism.common.integration.lookingat.HwylaLookingAtHelper;
import mekanism.common.integration.lookingat.LookingAtUtils;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.registries.MekanismGases;
import mekanism.common.tile.TileEntityChemicalTank;
import mekanism.common.tile.machine.TileEntityElectricPump;
import mekanism.common.tile.multiblock.TileEntityDynamicTank;
import mekanism.gametest.MultiblockStructures.Shell;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

/**
 * Jade and WTHIT write this NBT from {@link LookingAtUtils#addInfo}. The One Probe reads the same tanks through its
 * own element helper. None of those mods are installed for this test.
 */
public class LookingAtGameTests {

    private static final long GAS = 750;
    private static final int FLUID = 1_500;
    private static final long TANK_FLUID = 2_500;

    @GameTest(template = TEMPLATE)
    public static void providerDataIncludesEnergyFluidAndGas(GameTestHelper helper) {
        BlockPos pumpPos = new BlockPos(1, 1, 1);
        helper.setBlock(pumpPos, MekanismBlocks.ELECTRIC_PUMP.getBlock());
        TileEntityElectricPump pump = (TileEntityElectricPump) helper.getBlockEntity(pumpPos);
        FloatingLong leftover = ((IStrictEnergyHandler) pump).insertEnergy(FloatingLong.createConst(20_000), Action.EXECUTE);
        FloatingLong stored = ((IStrictEnergyHandler) pump).getEnergy(0);
        FloatingLong capacity = ((IStrictEnergyHandler) pump).getMaxEnergy(0);
        check(leftover.smallerThan(FloatingLong.createConst(20_000)) && stored.greaterThan(FloatingLong.ZERO), "The pump stored no energy");
        pump.fluidTank.setStack(new FluidStack(Fluids.WATER, FLUID));

        BlockPos tankPos = new BlockPos(3, 1, 1);
        helper.setBlock(tankPos, MekanismBlocks.BASIC_CHEMICAL_TANK.getBlock());
        TileEntityChemicalTank chemicalTank = (TileEntityChemicalTank) helper.getBlockEntity(tankPos);
        chemicalTank.getChemicalTank().getGasTank().setStack(new GasStack(MekanismGases.HYDROGEN.get(), GAS));

        CompoundTag pumpData = providerData(pump);
        CompoundTag energy = element(pumpData, NBTConstants.ENERGY_STORED);
        check(FloatingLong.parseFloatingLong(energy.getString(NBTConstants.ENERGY_STORED), true).equals(stored),
              "Energy element stored " + energy.getString(NBTConstants.ENERGY_STORED));
        check(FloatingLong.parseFloatingLong(energy.getString(NBTConstants.MAX), true).equals(capacity),
              "Energy element capacity " + energy.getString(NBTConstants.MAX));
        CompoundTag fluid = element(pumpData, NBTConstants.FLUID_STORED);
        FluidStack reported = FluidStack.loadFluidStackFromNBT(fluid.getCompound(NBTConstants.FLUID_STORED));
        check(reported.getFluid() == Fluids.WATER && reported.getAmount() == FLUID, "Fluid element is " + reported);
        check(fluid.getInt(NBTConstants.MAX) == pump.fluidTank.getCapacity(), "Fluid capacity is " + fluid.getInt(NBTConstants.MAX));

        CompoundTag gasElement = element(providerData(chemicalTank), HwylaLookingAtHelper.CHEMICAL_STACK);
        CompoundTag gas = gasElement.getCompound(HwylaLookingAtHelper.CHEMICAL_STACK);
        check("mekanism:hydrogen".equals(gas.getString(NBTConstants.GAS_NAME)), "Gas element is " + gas);
        check(gas.getLong(NBTConstants.AMOUNT) == GAS, "Gas amount is " + gas.getLong(NBTConstants.AMOUNT));
        check(gasElement.getLong(NBTConstants.MAX) == chemicalTank.getChemicalTank().getGasTank().getCapacity(),
              "Gas capacity is " + gasElement.getLong(NBTConstants.MAX));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void providerDataReadsFormedTankFromTheShell(GameTestHelper helper) {
        Shell shell = MultiblockStructures.dynamicTank(helper, new BlockPos(1, 1, 1), 3);
        BlockPos loneGlass = new BlockPos(6, 1, 1);
        helper.setBlock(loneGlass, MekanismBlocks.STRUCTURAL_GLASS.getBlock());
        helper.succeedWhen(() -> {
            TileEntityDynamicTank valve = valve(helper, shell);
            TankMultiblockData tank = valve.getMultiblock();
            check(tank.isFormed(), "The dynamic tank is not formed");
            long filled = TestPlatform.insertBlockFluid(valve, shell.valveSide(), Fluids.WATER, TANK_FLUID, true);
            check(filled == TANK_FLUID, "The valve accepted " + filled + " of " + TANK_FLUID);
            BlockPos glass = null;
            for (BlockPos pos : shell.blocks()) {
                if (helper.getBlockState(pos).is(MekanismBlocks.STRUCTURAL_GLASS.getBlock())) {
                    glass = pos;
                    break;
                }
            }
            check(glass != null, "The shell has no structural glass");
            CompoundTag reported = element(providerData(helper.getBlockEntity(glass)), NBTConstants.FLUID_STORED);
            FluidStack fluid = FluidStack.loadFluidStackFromNBT(reported.getCompound(NBTConstants.FLUID_STORED));
            check(fluid.getFluid() == Fluids.WATER && fluid.getAmount() == TANK_FLUID,
                  "Structural glass reports " + fluid + " for a formed tank holding " + TANK_FLUID);
            check(reported.getInt(NBTConstants.MAX) == tank.getTankCapacity(), "Shell fluid capacity is " + reported.getInt(NBTConstants.MAX));
            CompoundTag absent = providerData(helper.getBlockEntity(loneGlass));
            check(!absent.contains(NBTConstants.MEK_DATA), "Unformed structural glass wrote " + absent);
        });
    }

    private static TileEntityDynamicTank valve(GameTestHelper helper, Shell shell) {
        check(shell.valve() != null, "The shell has no valve");
        BlockEntity blockEntity = helper.getBlockEntity(shell.valve());
        check(blockEntity instanceof TileEntityDynamicTank, "The valve is " + blockEntity);
        return (TileEntityDynamicTank) blockEntity;
    }

    private static CompoundTag providerData(BlockEntity tile) {
        HwylaLookingAtHelper looking = new HwylaLookingAtHelper();
        LookingAtUtils.addInfo(looking, tile, true, true);
        CompoundTag data = new CompoundTag();
        looking.finalizeData(data);
        return data;
    }

    private static CompoundTag element(CompoundTag data, String key) {
        check(data.contains(NBTConstants.MEK_DATA, Tag.TAG_LIST), "Provider wrote no Mekanism data");
        ListTag list = data.getList(NBTConstants.MEK_DATA, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag element = list.getCompound(i);
            if (element.contains(key)) {
                return element;
            }
        }
        check(false, "No element with " + key + " in " + list);
        return new CompoundTag();
    }
}

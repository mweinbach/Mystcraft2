/*
 * Mekanism 1.19.2 port - in-world tests, Forge side.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import java.util.Optional;
import mekanism.api.Action;
import mekanism.api.chemical.gas.Gas;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.gas.IGasHandler;
import mekanism.api.heat.IHeatHandler;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.tile.TileEntityFluidTank;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.items.ItemHandlerHelper;

/**
 * What the shared tests do through the loader's APIs: Forge Energy, Forge's item and fluid capabilities, and
 * Mekanism's chemical and heat capabilities. {@link #DROPLETS_PER_MB} is the Transfer API ratio the shared fluid
 * assertion uses; Forge itself counts millibuckets. Chemicals are Mekanism units.
 */
public final class TestPlatform {

    public static final long DROPLETS_PER_MB = 81;

    private TestPlatform() {
    }

    public static long insertEnergy(BlockEntity blockEntity, Direction side, long amount) {
        return blockEntity.getCapability(ForgeCapabilities.ENERGY, side).map(storage -> storage.receiveEnergy((int) amount, false)).orElse(0);
    }

    public static long insertItems(BlockEntity blockEntity, Direction side, ItemStack stack) {
        return insertThroughItemCapability(blockEntity, side, stack);
    }

    public static int insertThroughItemCapability(BlockEntity blockEntity, Direction side, ItemStack stack) {
        return blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side)
              .map(handler -> stack.getCount() - ItemHandlerHelper.insertItem(handler, stack.copy(), false).getCount())
              .orElse(0);
    }

    public static long insertItemEnergy(ItemStack stack, long amount, boolean commit) {
        if (amount <= 0) {
            return 0;
        }
        return stack.getCapability(ForgeCapabilities.ENERGY)
              .map(storage -> (long) storage.receiveEnergy((int) Math.min(amount, Integer.MAX_VALUE), !commit))
              .orElse(0L);
    }

    public static long extractItemEnergy(ItemStack stack, long amount, boolean commit) {
        if (amount <= 0) {
            return 0;
        }
        return stack.getCapability(ForgeCapabilities.ENERGY)
              .map(storage -> (long) storage.extractEnergy((int) Math.min(amount, Integer.MAX_VALUE), !commit))
              .orElse(0L);
    }

    public static long itemEnergy(ItemStack stack) {
        return stack.getCapability(ForgeCapabilities.ENERGY).map(storage -> (long) storage.getEnergyStored()).orElse(0L);
    }

    public static long itemEnergyCapacity(ItemStack stack) {
        return stack.getCapability(ForgeCapabilities.ENERGY).map(storage -> (long) storage.getMaxEnergyStored()).orElse(0L);
    }

    public static long insertItemFluid(ItemStack stack, Fluid fluid, long milliBuckets, boolean commit) {
        return fluidHandler(stack).map(handler -> {
            int moved = handler.fill(new FluidStack(fluid, (int) Math.min(milliBuckets, Integer.MAX_VALUE)), action(commit));
            keepStack(stack, handler);
            return (long) moved;
        }).orElse(0L);
    }

    public static long extractItemFluid(ItemStack stack, Fluid fluid, long milliBuckets, boolean commit) {
        return fluidHandler(stack).map(handler -> {
            FluidStack drained = handler.drain(new FluidStack(fluid, (int) Math.min(milliBuckets, Integer.MAX_VALUE)), action(commit));
            keepStack(stack, handler);
            return drained.isEmpty() ? 0L : drained.getAmount();
        }).orElse(0L);
    }

    public static long itemFluidDroplets(ItemStack stack) {
        return fluidStored(stack) * DROPLETS_PER_MB;
    }

    public static long itemFluidCapacity(ItemStack stack) {
        return fluidHandler(stack).map(handler -> {
            long total = 0;
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                total += handler.getTankCapacity(tank);
            }
            return total;
        }).orElse(0L);
    }

    private static Optional<IFluidHandlerItem> fluidHandler(ItemStack stack) {
        return stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).resolve();
    }

    private static long fluidStored(ItemStack stack) {
        return fluidHandler(stack).map(handler -> {
            long total = 0;
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                total += handler.getFluidInTank(tank).getAmount();
            }
            return total;
        }).orElse(0L);
    }

    private static IFluidHandler.FluidAction action(boolean commit) {
        return commit ? IFluidHandler.FluidAction.EXECUTE : IFluidHandler.FluidAction.SIMULATE;
    }

    public static GasStack extractGas(BlockEntity blockEntity, Direction side, long amount, boolean execute) {
        return blockEntity.getCapability(Capabilities.GAS_HANDLER, side)
              .map(handler -> handler.extractChemical(amount, chemicalAction(execute)))
              .orElse(GasStack.EMPTY);
    }

    public static int gasTanks(BlockEntity blockEntity, Direction side) {
        return blockEntity.getCapability(Capabilities.GAS_HANDLER, side).map(IGasHandler::getTanks).orElse(0);
    }

    public static int infusionTanks(BlockEntity blockEntity, Direction side) {
        return blockEntity.getCapability(Capabilities.INFUSION_HANDLER, side).map(handler -> handler.getTanks()).orElse(0);
    }

    public static int pigmentTanks(BlockEntity blockEntity, Direction side) {
        return blockEntity.getCapability(Capabilities.PIGMENT_HANDLER, side).map(handler -> handler.getTanks()).orElse(0);
    }

    public static int slurryTanks(BlockEntity blockEntity, Direction side) {
        return blockEntity.getCapability(Capabilities.SLURRY_HANDLER, side).map(handler -> handler.getTanks()).orElse(0);
    }

    public static int heatCapacitors(BlockEntity blockEntity, Direction side) {
        return blockEntity.getCapability(Capabilities.HEAT_HANDLER, side).map(IHeatHandler::getHeatCapacitorCount).orElse(0);
    }

    public static int itemGasTanks(ItemStack stack) {
        return stack.getCapability(Capabilities.GAS_HANDLER).map(IGasHandler::getTanks).orElse(0);
    }

    public static int itemInfusionTanks(ItemStack stack) {
        return stack.getCapability(Capabilities.INFUSION_HANDLER).map(handler -> handler.getTanks()).orElse(0);
    }

    public static int itemPigmentTanks(ItemStack stack) {
        return stack.getCapability(Capabilities.PIGMENT_HANDLER).map(handler -> handler.getTanks()).orElse(0);
    }

    public static int itemSlurryTanks(ItemStack stack) {
        return stack.getCapability(Capabilities.SLURRY_HANDLER).map(handler -> handler.getTanks()).orElse(0);
    }

    public static long insertItemGas(ItemStack stack, Gas gas, long amount, boolean execute) {
        if (amount <= 0) {
            return 0;
        }
        return stack.getCapability(Capabilities.GAS_HANDLER).map(handler -> {
            GasStack remainder = handler.insertChemical(new GasStack(gas, amount), chemicalAction(execute));
            return amount - remainder.getAmount();
        }).orElse(0L);
    }

    public static long extractItemGas(ItemStack stack, long amount, boolean execute) {
        if (amount <= 0) {
            return 0;
        }
        return stack.getCapability(Capabilities.GAS_HANDLER)
              .map(handler -> handler.extractChemical(amount, chemicalAction(execute)).getAmount())
              .orElse(0L);
    }

    public static long itemGas(ItemStack stack) {
        return stack.getCapability(Capabilities.GAS_HANDLER).map(handler -> {
            long total = 0;
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                total += handler.getChemicalInTank(tank).getAmount();
            }
            return total;
        }).orElse(0L);
    }

    private static Action chemicalAction(boolean execute) {
        return execute ? Action.EXECUTE : Action.SIMULATE;
    }

    public static void fillBlockFluid(BlockEntity blockEntity, Fluid fluid, int milliBuckets) {
        if (blockEntity instanceof TileEntityFluidTank tank) {
            tank.fluidTank.setStack(new FluidStack(fluid, milliBuckets));
        }
    }

    private static void keepStack(ItemStack stack, IFluidHandlerItem handler) {
        ItemStack container = handler.getContainer();
        if (container != stack && container.is(stack.getItem())) {
            stack.setTag(container.getTag());
            stack.setCount(container.getCount());
        }
    }
}

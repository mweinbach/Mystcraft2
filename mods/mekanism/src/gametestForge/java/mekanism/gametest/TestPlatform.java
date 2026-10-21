/*
 * Mekanism 1.19.2 port - in-world tests, Forge side.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import java.util.Optional;
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
 * What the shared tests do through the loader's APIs: Forge Energy and Forge's item handler capability.
 * {@link #DROPLETS_PER_MB} is the Transfer API ratio the shared fluid assertion uses; Forge itself counts millibuckets.
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

    private static void keepStack(ItemStack stack, IFluidHandlerItem handler) {
        ItemStack container = handler.getContainer();
        if (container != stack && container.is(stack.getItem())) {
            stack.setTag(container.getTag());
            stack.setCount(container.getCount());
        }
    }
}

/*
 * Mekanism 1.19.2 port - in-world tests, Fabric side.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import java.util.function.Function;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.base.SingleStackStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import ports.mekanism.lib.common.capabilities.ForgeCapabilities;
import ports.mekanism.lib.items.ItemHandlerHelper;
import team.reborn.energy.api.EnergyStorage;

/**
 * What the shared tests do through the loader's APIs: Team Reborn Energy and the Transfer API.
 * Item energy is 1 E per FE. Item fluid is {@link #DROPLETS_PER_MB} droplets per millibucket.
 */
public final class TestPlatform {

    public static final long DROPLETS_PER_MB = FluidConstants.BUCKET / 1000;

    private TestPlatform() {
    }

    public static long insertEnergy(BlockEntity blockEntity, Direction side, long amount) {
        EnergyStorage storage = EnergyStorage.SIDED.find(blockEntity.getLevel(), blockEntity.getBlockPos(), side);
        if (storage == null) {
            return 0;
        }
        try (Transaction transaction = Transaction.openOuter()) {
            long inserted = storage.insert(amount, transaction);
            transaction.commit();
            return inserted;
        }
    }

    public static long insertItems(BlockEntity blockEntity, Direction side, ItemStack stack) {
        Storage<ItemVariant> storage = ItemStorage.SIDED.find(blockEntity.getLevel(), blockEntity.getBlockPos(), side);
        if (storage == null) {
            return 0;
        }
        try (Transaction transaction = Transaction.openOuter()) {
            long inserted = storage.insert(ItemVariant.of(stack), stack.getCount(), transaction);
            transaction.commit();
            return inserted;
        }
    }

    public static int insertThroughItemCapability(BlockEntity blockEntity, Direction side, ItemStack stack) {
        return blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side)
              .map(handler -> stack.getCount() - ItemHandlerHelper.insertItem(handler, stack.copy(), false).getCount())
              .orElse(0);
    }

    public static long insertItemEnergy(ItemStack stack, long amount, boolean commit) {
        return inSlot(stack, context -> {
            EnergyStorage storage = context.find(EnergyStorage.ITEM);
            if (storage == null || amount <= 0) {
                return 0L;
            }
            try (Transaction transaction = Transaction.openOuter()) {
                long moved = storage.insert(amount, transaction);
                if (commit) {
                    transaction.commit();
                }
                return moved;
            }
        });
    }

    public static long extractItemEnergy(ItemStack stack, long amount, boolean commit) {
        return inSlot(stack, context -> {
            EnergyStorage storage = context.find(EnergyStorage.ITEM);
            if (storage == null || amount <= 0) {
                return 0L;
            }
            try (Transaction transaction = Transaction.openOuter()) {
                long moved = storage.extract(amount, transaction);
                if (commit) {
                    transaction.commit();
                }
                return moved;
            }
        });
    }

    public static long itemEnergy(ItemStack stack) {
        return inSlot(stack, context -> {
            EnergyStorage storage = context.find(EnergyStorage.ITEM);
            return storage == null ? 0L : storage.getAmount();
        });
    }

    public static long itemEnergyCapacity(ItemStack stack) {
        return inSlot(stack, context -> {
            EnergyStorage storage = context.find(EnergyStorage.ITEM);
            return storage == null ? 0L : storage.getCapacity();
        });
    }

    public static long insertItemFluid(ItemStack stack, Fluid fluid, long milliBuckets, boolean commit) {
        return moveFluid(stack, FluidVariant.of(fluid), milliBuckets, commit, true);
    }

    public static long extractItemFluid(ItemStack stack, Fluid fluid, long milliBuckets, boolean commit) {
        return moveFluid(stack, FluidVariant.of(fluid), milliBuckets, commit, false);
    }

    public static long itemFluidDroplets(ItemStack stack) {
        return inSlot(stack, context -> {
            Storage<FluidVariant> storage = context.find(FluidStorage.ITEM);
            if (storage == null) {
                return 0L;
            }
            long total = 0;
            for (StorageView<FluidVariant> view : storage) {
                total += view.getAmount();
            }
            return total;
        });
    }

    public static long itemFluidCapacity(ItemStack stack) {
        return inSlot(stack, context -> {
            Storage<FluidVariant> storage = context.find(FluidStorage.ITEM);
            if (storage == null) {
                return 0L;
            }
            long total = 0;
            for (StorageView<FluidVariant> view : storage) {
                total += view.getCapacity() / DROPLETS_PER_MB;
            }
            return total;
        });
    }

    private static long moveFluid(ItemStack stack, FluidVariant variant, long milliBuckets, boolean commit, boolean insert) {
        if (milliBuckets <= 0) {
            return 0;
        }
        return inSlot(stack, context -> {
            Storage<FluidVariant> storage = context.find(FluidStorage.ITEM);
            if (storage == null) {
                return 0L;
            }
            try (Transaction transaction = Transaction.openOuter()) {
                long moved = insert
                      ? storage.insert(variant, milliBuckets * DROPLETS_PER_MB, transaction)
                      : storage.extract(variant, milliBuckets * DROPLETS_PER_MB, transaction);
                if (commit) {
                    transaction.commit();
                }
                return moved / DROPLETS_PER_MB;
            }
        });
    }

    private static long inSlot(ItemStack stack, Function<ContainerItemContext, Long> action) {
        return action.apply(ContainerItemContext.ofSingleSlot(new StackSlot(stack)));
    }

    /**
     * A lone stack as the slot another mod would pass to {@link ContainerItemContext}. A committed edit of the same item
     * is copied back onto the stack the test is holding.
     */
    private static final class StackSlot extends SingleStackStorage {

        private final ItemStack original;
        private ItemStack stack;

        private StackSlot(ItemStack stack) {
            this.original = stack;
            this.stack = stack;
        }

        @Override
        protected ItemStack getStack() {
            return stack;
        }

        @Override
        protected void setStack(ItemStack stack) {
            this.stack = stack;
        }

        @Override
        protected void onFinalCommit() {
            super.onFinalCommit();
            if (stack != original && stack.is(original.getItem())) {
                original.setTag(stack.getTag());
                original.setCount(stack.getCount());
                stack = original;
            }
        }
    }
}

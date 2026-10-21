/*
 * Mekanism 1.19.2 port - in-world tests, Fabric side.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import java.util.function.Function;
import mekanism.api.Action;
import mekanism.api.chemical.gas.Gas;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.gas.IGasHandler;
import mekanism.api.chemical.infuse.IInfusionHandler;
import mekanism.api.chemical.pigment.IPigmentHandler;
import mekanism.api.chemical.slurry.ISlurryHandler;
import mekanism.api.heat.IHeatHandler;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
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
import mekanism.common.tile.TileEntityFluidTank;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import ports.mekanism.fabric.api.MekanismChemicalLookups;
import ports.mekanism.lib.common.capabilities.ForgeCapabilities;
import ports.mekanism.lib.common.extensions.IForgeBlockEntity;
import ports.mekanism.lib.fluids.FluidStack;
import ports.mekanism.lib.items.ItemHandlerHelper;
import team.reborn.energy.api.EnergyStorage;

/**
 * What the shared tests do through the loader's APIs: Team Reborn Energy, the Transfer API and
 * {@link MekanismChemicalLookups}. Item energy is 1 E per FE. Item fluid is {@link #DROPLETS_PER_MB} droplets per
 * millibucket. Chemicals are Mekanism units.
 */
public final class TestPlatform {

    public static final long DROPLETS_PER_MB = FluidConstants.BUCKET / 1000;

    private TestPlatform() {
    }

    public static AABB renderBounds(BlockEntity blockEntity) {
        return ((IForgeBlockEntity) blockEntity).getRenderBoundingBox();
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

    public static GasStack extractGas(BlockEntity blockEntity, Direction side, long amount, boolean execute) {
        IGasHandler handler = find(MekanismChemicalLookups.GAS, blockEntity, side);
        return handler == null ? GasStack.EMPTY : handler.extractChemical(amount, chemicalAction(execute));
    }

    public static int gasTanks(BlockEntity blockEntity, Direction side) {
        IGasHandler handler = find(MekanismChemicalLookups.GAS, blockEntity, side);
        return handler == null ? 0 : handler.getTanks();
    }

    public static int infusionTanks(BlockEntity blockEntity, Direction side) {
        IInfusionHandler handler = find(MekanismChemicalLookups.INFUSION, blockEntity, side);
        return handler == null ? 0 : handler.getTanks();
    }

    public static int pigmentTanks(BlockEntity blockEntity, Direction side) {
        IPigmentHandler handler = find(MekanismChemicalLookups.PIGMENT, blockEntity, side);
        return handler == null ? 0 : handler.getTanks();
    }

    public static int slurryTanks(BlockEntity blockEntity, Direction side) {
        ISlurryHandler handler = find(MekanismChemicalLookups.SLURRY, blockEntity, side);
        return handler == null ? 0 : handler.getTanks();
    }

    public static int heatCapacitors(BlockEntity blockEntity, Direction side) {
        IHeatHandler handler = find(MekanismChemicalLookups.HEAT, blockEntity, side);
        return handler == null ? 0 : handler.getHeatCapacitorCount();
    }

    public static int itemGasTanks(ItemStack stack) {
        IGasHandler handler = MekanismChemicalLookups.GAS_ITEM.find(stack, null);
        return handler == null ? 0 : handler.getTanks();
    }

    public static int itemInfusionTanks(ItemStack stack) {
        IInfusionHandler handler = MekanismChemicalLookups.INFUSION_ITEM.find(stack, null);
        return handler == null ? 0 : handler.getTanks();
    }

    public static int itemPigmentTanks(ItemStack stack) {
        IPigmentHandler handler = MekanismChemicalLookups.PIGMENT_ITEM.find(stack, null);
        return handler == null ? 0 : handler.getTanks();
    }

    public static int itemSlurryTanks(ItemStack stack) {
        ISlurryHandler handler = MekanismChemicalLookups.SLURRY_ITEM.find(stack, null);
        return handler == null ? 0 : handler.getTanks();
    }

    public static long insertItemGas(ItemStack stack, Gas gas, long amount, boolean execute) {
        if (amount <= 0) {
            return 0;
        }
        IGasHandler handler = MekanismChemicalLookups.GAS_ITEM.find(stack, null);
        if (handler == null) {
            return 0;
        }
        return amount - handler.insertChemical(new GasStack(gas, amount), chemicalAction(execute)).getAmount();
    }

    public static long extractItemGas(ItemStack stack, long amount, boolean execute) {
        if (amount <= 0) {
            return 0;
        }
        IGasHandler handler = MekanismChemicalLookups.GAS_ITEM.find(stack, null);
        return handler == null ? 0 : handler.extractChemical(amount, chemicalAction(execute)).getAmount();
    }

    public static long itemGas(ItemStack stack) {
        IGasHandler handler = MekanismChemicalLookups.GAS_ITEM.find(stack, null);
        if (handler == null) {
            return 0;
        }
        long total = 0;
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            total += handler.getChemicalInTank(tank).getAmount();
        }
        return total;
    }

    private static Action chemicalAction(boolean execute) {
        return execute ? Action.EXECUTE : Action.SIMULATE;
    }

    private static <A, C> A find(BlockApiLookup<A, C> lookup, BlockEntity blockEntity, C context) {
        if (blockEntity.getLevel() == null) {
            return null;
        }
        return lookup.find(blockEntity.getLevel(), blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity, context);
    }

    public static void fillBlockFluid(BlockEntity blockEntity, Fluid fluid, int milliBuckets) {
        if (blockEntity instanceof TileEntityFluidTank tank) {
            tank.fluidTank.setStack(new FluidStack(fluid, milliBuckets));
        }
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

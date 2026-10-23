/*
 * Extreme Reactors 1.19.2 port - game tests
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.gametest;

import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.base.SingleStackStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import ports.extremereactors.corelib.compat.mekanism.MekanismGas;
import ports.extremereactors.platform.Platform;
import team.reborn.energy.api.EnergyStorage;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * {@link LoaderApis} through the Fabric Transfer API and Team Reborn Energy.
 */
final class FabricLoaderApis implements LoaderApis {

    static final LoaderApis INSTANCE = new FabricLoaderApis();

    private static final long DROPLETS_PER_MB = FluidConstants.BUCKET / 1000;

    @Override
    public long insertItems(final ServerLevel level, final BlockPos position, final Direction side, final ItemStack stack) {

        final Storage<ItemVariant> storage = found(ItemStorage.SIDED.find(level, position, side), "item storage", position);

        try (Transaction transaction = Transaction.openOuter()) {

            final long inserted = storage.insert(ItemVariant.of(stack), stack.getCount(), transaction);

            transaction.commit();
            return inserted;
        }
    }

    @Override
    public long insertFluid(final ServerLevel level, final BlockPos position, final Direction side, final Fluid fluid,
                            final int millibuckets) {

        final Storage<FluidVariant> storage = found(FluidStorage.SIDED.find(level, position, side), "fluid storage", position);

        try (Transaction transaction = Transaction.openOuter()) {

            final long inserted = storage.insert(FluidVariant.of(fluid), millibuckets * DROPLETS_PER_MB, transaction);

            transaction.commit();
            return inserted / DROPLETS_PER_MB;
        }
    }

    @Override
    public long extractEnergy(final ServerLevel level, final BlockPos position, final Direction side, final long maxAmount) {

        final EnergyStorage storage = found(EnergyStorage.SIDED.find(level, position, side), "energy storage", position);

        try (Transaction transaction = Transaction.openOuter()) {

            final long extracted = storage.extract(maxAmount, transaction);

            transaction.commit();
            return extracted;
        }
    }

    @Override
    public String energyUnit() {
        return "E";
    }

    @Override
    public ItemFluidTransfer drainItem(final ItemStack stack, final int millibuckets) {

        final StackSlot slot = new StackSlot(stack.copy());
        final Storage<FluidVariant> storage = itemStorage(slot);
        final FluidVariant stored = StorageUtil.findStoredResource(storage);

        if (null == stored || stored.isBlank()) {
            return new ItemFluidTransfer(null, 0, stack.copy());
        }

        try (Transaction transaction = Transaction.openOuter()) {

            final long extracted = storage.extract(stored, millibuckets * DROPLETS_PER_MB, transaction);

            transaction.commit();
            return new ItemFluidTransfer(0 == extracted ? null : stored.getFluid(), extracted / DROPLETS_PER_MB,
                    slot.getStack());
        }
    }

    @Override
    public ItemFluidTransfer fillItem(final ItemStack stack, final Fluid fluid, final int millibuckets) {

        final StackSlot slot = new StackSlot(stack.copy());
        final Storage<FluidVariant> storage = itemStorage(slot);

        try (Transaction transaction = Transaction.openOuter()) {

            final long inserted = storage.insert(FluidVariant.of(fluid), millibuckets * DROPLETS_PER_MB, transaction);

            transaction.commit();
            return new ItemFluidTransfer(0 == inserted ? null : fluid, inserted / DROPLETS_PER_MB, slot.getStack());
        }
    }

    @Override
    public Optional<GasTank> mekanismGas(final ServerLevel level, final BlockPos position, @Nullable final Direction side) {
        return Platform.isModLoaded(MekanismGas.MOD_ID) ? FabricMekanismGas.find(level, position, side) : Optional.empty();
    }

    @Override
    public long extractMekanismGas(final ServerLevel level, final BlockPos position, final Direction side,
                                   final long maxAmount) {
        return Platform.isModLoaded(MekanismGas.MOD_ID)
                ? FabricMekanismGas.extract(level, position, side, maxAmount) : 0;
    }

    /** The item fluid storage of the slot's stack, seen as another mod's tank or pipe sees an item in its slot. */
    private static Storage<FluidVariant> itemStorage(final StackSlot slot) {

        final ItemStack stack = slot.getStack();
        final Storage<FluidVariant> storage = FluidStorage.ITEM.find(stack, ContainerItemContext.ofSingleSlot(slot));

        if (null == storage) {
            throw new GameTestAssertException("no FluidStorage.ITEM on " + Registry.ITEM.getKey(stack.getItem()));
        }

        return storage;
    }

    private static <T> T found(final T lookupResult, final String what, final BlockPos position) {

        if (null == lookupResult) {
            throw new GameTestAssertException("no " + what + " at " + position);
        }

        return lookupResult;
    }

    /** A one-stack slot, so the storage can swap a full bucket for an empty one and back. */
    private static final class StackSlot extends SingleStackStorage {

        private ItemStack _stack;

        StackSlot(final ItemStack stack) {
            this._stack = stack;
        }

        @Override
        protected ItemStack getStack() {
            return this._stack;
        }

        @Override
        protected void setStack(final ItemStack stack) {
            this._stack = stack;
        }
    }

    private FabricLoaderApis() {
    }
}

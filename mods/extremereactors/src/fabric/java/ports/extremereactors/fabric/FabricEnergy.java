/*
 * Extreme Reactors 1.19.2 port - Fabric adapter
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.fabric;

import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.base.SingleStackStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import ports.extremereactors.corelib.capability.CapabilityHelper;
import ports.extremereactors.corelib.capability.ICapabilityProvider;
import ports.extremereactors.corelib.capability.LazyOptional;
import ports.extremereactors.corelib.energy.IEnergyStorage;
import ports.extremereactors.corelib.item.inventory.handler.IItemHandlerModifiable;
import team.reborn.energy.api.EnergyStorage;

import javax.annotation.Nullable;

/**
 * Bridges the corelib Forge Energy style storages with Team Reborn Energy, at 1 E per FE.
 */
final class FabricEnergy {

    static void registerBlockEntityLookups(final BlockEntityType<?>... types) {
        EnergyStorage.SIDED.registerForBlockEntities(FabricEnergy::energyStorageOf, types);
    }

    static LazyOptional<IEnergyStorage> foreignEnergy(final Level level, final BlockPos position, final BlockState state,
                                                      @Nullable final BlockEntity blockEntity, final Direction side) {

        final EnergyStorage storage = EnergyStorage.SIDED.find(level, position, state, blockEntity, side);

        return null == storage ? LazyOptional.empty() : LazyOptional.of(() -> new RebornEnergyHandler(storage));
    }

    /**
     * An energy view of the item in a slot of one of our inventories, or null if that item holds no energy.
     */
    @Nullable
    static IEnergyStorage itemEnergy(final IItemHandlerModifiable inventory, final int slot) {

        if (inventory.getStackInSlot(slot).isEmpty()) {
            return null;
        }

        final EnergyStorage storage = ContainerItemContext.ofSingleSlot(new InventorySlot(inventory, slot)).find(EnergyStorage.ITEM);

        return null == storage ? null : new RebornEnergyHandler(storage);
    }

    @Nullable
    private static EnergyStorage energyStorageOf(final BlockEntity blockEntity, @Nullable final Direction side) {
        return blockEntity instanceof ICapabilityProvider provider ?
                provider.getCapability(CapabilityHelper.ENERGY, side).map(EnergyHandlerStorage::new).orElse(null) : null;
    }

    /**
     * One of our energy storages as a Reborn Energy storage. Like the item and fluid storages, what a transaction
     * moved is applied when the outermost transaction commits.
     */
    private static final class EnergyHandlerStorage
            extends SnapshotParticipant<Long>
            implements EnergyStorage {

        EnergyHandlerStorage(final IEnergyStorage handler) {

            this._handler = handler;
            this._pending = 0;
        }

        @Override
        public boolean supportsInsertion() {
            return this._handler.canReceive();
        }

        @Override
        public long insert(final long maxAmount, final TransactionContext transaction) {

            if (this._pending < 0 || maxAmount <= 0 || !this._handler.canReceive()) {
                return 0;
            }

            final long accepted = Math.min(maxAmount, this._handler.receiveEnergy(
                    FabricTransfer.clampToInt(this._pending + maxAmount), true) - this._pending);

            if (accepted <= 0) {
                return 0;
            }

            this.updateSnapshots(transaction);
            this._pending += accepted;
            return accepted;
        }

        @Override
        public boolean supportsExtraction() {
            return this._handler.canExtract();
        }

        @Override
        public long extract(final long maxAmount, final TransactionContext transaction) {

            if (this._pending > 0 || maxAmount <= 0 || !this._handler.canExtract()) {
                return 0;
            }

            final long already = -this._pending;
            final long accepted = Math.min(maxAmount, this._handler.extractEnergy(
                    FabricTransfer.clampToInt(already + maxAmount), true) - already);

            if (accepted <= 0) {
                return 0;
            }

            this.updateSnapshots(transaction);
            this._pending -= accepted;
            return accepted;
        }

        @Override
        public long getAmount() {
            return Math.max(0, this._handler.getEnergyStored() + this._pending);
        }

        @Override
        public long getCapacity() {
            return this._handler.getMaxEnergyStored();
        }

        @Override
        protected Long createSnapshot() {
            return this._pending;
        }

        @Override
        protected void readSnapshot(final Long snapshot) {
            this._pending = snapshot;
        }

        @Override
        protected void onFinalCommit() {

            final long pending = this._pending;

            this._pending = 0;

            if (pending > 0) {
                this._handler.receiveEnergy(FabricTransfer.clampToInt(pending), false);
            } else if (pending < 0) {
                this._handler.extractEnergy(FabricTransfer.clampToInt(-pending), false);
            }
        }

        private final IEnergyStorage _handler;
        private long _pending;
    }

    private record RebornEnergyHandler(EnergyStorage storage) implements IEnergyStorage {

        @Override
        public int receiveEnergy(final int maxReceive, final boolean simulate) {
            return maxReceive <= 0 ? 0 : (int) FabricTransfer.transact(simulate, transaction -> this.storage.insert(maxReceive, transaction));
        }

        @Override
        public int extractEnergy(final int maxExtract, final boolean simulate) {
            return maxExtract <= 0 ? 0 : (int) FabricTransfer.transact(simulate, transaction -> this.storage.extract(maxExtract, transaction));
        }

        @Override
        public int getEnergyStored() {
            return FabricTransfer.clampToInt(this.storage.getAmount());
        }

        @Override
        public int getMaxEnergyStored() {
            return FabricTransfer.clampToInt(this.storage.getCapacity());
        }

        @Override
        public boolean canExtract() {
            return this.storage.supportsExtraction();
        }

        @Override
        public boolean canReceive() {
            return this.storage.supportsInsertion();
        }
    }

    /**
     * A slot of one of our inventories, so the energy API of the item in it can replace the stack.
     */
    private static final class InventorySlot extends SingleStackStorage {

        InventorySlot(final IItemHandlerModifiable inventory, final int slot) {

            this._inventory = inventory;
            this._slot = slot;
        }

        @Override
        protected ItemStack getStack() {
            return this._inventory.getStackInSlot(this._slot);
        }

        @Override
        protected void setStack(final ItemStack stack) {
            this._inventory.setStackInSlot(this._slot, stack);
        }

        @Override
        protected int getCapacity(final ItemVariant itemVariant) {
            return Math.min(this._inventory.getSlotLimit(this._slot), itemVariant.getItem().getMaxStackSize());
        }

        private final IItemHandlerModifiable _inventory;
        private final int _slot;
    }

    private FabricEnergy() {
    }
}

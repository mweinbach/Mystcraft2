/*
 * Mekanism 1.19.2 port - Fabric loader adapter.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric;

import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import ports.mekanism.lib.common.capabilities.ForgeCapabilities;
import ports.mekanism.lib.common.util.LazyOptional;
import ports.mekanism.lib.energy.IEnergyStorage;
import team.reborn.energy.api.EnergyStorage;

/**
 * Bridges Forge Energy with Team Reborn Energy at 1 E per FE, so Mekanism's configured Joule to FE rate applies to both.
 */
final class FabricEnergy {

    private FabricEnergy() {
    }

    static void registerBlockEntityLookups(BlockEntityType<?>... types) {
        EnergyStorage.SIDED.registerForBlockEntities((blockEntity, side) -> blockEntity.getCapability(ForgeCapabilities.ENERGY, side)
              .<EnergyStorage>map(HandlerEnergyStorage::new).orElse(null), types);
    }

    static LazyOptional<IEnergyStorage> foreignEnergy(BlockEntity blockEntity, Direction side) {
        EnergyStorage storage = EnergyStorage.SIDED.find(blockEntity.getLevel(), blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity, side);
        return storage == null ? LazyOptional.empty() : LazyOptional.of(() -> new StorageEnergyHandler(storage));
    }

    static LazyOptional<IEnergyStorage> foreignEnergy(ContainerItemContext context) {
        EnergyStorage storage = context.find(EnergyStorage.ITEM);
        return storage == null ? LazyOptional.empty() : LazyOptional.of(() -> new StorageEnergyHandler(storage));
    }

    /**
     * A Forge Energy storage as a Team Reborn Energy storage. What a transaction moved is applied when the outermost
     * transaction commits.
     */
    private static final class HandlerEnergyStorage extends SnapshotParticipant<Long> implements EnergyStorage {

        private final IEnergyStorage handler;
        private long pending;

        HandlerEnergyStorage(IEnergyStorage handler) {
            this.handler = handler;
        }

        @Override
        public boolean supportsInsertion() {
            return handler.canReceive();
        }

        @Override
        public long insert(long maxAmount, TransactionContext transaction) {
            if (pending < 0 || maxAmount <= 0 || !handler.canReceive()) {
                return 0;
            }
            long accepted = Math.min(maxAmount, handler.receiveEnergy(FabricTransfer.clampToInt(pending + maxAmount), true) - pending);
            if (accepted <= 0) {
                return 0;
            }
            updateSnapshots(transaction);
            pending += accepted;
            return accepted;
        }

        @Override
        public boolean supportsExtraction() {
            return handler.canExtract();
        }

        @Override
        public long extract(long maxAmount, TransactionContext transaction) {
            if (pending > 0 || maxAmount <= 0 || !handler.canExtract()) {
                return 0;
            }
            long already = -pending;
            long accepted = Math.min(maxAmount, handler.extractEnergy(FabricTransfer.clampToInt(already + maxAmount), true) - already);
            if (accepted <= 0) {
                return 0;
            }
            updateSnapshots(transaction);
            pending -= accepted;
            return accepted;
        }

        @Override
        public long getAmount() {
            return Math.max(0, handler.getEnergyStored() + pending);
        }

        @Override
        public long getCapacity() {
            return handler.getMaxEnergyStored();
        }

        @Override
        protected Long createSnapshot() {
            return pending;
        }

        @Override
        protected void readSnapshot(Long snapshot) {
            pending = snapshot;
        }

        @Override
        protected void onFinalCommit() {
            long committed = pending;
            pending = 0;
            if (committed > 0) {
                handler.receiveEnergy(FabricTransfer.clampToInt(committed), false);
            } else if (committed < 0) {
                handler.extractEnergy(FabricTransfer.clampToInt(-committed), false);
            }
        }
    }

    private record StorageEnergyHandler(EnergyStorage storage) implements IEnergyStorage {

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return maxReceive <= 0 ? 0 : (int) FabricTransfer.transact(simulate, transaction -> storage.insert(maxReceive, transaction));
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return maxExtract <= 0 ? 0 : (int) FabricTransfer.transact(simulate, transaction -> storage.extract(maxExtract, transaction));
        }

        @Override
        public int getEnergyStored() {
            return FabricTransfer.clampToInt(storage.getAmount());
        }

        @Override
        public int getMaxEnergyStored() {
            return FabricTransfer.clampToInt(storage.getCapacity());
        }

        @Override
        public boolean canExtract() {
            return storage.supportsExtraction();
        }

        @Override
        public boolean canReceive() {
            return storage.supportsInsertion();
        }
    }
}

package com.technovision.advancedgenetics.fabric;

import com.technovision.advancedgenetics.api.blockentity.AbstractInventoryBlockEntity;
import com.technovision.advancedgenetics.api.blockentity.AbstractProcessingBlockEntity;
import com.technovision.advancedgenetics.api.blockentity.MachineEnergy;
import com.technovision.advancedgenetics.registry.BlockEntityRegistry;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.world.level.block.entity.BlockEntityType;
import team.reborn.energy.api.EnergyStorage;

import java.util.function.Supplier;

/**
 * Exposes the machines through Team Reborn Energy and the Fabric Transfer API, as upstream's
 * Fabric build did. Unsided item access sees the whole inventory; sided access follows the
 * machine's face rules, matching the Forge item handlers.
 */
@SuppressWarnings("UnstableApiUsage")
final class FabricTransfer {

    private FabricTransfer() { }

    static void register() {
        BlockEntityType<?>[] machines = BlockEntityRegistry.MACHINES.stream().map(Supplier::get).toArray(BlockEntityType<?>[]::new);
        EnergyStorage.SIDED.registerForBlockEntities((blockEntity, side) -> blockEntity instanceof AbstractProcessingBlockEntity machine
                ? machine.getEnergyStorage().view(EnergyView::new) : null, machines);
        ItemStorage.SIDED.registerForBlockEntities((blockEntity, side) -> blockEntity instanceof AbstractInventoryBlockEntity inventory
                ? InventoryStorage.of(inventory, side) : null, machines);
    }

    /** Upstream's {@code SimpleEnergyStorage(capacity, capacity, capacity)}, over the shared buffer. */
    private static final class EnergyView extends SnapshotParticipant<Integer> implements EnergyStorage {

        private final MachineEnergy energy;

        EnergyView(MachineEnergy energy) {
            this.energy = energy;
        }

        @Override
        protected Integer createSnapshot() {
            return energy.getEnergyStored();
        }

        @Override
        protected void readSnapshot(Integer snapshot) {
            energy.restore(snapshot);
        }

        @Override
        protected void onFinalCommit() {
            energy.markChanged();
        }

        @Override
        public boolean supportsInsertion() {
            return energy.getMaxEnergyStored() > 0;
        }

        @Override
        public long insert(long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notNegative(maxAmount);
            int inserted = (int) Math.min(maxAmount, energy.getMaxEnergyStored() - energy.getEnergyStored());
            if (inserted <= 0) return 0;
            updateSnapshots(transaction);
            energy.restore(energy.getEnergyStored() + inserted);
            return inserted;
        }

        @Override
        public boolean supportsExtraction() {
            return energy.getMaxEnergyStored() > 0;
        }

        @Override
        public long extract(long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notNegative(maxAmount);
            int extracted = (int) Math.min(maxAmount, energy.getEnergyStored());
            if (extracted <= 0) return 0;
            updateSnapshots(transaction);
            energy.restore(energy.getEnergyStored() - extracted);
            return extracted;
        }

        @Override
        public long getAmount() {
            return energy.getEnergyStored();
        }

        @Override
        public long getCapacity() {
            return energy.getMaxEnergyStored();
        }
    }
}

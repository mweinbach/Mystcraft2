package ports.gridwright.fabric;

import ports.gridwright.machine.EnergyBuffer;
import ports.gridwright.machine.EnergyHolder;
import ports.gridwright.registry.ModBlockEntities;
import net.minecraft.world.WorldlyContainer;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import team.reborn.energy.api.EnergyStorage;

/**
 * Exposes the machines through the Fabric Transfer API (following each machine's face rules, like the
 * Forge item handlers) and energy stores through Team Reborn Energy, in the directions each store
 * allows: generators give, the ender quarry takes.
 */
@SuppressWarnings("UnstableApiUsage")
final class FabricTransfer {

    private FabricTransfer() { }

    static void register() {
        EnergyStorage.SIDED.registerForBlockEntities((blockEntity, side) -> blockEntity instanceof EnergyHolder holder
                ? new EnergyView(holder.energy()) : null, ModBlockEntities.GENERATOR.get(), ModBlockEntities.ENDER_QUARRY.get());
        ItemStorage.SIDED.registerForBlockEntities((blockEntity, side) -> blockEntity instanceof WorldlyContainer container
                ? InventoryStorage.of(container, side) : null, ModBlockEntities.GENERATOR.get(), ModBlockEntities.RESONATOR.get(),
                ModBlockEntities.QUANTUM_QUARRY.get(), ModBlockEntities.ENDER_QUARRY.get(), ModBlockEntities.MECHANICAL_MINER.get(),
                ModBlockEntities.MECHANICAL_USER.get());
    }

    /** Team Reborn Energy view of a store; other mods may move energy only in the directions it allows. */
    private static final class EnergyView extends SnapshotParticipant<Long> implements EnergyStorage {

        private final EnergyBuffer energy;

        EnergyView(EnergyBuffer energy) {
            this.energy = energy;
        }

        @Override
        protected Long createSnapshot() {
            return energy.stored();
        }

        @Override
        protected void readSnapshot(Long snapshot) {
            energy.restore(snapshot);
        }

        @Override
        protected void onFinalCommit() {
            energy.markChanged();
        }

        @Override
        public boolean supportsInsertion() {
            return energy.maxReceive() > 0;
        }

        @Override
        public long insert(long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notNegative(maxAmount);
            long inserted = energy.receive(maxAmount, true);
            if (inserted <= 0) return 0;
            updateSnapshots(transaction);
            energy.restore(energy.stored() + inserted);
            return inserted;
        }

        @Override
        public boolean supportsExtraction() {
            return energy.maxExtract() > 0;
        }

        @Override
        public long extract(long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notNegative(maxAmount);
            long extracted = energy.extract(maxAmount, true);
            if (extracted <= 0) return 0;
            updateSnapshots(transaction);
            energy.restore(energy.stored() - extracted);
            return extracted;
        }

        @Override
        public long getAmount() {
            return energy.stored();
        }

        @Override
        public long getCapacity() {
            return energy.capacity();
        }
    }
}

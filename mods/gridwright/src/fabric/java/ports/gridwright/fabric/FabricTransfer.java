package ports.gridwright.fabric;

import ports.gridwright.machine.EnergyBuffer;
import ports.gridwright.machine.MachineBlockEntity;
import ports.gridwright.registry.ModBlockEntities;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import team.reborn.energy.api.EnergyStorage;

/**
 * Exposes the machines through the Fabric Transfer API (following each machine's face rules, like the
 * Forge item handlers) and the generators through Team Reborn Energy, extraction only.
 */
@SuppressWarnings("UnstableApiUsage")
final class FabricTransfer {

    private FabricTransfer() { }

    static void register() {
        EnergyStorage.SIDED.registerForBlockEntity((generator, side) -> new EnergyView(generator.energy()),
                ModBlockEntities.GENERATOR.get());
        ItemStorage.SIDED.registerForBlockEntities((blockEntity, side) -> blockEntity instanceof MachineBlockEntity machine
                ? InventoryStorage.of(machine, side) : null, ModBlockEntities.GENERATOR.get(), ModBlockEntities.RESONATOR.get());
    }

    /** Team Reborn Energy view of a generator's store; other mods may extract, never insert. */
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
            return false;
        }

        @Override
        public long insert(long maxAmount, TransactionContext transaction) {
            return 0;
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

package ports.gridwright.fabric;

import ports.gridwright.item.AngelRingFlight;
import ports.gridwright.registry.ModUtilities;
import ports.gridwright.storage.DrumBlockEntity;
import ports.gridwright.storage.FluidTank;
import ports.gridwright.storage.TrashCanBlockEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.base.InsertionOnlyStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import team.reborn.energy.api.EnergyStorage;

/**
 * Fabric side of the phase 2b utilities: the Angel Ring's per-tick flight check, a fluid storage on
 * drums and void storages on trash cans (items, fluids and Team Reborn Energy). Sound muffling is
 * done by {@code ports.gridwright.fabric.mixin.ServerLevelMixin}, since Fabric has no sound event.
 */
@SuppressWarnings("UnstableApiUsage")
final class FabricUtilityEvents {

    private FabricUtilityEvents() { }

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(AngelRingFlight::tick);
        ServerWorldEvents.UNLOAD.register((server, level) -> FabricFakePlayer.unload(level));
        FluidStorage.SIDED.registerForBlockEntity((drum, side) -> new TankStorage(drum.tank()), ModUtilities.DRUM.get());
        ItemStorage.SIDED.registerForBlockEntity((can, side) -> switch (can.kind()) {
            case ITEM -> new VoidStorage<ItemVariant>(can);
            default -> null;
        }, ModUtilities.TRASH_CAN.get());
        FluidStorage.SIDED.registerForBlockEntity((can, side) -> switch (can.kind()) {
            case FLUID -> new VoidStorage<FluidVariant>(can);
            default -> null;
        }, ModUtilities.TRASH_CAN.get());
        EnergyStorage.SIDED.registerForBlockEntity((can, side) -> switch (can.kind()) {
            case ENERGY -> new VoidEnergy(can);
            default -> null;
        }, ModUtilities.TRASH_CAN.get());
    }

    /** A drum's tank as one fluid slot; changes are rolled back with the transaction, notified on commit. */
    private static final class TankStorage extends SnapshotParticipant<FluidTank.Snapshot> implements SingleSlotStorage<FluidVariant> {

        private final FluidTank tank;

        TankStorage(FluidTank tank) {
            this.tank = tank;
        }

        @Override
        public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            long accepted = tank.insert(resource.getFluid(), resource.getNbt(), maxAmount, true);
            if (accepted <= 0) return 0;
            updateSnapshots(transaction);
            tank.insert(resource.getFluid(), resource.getNbt(), accepted, false);
            return accepted;
        }

        @Override
        public long extract(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            if (!tank.holds(resource.getFluid(), resource.getNbt())) return 0;
            long removed = tank.extract(maxAmount, true);
            if (removed <= 0) return 0;
            updateSnapshots(transaction);
            tank.extract(removed, false);
            return removed;
        }

        @Override
        public boolean isResourceBlank() {
            return tank.isEmpty();
        }

        @Override
        public FluidVariant getResource() {
            return tank.isEmpty() ? FluidVariant.blank() : FluidVariant.of(tank.fluid(), tank.tag());
        }

        @Override
        public long getAmount() {
            return tank.amount();
        }

        @Override
        public long getCapacity() {
            return tank.capacity();
        }

        @Override
        protected FluidTank.Snapshot createSnapshot() {
            return tank.snapshot();
        }

        @Override
        protected void readSnapshot(FluidTank.Snapshot snapshot) {
            tank.restore(snapshot);
        }

        @Override
        protected void onFinalCommit() {
            tank.changed();
        }
    }

    /** Accepts any amount of anything and destroys it at commit. */
    private static final class VoidStorage<T> extends SnapshotParticipant<Long> implements InsertionOnlyStorage<T> {

        private final TrashCanBlockEntity can;
        private long pending;

        VoidStorage(TrashCanBlockEntity can) {
            this.can = can;
        }

        @Override
        public long insert(T resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notNegative(maxAmount);
            if (maxAmount == 0) return 0;
            updateSnapshots(transaction);
            pending += maxAmount;
            return maxAmount;
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
            can.destroy(pending);
            pending = 0;
        }
    }

    /** Accepts any amount of Team Reborn Energy and destroys it at commit. */
    private static final class VoidEnergy extends SnapshotParticipant<Long> implements EnergyStorage {

        private final TrashCanBlockEntity can;
        private long pending;

        VoidEnergy(TrashCanBlockEntity can) {
            this.can = can;
        }

        @Override
        public long insert(long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notNegative(maxAmount);
            if (maxAmount == 0) return 0;
            updateSnapshots(transaction);
            pending += maxAmount;
            return maxAmount;
        }

        @Override
        public boolean supportsExtraction() {
            return false;
        }

        @Override
        public long extract(long maxAmount, TransactionContext transaction) {
            return 0;
        }

        @Override
        public long getAmount() {
            return 0;
        }

        @Override
        public long getCapacity() {
            return Long.MAX_VALUE;
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
            can.destroy(pending);
            pending = 0;
        }
    }
}

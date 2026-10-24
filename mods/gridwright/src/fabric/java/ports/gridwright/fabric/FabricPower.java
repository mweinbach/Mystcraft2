package ports.gridwright.fabric;

import ports.gridwright.machine.GeneratorBlockEntity;
import ports.gridwright.machine.GeneratorKind;
import ports.gridwright.power.ConverterBlockEntity;
import ports.gridwright.registry.ModBlockEntities;
import ports.gridwright.registry.ModPower;
import ports.gridwright.storage.FluidTank;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.base.InsertionOnlyStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import team.reborn.energy.api.EnergyStorage;

/**
 * Fabric side of phase 2d: Team Reborn Energy on the power converter (insert-only or extract-only by
 * mode) and an insert-only lava fluid storage on lava generators.
 */
@SuppressWarnings("UnstableApiUsage")
final class FabricPower {

    private FabricPower() { }

    static void register() {
        EnergyStorage.SIDED.registerForBlockEntity((converter, side) -> new ConverterEnergy(converter),
                ModPower.POWER_CONVERTER_ENTITY.get());
        FluidStorage.SIDED.registerForBlockEntity((generator, side) -> generator.kind() == GeneratorKind.LAVA
                ? new LavaInput(generator) : null, ModBlockEntities.GENERATOR.get());
    }

    /** The converter's store; changes roll back with the transaction and are saved on commit. */
    private static final class ConverterEnergy extends SnapshotParticipant<Long> implements EnergyStorage {

        private final ConverterBlockEntity converter;

        ConverterEnergy(ConverterBlockEntity converter) {
            this.converter = converter;
        }

        @Override
        public boolean supportsInsertion() {
            return converter.canReceive();
        }

        @Override
        public boolean supportsExtraction() {
            return converter.canExtract();
        }

        @Override
        public long insert(long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notNegative(maxAmount);
            long accepted = converter.receive(maxAmount, true);
            if (accepted <= 0) return 0;
            updateSnapshots(transaction);
            converter.restore(converter.stored() + accepted);
            return accepted;
        }

        @Override
        public long extract(long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notNegative(maxAmount);
            long taken = converter.extract(maxAmount, true);
            if (taken <= 0) return 0;
            updateSnapshots(transaction);
            converter.restore(converter.stored() - taken);
            return taken;
        }

        @Override
        public long getAmount() {
            return converter.stored();
        }

        @Override
        public long getCapacity() {
            return converter.capacity();
        }

        @Override
        protected Long createSnapshot() {
            return converter.stored();
        }

        @Override
        protected void readSnapshot(Long snapshot) {
            converter.restore(snapshot);
        }

        @Override
        protected void onFinalCommit() {
            converter.markChanged();
        }
    }

    /** Pipes may fill a lava generator's tank with lava; nothing may drain it. */
    private static final class LavaInput extends SnapshotParticipant<FluidTank.Snapshot> implements InsertionOnlyStorage<FluidVariant> {

        private final GeneratorBlockEntity generator;

        LavaInput(GeneratorBlockEntity generator) {
            this.generator = generator;
        }

        @Override
        public long insert(FluidVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            long accepted = generator.insertLava(resource.getFluid(), resource.getNbt(), maxAmount, true);
            if (accepted <= 0) return 0;
            updateSnapshots(transaction);
            generator.insertLava(resource.getFluid(), resource.getNbt(), accepted, false);
            return accepted;
        }

        @Override
        protected FluidTank.Snapshot createSnapshot() {
            return generator.lavaTank().snapshot();
        }

        @Override
        protected void readSnapshot(FluidTank.Snapshot snapshot) {
            generator.lavaTank().restore(snapshot);
        }

        @Override
        protected void onFinalCommit() {
            generator.lavaTank().changed();
        }
    }
}

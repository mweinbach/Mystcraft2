/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.base.multiblock.part.io.power;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import ports.extremereactors.corelib.base.multiblock.part.AbstractMultiblockEntity;
import ports.extremereactors.corelib.base.multiblock.part.io.AbstractIOPortHandler;
import ports.extremereactors.corelib.capability.Capability;
import ports.extremereactors.corelib.capability.CapabilityHelper;
import ports.extremereactors.corelib.capability.LazyOptional;
import ports.extremereactors.corelib.data.IoMode;
import ports.extremereactors.corelib.data.WideAmount;
import ports.extremereactors.corelib.data.stack.OperationMode;
import ports.extremereactors.corelib.energy.EnergySystem;
import ports.extremereactors.corelib.energy.IEnergyStorage;
import ports.extremereactors.corelib.energy.IWideEnergyStorage2;
import ports.extremereactors.corelib.energy.NullEnergyHandlers;
import ports.extremereactors.corelib.energy.adapter.ForgeEnergyAdapter;
import ports.extremereactors.corelib.energy.handler.WideEnergyStoragePolicyWrapper;
import ports.extremereactors.multiblock.cuboid.AbstractCuboidMultiblockController;

import javax.annotation.Nullable;

/**
 * Active ports push the energy they are given into the neighbour's storage. Passive ports expose an extract-only
 * view of the machine storage on their outward side.
 */
class PowerPortHandler<Controller extends AbstractCuboidMultiblockController<Controller> & IWideEnergyStorage2,
                       T extends AbstractMultiblockEntity<Controller> & IPowerPort>
        extends AbstractIOPortHandler<Controller, T>
        implements IPowerPortHandler {

    PowerPortHandler(final EnergySystem system, final IoMode mode, final T part) {

        super(part, mode);
        this._system = system;
        this._neighbor = LazyOptional.empty();
        this._exposed = null;
    }

    //region IPowerPortHandler

    @Override
    public EnergySystem getEnergySystem() {
        return this._system;
    }

    @Override
    public WideAmount outputEnergy(final WideAmount amount) {

        if (this.isPassive() || amount.isZero()) {
            return WideAmount.ZERO;
        }

        final IEnergyStorage destination = this._neighbor.orElse(null);

        if (null == destination || !destination.canReceive()) {
            return WideAmount.ZERO;
        }

        final double offered = this._system.convertTo(EnergySystem.ForgeEnergy, amount.doubleValue());
        final int accepted = destination.receiveEnergy((int) Math.min(Integer.MAX_VALUE, offered), false);

        return accepted > 0 ? WideAmount.from(EnergySystem.ForgeEnergy.convertTo(this._system, accepted)) : WideAmount.ZERO;
    }

    //endregion
    //region IIOPortHandler

    @Override
    public boolean isConnected() {
        return this._neighbor.isPresent();
    }

    @Override
    public void checkConnections(@Nullable final Level world, final BlockPos position) {
        this._neighbor = this.lookupNeighbor(world, position, CapabilityHelper.ENERGY);
    }

    @Override
    public void invalidate() {

        this._neighbor = LazyOptional.empty();

        if (null != this._exposed) {

            this._exposed.invalidate();
            this._exposed = null;
        }
    }

    @Nullable
    @Override
    public <C> LazyOptional<C> getCapability(final Capability<C> capability, @Nullable final Direction direction) {

        if (CapabilityHelper.ENERGY != capability) {
            return null;
        }

        if (this.isActive() || !this.isExposedOn(direction)) {
            return LazyOptional.empty();
        }

        if (null == this._exposed) {
            this._exposed = LazyOptional.of(() -> ForgeEnergyAdapter.wrap(
                    WideEnergyStoragePolicyWrapper.outputOnly(new MachineStorage<>(this.getPart()))));
        }

        return this._exposed.cast();
    }

    //endregion
    //region internals

    /**
     * Follow the part to whatever machine it is attached to now.
     */
    private record MachineStorage<Controller extends AbstractCuboidMultiblockController<Controller> & IWideEnergyStorage2>(
            AbstractMultiblockEntity<Controller> part) implements IWideEnergyStorage2 {

        private IWideEnergyStorage2 storage() {
            return this.part.getMultiblockController()
                    .filter(AbstractCuboidMultiblockController::isAssembled)
                    .<IWideEnergyStorage2>map(c -> c)
                    .orElse(NullEnergyHandlers.WIDE_STORAGE);
        }

        @Override
        public EnergySystem getEnergySystem() {
            return this.storage().getEnergySystem();
        }

        @Override
        public WideAmount insertEnergy(final EnergySystem system, final WideAmount maxAmount, final OperationMode mode) {
            return this.storage().insertEnergy(system, maxAmount, mode);
        }

        @Override
        public WideAmount extractEnergy(final EnergySystem system, final WideAmount maxAmount, final OperationMode mode) {
            return this.storage().extractEnergy(system, maxAmount, mode);
        }

        @Override
        public WideAmount getEnergyStored(final EnergySystem system) {
            return this.storage().getEnergyStored(system);
        }

        @Override
        public WideAmount getCapacity(final EnergySystem system) {
            return this.storage().getCapacity(system);
        }
    }

    private final EnergySystem _system;
    private LazyOptional<IEnergyStorage> _neighbor;
    @Nullable
    private LazyOptional<IEnergyStorage> _exposed;

    //endregion
}

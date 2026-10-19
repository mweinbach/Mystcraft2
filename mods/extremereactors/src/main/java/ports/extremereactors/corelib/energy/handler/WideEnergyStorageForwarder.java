/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.energy.handler;

import java.util.Objects;
import ports.extremereactors.corelib.energy.EnergySystem;
import ports.extremereactors.corelib.energy.IWideEnergyStorage;

/** Forwards every call to a store that can be swapped at any time, so a capability can stay valid. */
public class WideEnergyStorageForwarder implements IWideEnergyStorage {

    public WideEnergyStorageForwarder(final IWideEnergyStorage handler) {
        this._handler = Objects.requireNonNull(handler);
    }

    public IWideEnergyStorage getHandler() {
        return this._handler;
    }

    public void setHandler(final IWideEnergyStorage handler) {
        this._handler = Objects.requireNonNull(handler);
    }

    @Override
    public EnergySystem getEnergySystem() {
        return this._handler.getEnergySystem();
    }

    @Override
    public double insertEnergy(final EnergySystem system, final double maxAmount, final boolean simulate) {
        return this._handler.insertEnergy(system, maxAmount, simulate);
    }

    @Override
    public double extractEnergy(final EnergySystem system, final double maxAmount, final boolean simulate) {
        return this._handler.extractEnergy(system, maxAmount, simulate);
    }

    @Override
    public double getEnergyStored(final EnergySystem system) {
        return this._handler.getEnergyStored(system);
    }

    @Override
    public double getCapacity(final EnergySystem system) {
        return this._handler.getCapacity(system);
    }

    @Override
    public boolean canInsert() {
        return this._handler.canInsert();
    }

    @Override
    public boolean canExtract() {
        return this._handler.canExtract();
    }

    private IWideEnergyStorage _handler;
}

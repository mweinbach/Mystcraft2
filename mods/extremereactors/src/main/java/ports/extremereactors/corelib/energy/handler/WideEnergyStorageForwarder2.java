/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.energy.handler;

import java.util.Objects;
import ports.extremereactors.corelib.data.WideAmount;
import ports.extremereactors.corelib.data.stack.OperationMode;
import ports.extremereactors.corelib.energy.EnergySystem;
import ports.extremereactors.corelib.energy.IWideEnergyStorage2;

/** Forwards every call to a store that can be swapped at any time, so a capability can stay valid. */
public class WideEnergyStorageForwarder2 implements IWideEnergyStorage2 {

    public WideEnergyStorageForwarder2(final IWideEnergyStorage2 handler) {
        this._handler = Objects.requireNonNull(handler);
    }

    public IWideEnergyStorage2 getHandler() {
        return this._handler;
    }

    public void setHandler(final IWideEnergyStorage2 handler) {
        this._handler = Objects.requireNonNull(handler);
    }

    @Override
    public EnergySystem getEnergySystem() {
        return this._handler.getEnergySystem();
    }

    @Override
    public WideAmount insertEnergy(final EnergySystem system, final WideAmount maxAmount, final OperationMode mode) {
        return this._handler.insertEnergy(system, maxAmount, mode);
    }

    @Override
    public WideAmount extractEnergy(final EnergySystem system, final WideAmount maxAmount, final OperationMode mode) {
        return this._handler.extractEnergy(system, maxAmount, mode);
    }

    @Override
    public WideAmount getEnergyStored(final EnergySystem system) {
        return this._handler.getEnergyStored(system);
    }

    @Override
    public WideAmount getCapacity(final EnergySystem system) {
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

    private IWideEnergyStorage2 _handler;
}

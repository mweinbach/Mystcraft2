/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.energy.handler;

import ports.extremereactors.corelib.data.WideAmount;
import ports.extremereactors.corelib.data.stack.OperationMode;
import ports.extremereactors.corelib.energy.EnergySystem;
import ports.extremereactors.corelib.energy.IWideEnergyStorage;
import ports.extremereactors.corelib.energy.IWideEnergyStorage2;

/** Restricts which operations a wrapped energy store accepts from outside. */
public final class WideEnergyStoragePolicyWrapper {

    public static IWideEnergyStorage inputOnly(final IWideEnergyStorage handler) {
        return new Policy(handler, true, false);
    }

    public static IWideEnergyStorage outputOnly(final IWideEnergyStorage handler) {
        return new Policy(handler, false, true);
    }

    public static IWideEnergyStorage2 inputOnly(final IWideEnergyStorage2 handler) {
        return new Policy2(handler, true, false);
    }

    public static IWideEnergyStorage2 outputOnly(final IWideEnergyStorage2 handler) {
        return new Policy2(handler, false, true);
    }

    private static final class Policy extends WideEnergyStorageForwarder {

        Policy(final IWideEnergyStorage handler, final boolean canInsert, final boolean canExtract) {

            super(handler);
            this._canInsert = canInsert;
            this._canExtract = canExtract;
        }

        @Override
        public double insertEnergy(final EnergySystem system, final double maxAmount, final boolean simulate) {
            return this._canInsert ? super.insertEnergy(system, maxAmount, simulate) : 0;
        }

        @Override
        public double extractEnergy(final EnergySystem system, final double maxAmount, final boolean simulate) {
            return this._canExtract ? super.extractEnergy(system, maxAmount, simulate) : 0;
        }

        @Override
        public boolean canInsert() {
            return this._canInsert && super.canInsert();
        }

        @Override
        public boolean canExtract() {
            return this._canExtract && super.canExtract();
        }

        private final boolean _canInsert;
        private final boolean _canExtract;
    }

    private static final class Policy2 extends WideEnergyStorageForwarder2 {

        Policy2(final IWideEnergyStorage2 handler, final boolean canInsert, final boolean canExtract) {

            super(handler);
            this._canInsert = canInsert;
            this._canExtract = canExtract;
        }

        @Override
        public WideAmount insertEnergy(final EnergySystem system, final WideAmount maxAmount, final OperationMode mode) {
            return this._canInsert ? super.insertEnergy(system, maxAmount, mode) : WideAmount.ZERO;
        }

        @Override
        public WideAmount extractEnergy(final EnergySystem system, final WideAmount maxAmount, final OperationMode mode) {
            return this._canExtract ? super.extractEnergy(system, maxAmount, mode) : WideAmount.ZERO;
        }

        @Override
        public boolean canInsert() {
            return this._canInsert && super.canInsert();
        }

        @Override
        public boolean canExtract() {
            return this._canExtract && super.canExtract();
        }

        private final boolean _canInsert;
        private final boolean _canExtract;
    }

    private WideEnergyStoragePolicyWrapper() {
    }
}

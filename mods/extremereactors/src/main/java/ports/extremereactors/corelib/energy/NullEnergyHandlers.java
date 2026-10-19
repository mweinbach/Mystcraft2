/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.energy;

import ports.extremereactors.corelib.data.WideAmount;
import ports.extremereactors.corelib.data.stack.OperationMode;

/** Energy stores that hold nothing and accept nothing. */
public final class NullEnergyHandlers {

    public static final IWideEnergyStorage STORAGE = new IWideEnergyStorage() {

        @Override
        public EnergySystem getEnergySystem() {
            return EnergySystem.REFERENCE;
        }

        @Override
        public double insertEnergy(final EnergySystem system, final double maxAmount, final boolean simulate) {
            return 0;
        }

        @Override
        public double extractEnergy(final EnergySystem system, final double maxAmount, final boolean simulate) {
            return 0;
        }

        @Override
        public double getEnergyStored(final EnergySystem system) {
            return 0;
        }

        @Override
        public double getCapacity(final EnergySystem system) {
            return 0;
        }

        @Override
        public boolean canInsert() {
            return false;
        }

        @Override
        public boolean canExtract() {
            return false;
        }
    };

    public static final IWideEnergyStorage2 WIDE_STORAGE = new IWideEnergyStorage2() {

        @Override
        public EnergySystem getEnergySystem() {
            return EnergySystem.REFERENCE;
        }

        @Override
        public WideAmount insertEnergy(final EnergySystem system, final WideAmount maxAmount, final OperationMode mode) {
            return WideAmount.ZERO;
        }

        @Override
        public WideAmount extractEnergy(final EnergySystem system, final WideAmount maxAmount, final OperationMode mode) {
            return WideAmount.ZERO;
        }

        @Override
        public WideAmount getEnergyStored(final EnergySystem system) {
            return WideAmount.ZERO;
        }

        @Override
        public WideAmount getCapacity(final EnergySystem system) {
            return WideAmount.ZERO;
        }

        @Override
        public boolean canInsert() {
            return false;
        }

        @Override
        public boolean canExtract() {
            return false;
        }
    };

    private NullEnergyHandlers() {
    }
}

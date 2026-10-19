/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.energy.adapter;

import ports.extremereactors.corelib.data.WideAmount;
import ports.extremereactors.corelib.data.stack.OperationMode;
import ports.extremereactors.corelib.energy.EnergySystem;
import ports.extremereactors.corelib.energy.IEnergyStorage;
import ports.extremereactors.corelib.energy.IWideEnergyStorage;
import ports.extremereactors.corelib.energy.IWideEnergyStorage2;

/** Presents a wide energy store as an integer FE {@link IEnergyStorage}, clamping amounts to the int range. */
public final class ForgeEnergyAdapter {

    public static IEnergyStorage wrap(final IWideEnergyStorage storage) {
        return new IEnergyStorage() {

            @Override
            public int receiveEnergy(final int maxReceive, final boolean simulate) {
                return maxReceive <= 0 ? 0 : clamp(storage.insertEnergy(EnergySystem.ForgeEnergy, maxReceive, simulate));
            }

            @Override
            public int extractEnergy(final int maxExtract, final boolean simulate) {
                return maxExtract <= 0 ? 0 : clamp(storage.extractEnergy(EnergySystem.ForgeEnergy, maxExtract, simulate));
            }

            @Override
            public int getEnergyStored() {
                return clamp(storage.getEnergyStored(EnergySystem.ForgeEnergy));
            }

            @Override
            public int getMaxEnergyStored() {
                return clamp(storage.getCapacity(EnergySystem.ForgeEnergy));
            }

            @Override
            public boolean canExtract() {
                return storage.canExtract();
            }

            @Override
            public boolean canReceive() {
                return storage.canInsert();
            }
        };
    }

    public static IEnergyStorage wrap(final IWideEnergyStorage2 storage) {
        return new IEnergyStorage() {

            @Override
            public int receiveEnergy(final int maxReceive, final boolean simulate) {
                return maxReceive <= 0 ? 0 : storage.insertEnergy(EnergySystem.ForgeEnergy, WideAmount.from(maxReceive), mode(simulate)).intValue();
            }

            @Override
            public int extractEnergy(final int maxExtract, final boolean simulate) {
                return maxExtract <= 0 ? 0 : storage.extractEnergy(EnergySystem.ForgeEnergy, WideAmount.from(maxExtract), mode(simulate)).intValue();
            }

            @Override
            public int getEnergyStored() {
                return storage.getEnergyStored(EnergySystem.ForgeEnergy).intValue();
            }

            @Override
            public int getMaxEnergyStored() {
                return storage.getCapacity(EnergySystem.ForgeEnergy).intValue();
            }

            @Override
            public boolean canExtract() {
                return storage.canExtract();
            }

            @Override
            public boolean canReceive() {
                return storage.canInsert();
            }
        };
    }

    private static OperationMode mode(final boolean simulate) {
        return simulate ? OperationMode.Simulate : OperationMode.Execute;
    }

    private static int clamp(final double value) {
        return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : value <= 0 ? 0 : (int) value;
    }

    private ForgeEnergyAdapter() {
    }
}

/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.energy;

/** An energy store with floating point amounts, expressed in a requested {@link EnergySystem}. */
public interface IWideEnergyStorage {

    EnergySystem getEnergySystem();

    /** @return the energy accepted, in {@code system} units */
    double insertEnergy(EnergySystem system, double maxAmount, boolean simulate);

    /** @return the energy extracted, in {@code system} units */
    double extractEnergy(EnergySystem system, double maxAmount, boolean simulate);

    double getEnergyStored(EnergySystem system);

    double getCapacity(EnergySystem system);

    default boolean canInsert() {
        return true;
    }

    default boolean canExtract() {
        return true;
    }
}

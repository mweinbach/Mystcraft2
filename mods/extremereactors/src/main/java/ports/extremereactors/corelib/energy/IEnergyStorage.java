/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.energy;

/**
 * An integer energy store in FE, the shape loaders expose to other mods. The Forge backend maps it to Forge Energy,
 * the Fabric backend to Team Reborn Energy.
 */
public interface IEnergyStorage {

    /** @return the energy accepted */
    int receiveEnergy(int maxReceive, boolean simulate);

    /** @return the energy extracted */
    int extractEnergy(int maxExtract, boolean simulate);

    int getEnergyStored();

    int getMaxEnergyStored();

    boolean canExtract();

    boolean canReceive();
}

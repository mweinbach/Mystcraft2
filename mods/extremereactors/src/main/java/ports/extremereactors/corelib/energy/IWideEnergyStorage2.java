/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.energy;

import ports.extremereactors.corelib.data.WideAmount;
import ports.extremereactors.corelib.data.stack.OperationMode;

/** An energy store with {@link WideAmount} amounts, expressed in a requested {@link EnergySystem}. */
public interface IWideEnergyStorage2 {

    EnergySystem getEnergySystem();

    /** @return the energy accepted, in {@code system} units */
    WideAmount insertEnergy(EnergySystem system, WideAmount maxAmount, OperationMode mode);

    /** @return the energy extracted, in {@code system} units */
    WideAmount extractEnergy(EnergySystem system, WideAmount maxAmount, OperationMode mode);

    WideAmount getEnergyStored(EnergySystem system);

    WideAmount getCapacity(EnergySystem system);

    default boolean canInsert() {
        return true;
    }

    default boolean canExtract() {
        return true;
    }
}

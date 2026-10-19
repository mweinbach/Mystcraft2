/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.energy;

import ports.extremereactors.corelib.data.WideAmount;
import ports.extremereactors.corelib.data.stack.OperationMode;

public final class EnergyHelper {

    /** Moves up to {@code maxAmount} (in the source's units) from {@code source} to {@code destination}. */
    public static double transferEnergy(final IWideEnergyStorage source, final IWideEnergyStorage destination,
                                        final double maxAmount, final OperationMode mode) {

        final EnergySystem system = source.getEnergySystem();
        final double available = source.extractEnergy(system, maxAmount, true);
        final double accepted = destination.insertEnergy(system, available, true);

        if (accepted <= 0 || mode == OperationMode.Simulate) {
            return Math.max(0, accepted);
        }

        final double extracted = source.extractEnergy(system, accepted, false);

        return destination.insertEnergy(system, extracted, false);
    }

    /** Moves up to {@code maxAmount} (in the source's units) from {@code source} to {@code destination}. */
    public static WideAmount transferEnergy(final IWideEnergyStorage2 source, final IWideEnergyStorage2 destination,
                                            final WideAmount maxAmount, final OperationMode mode) {

        final EnergySystem system = source.getEnergySystem();
        final WideAmount available = source.extractEnergy(system, maxAmount, OperationMode.Simulate);
        final WideAmount accepted = destination.insertEnergy(system, available, OperationMode.Simulate);

        if (!accepted.isPositive() || mode == OperationMode.Simulate) {
            return accepted;
        }

        final WideAmount extracted = source.extractEnergy(system, accepted, OperationMode.Execute);

        return destination.insertEnergy(system, extracted, OperationMode.Execute);
    }

    private EnergyHelper() {
    }
}

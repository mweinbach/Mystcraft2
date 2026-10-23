/*
 * Mekanism 1.19.2 port - in-world tests, Forge side.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest.forge;

import sonar.fluxnetworks.api.energy.IFNEnergyStorage;

/**
 * The Flux Networks call {@code ForgeFluxCapabilityGameTests} makes. Kept out of the scanned test package so the test class
 * itself loads without Flux Networks.
 */
public final class FluxNetworksTestAccess {

    private FluxNetworksTestAccess() {
    }

    public static long extract(Object storage, long amount) {
        return ((IFNEnergyStorage) storage).extractEnergyL(amount, true);
    }
}

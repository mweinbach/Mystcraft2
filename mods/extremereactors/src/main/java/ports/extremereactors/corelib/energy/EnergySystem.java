/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.energy;

import net.minecraft.nbt.CompoundTag;
import ports.extremereactors.platform.Platform;

/**
 * The energy units a machine can work with. The port only ships one internal unit, saved as {@code ForgeEnergy}: it is
 * Forge Energy (FE) on Forge and Team Reborn Energy (E) on Fabric, where 1 E equals 1 FE. Players see the loader's
 * label.
 */
public enum EnergySystem {

    ForgeEnergy(1.0);

    /** The unit every other one is converted through. */
    public static final EnergySystem REFERENCE = ForgeEnergy;

    EnergySystem(final double referenceRatio) {
        this._referenceRatio = referenceRatio;
    }

    public String getUnit() {
        return Platform.backend().energyUnit();
    }

    public String getFullUnit() {
        return Platform.backend().energyUnitName();
    }

    /** Converts {@code amount}, expressed in this unit, to {@code target}. */
    public double convertTo(final EnergySystem target, final double amount) {
        return this == target ? amount : amount * this._referenceRatio / target._referenceRatio;
    }

    public static EnergySystem read(final CompoundTag data, final String key, final EnergySystem defaultValue) {

        if (data.contains(key)) {

            try {
                return EnergySystem.valueOf(data.getString(key));
            } catch (IllegalArgumentException ignored) {
                // unknown system: use the default
            }
        }

        return defaultValue;
    }

    public static CompoundTag write(final CompoundTag data, final String key, final EnergySystem system) {

        data.putString(key, system.name());
        return data;
    }

    private final double _referenceRatio;
}

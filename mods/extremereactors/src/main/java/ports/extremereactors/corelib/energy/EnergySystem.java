/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.energy;

import net.minecraft.nbt.CompoundTag;

/**
 * The energy units a machine can work with. The port only ships Forge Energy (FE); on Fabric the same unit is exposed
 * through Team Reborn Energy, where 1 E equals 1 FE.
 */
public enum EnergySystem {

    ForgeEnergy("FE", "Forge Energy", 1.0);

    /** The unit every other one is converted through. */
    public static final EnergySystem REFERENCE = ForgeEnergy;

    EnergySystem(final String unit, final String fullUnit, final double referenceRatio) {

        this._unit = unit;
        this._fullUnit = fullUnit;
        this._referenceRatio = referenceRatio;
    }

    public String getUnit() {
        return this._unit;
    }

    public String getFullUnit() {
        return this._fullUnit;
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

    private final String _unit;
    private final String _fullUnit;
    private final double _referenceRatio;
}

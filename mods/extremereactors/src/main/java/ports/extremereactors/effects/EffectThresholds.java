/*
 * Extreme Reactors 1.19.2 port - visual effects
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.effects;

/**
 * Where the client effects switch on or off. They are fixed, not client options, because the server resends a
 * machine's state whenever it crosses one of them (see {@link StatusSyncTracker}): the server has to know them.
 */
public final class EffectThresholds {

    /** Fuel temperature, in °C, from which a running actively-cooled reactor steams at its output ports. */
    public static final double STEAM_TEMPERATURE = 100.0;
    /** Fuel temperature, in °C, from which {@link ReactorAlarm#OVERHEAT} is raised. */
    public static final double OVERHEAT_TEMPERATURE = 2000.0;
    /** Coolant tank share under which a running actively-cooled reactor raises {@link ReactorAlarm#COOLANT_LOW}. */
    public static final double LOW_COOLANT_SHARE = 0.1;
    /** Waste share of the fuel pool capacity from which {@link ReactorAlarm#WASTE_FULL} is raised. */
    public static final double WASTE_FULL_SHARE = 0.9;
    /** Exhaust tank share from which a "vent overflow" turbine is drawn venting. */
    public static final double TURBINE_OVERFLOW_SHARE = 0.99;

    private EffectThresholds() {
    }
}

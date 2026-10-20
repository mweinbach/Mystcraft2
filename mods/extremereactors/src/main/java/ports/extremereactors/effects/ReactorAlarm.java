/*
 * Extreme Reactors 1.19.2 port - visual effects
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.effects;

import java.util.EnumSet;
import java.util.Set;

/**
 * The conditions a reactor's controller lamp warns about. Critical ones flash red and sound the alarm; the
 * others flash amber. They are cosmetic: Extreme Reactors has no overheat model and nothing here changes how a
 * reactor runs.
 */
public enum ReactorAlarm {

    OVERHEAT(true),
    COOLANT_LOW(true),
    NO_FUEL(false),
    WASTE_FULL(false);

    /** Waste at or above this share of the fuel pool capacity raises {@link #WASTE_FULL}. */
    public static final double WASTE_FULL_SHARE = 0.9;

    ReactorAlarm(final boolean critical) {
        this._critical = critical;
    }

    public boolean isCritical() {
        return this._critical;
    }

    /** What a client knows about a reactor. */
    public record Readings(boolean active, boolean activelyCooled, double fuelTemperature, double coolantFraction,
                           int fuel, int waste, int capacity) {
    }

    /**
     * @param overheatAt      the fuel temperature, in °C, from which {@link #OVERHEAT} is raised
     * @param lowCoolantBelow the coolant tank share under which a running actively-cooled reactor raises
     *                        {@link #COOLANT_LOW}
     */
    public static Set<ReactorAlarm> evaluate(final Readings readings, final double overheatAt,
                                             final double lowCoolantBelow) {

        final Set<ReactorAlarm> alarms = EnumSet.noneOf(ReactorAlarm.class);

        if (readings.fuelTemperature() >= overheatAt) {
            alarms.add(OVERHEAT);
        }

        if (readings.active() && readings.activelyCooled() && readings.coolantFraction() < lowCoolantBelow) {
            alarms.add(COOLANT_LOW);
        }

        if (readings.active() && readings.fuel() <= 0) {
            alarms.add(NO_FUEL);
        }

        if (readings.capacity() > 0 && readings.waste() >= WASTE_FULL_SHARE * readings.capacity()) {
            alarms.add(WASTE_FULL);
        }

        return alarms;
    }

    public static boolean anyCritical(final Set<ReactorAlarm> alarms) {
        return alarms.stream().anyMatch(ReactorAlarm::isCritical);
    }

    private final boolean _critical;
}

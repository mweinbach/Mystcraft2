/*
 * Extreme Reactors 1.19.2 port - visual effects
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.effects;

/**
 * Server side: decides when a reactor should resend its state to the players around it, so their alarms and
 * glow follow its temperature and coolant without a screen open. Like Extreme Reactors' turbine RPM tracker it
 * only fires on a noticeable change, and at most once every {@link #MIN_INTERVAL} ticks, so a reactor at a steady
 * state sends nothing.
 */
public final class StatusSyncTracker {

    public static final int MIN_INTERVAL = 40;
    public static final double MIN_TEMPERATURE_CHANGE = 20.0;
    public static final double RELATIVE_TEMPERATURE_CHANGE = 0.05;
    public static final double MIN_FRACTION_CHANGE = 0.05;

    /**
     * Call once per server tick.
     *
     * @param temperature the fuel temperature, in °C
     * @param coolant     the coolant tank share, 0 to 1
     * @param vapor       the vapor tank share, 0 to 1
     * @return true if the state should be sent now
     */
    public boolean shouldSync(final double temperature, final double coolant, final double vapor) {

        if (this._ticksSinceSync < MIN_INTERVAL) {

            ++this._ticksSinceSync;
            return false;
        }

        final boolean changed = Double.isNaN(this._temperature) ||
                Math.abs(temperature - this._temperature) >= Math.max(MIN_TEMPERATURE_CHANGE,
                        RELATIVE_TEMPERATURE_CHANGE * Math.abs(this._temperature)) ||
                Math.abs(coolant - this._coolant) >= MIN_FRACTION_CHANGE ||
                Math.abs(vapor - this._vapor) >= MIN_FRACTION_CHANGE;

        if (changed) {

            this._ticksSinceSync = 0;
            this._temperature = temperature;
            this._coolant = coolant;
            this._vapor = vapor;
        }

        return changed;
    }

    private int _ticksSinceSync = MIN_INTERVAL;
    private double _temperature = Double.NaN;
    private double _coolant;
    private double _vapor;
}

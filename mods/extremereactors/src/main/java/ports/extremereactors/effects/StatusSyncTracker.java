/*
 * Extreme Reactors 1.19.2 port - visual effects
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.effects;

/**
 * Server side: decides when a machine should resend its state to the players around it, so their effects follow
 * it without a screen open. Like Extreme Reactors' turbine RPM tracker it fires on a noticeable change, and at
 * most once every {@link #MIN_INTERVAL} ticks, so a machine at a steady state sends nothing. Crossing one of
 * the {@link EffectThresholds} always counts as a change, however small the step.
 * <p>
 * Readings are compared with what was last sent, not with the previous tick, so neither a slow drift nor a
 * crossing during the interval is lost.
 */
public final class StatusSyncTracker {

    public static final int MIN_INTERVAL = 40;

    /**
     * How one reading is watched.
     *
     * @param minChange      the smallest change worth sending
     * @param relativeChange the change worth sending, as a share of the last sent value, when that is larger
     * @param thresholds     values whose crossing is always sent; a reading equal to one is above it
     */
    public record Reading(double minChange, double relativeChange, double... thresholds) {

        public static Reading thresholdsOnly(final double... thresholds) {
            return new Reading(Double.POSITIVE_INFINITY, 0, thresholds);
        }

        boolean changed(final double sent, final double now) {

            if (Math.abs(now - sent) >= Math.max(this.minChange, this.relativeChange * Math.abs(sent))) {
                return true;
            }

            for (final double threshold : this.thresholds) {
                if ((sent >= threshold) != (now >= threshold)) {
                    return true;
                }
            }

            return false;
        }
    }

    /**
     * Fuel temperature (°C), coolant and vapor tank shares, fuel amount and waste share of the fuel capacity.
     */
    public static StatusSyncTracker forReactor() {
        return new StatusSyncTracker(
                new Reading(20.0, 0.05, EffectThresholds.STEAM_TEMPERATURE, EffectThresholds.OVERHEAT_TEMPERATURE),
                new Reading(0.05, 0, EffectThresholds.LOW_COOLANT_SHARE),
                new Reading(0.05, 0),
                Reading.thresholdsOnly(0.5),
                Reading.thresholdsOnly(EffectThresholds.WASTE_FULL_SHARE));
    }

    /**
     * Steam consumed last tick (mB), exhaust tank share and the vent setting's ordinal.
     */
    public static StatusSyncTracker forTurbine() {
        return new StatusSyncTracker(
                new Reading(10.0, 0.25, 0.5),
                new Reading(0.05, 0, EffectThresholds.TURBINE_OVERFLOW_SHARE),
                new Reading(0.5, 0));
    }

    public StatusSyncTracker(final Reading... readings) {

        this._readings = readings.clone();
        this._sent = new double[readings.length];
    }

    /**
     * Call once per server tick.
     *
     * @param values the current readings, in the order the tracker was built with
     * @return true if the state should be sent now
     */
    public boolean shouldSync(final double... values) {

        if (values.length != this._readings.length) {
            throw new IllegalArgumentException("Expected " + this._readings.length + " readings");
        }

        if (this._ticksSinceSync < MIN_INTERVAL) {

            ++this._ticksSinceSync;
            return false;
        }

        boolean changed = !this._hasSent;

        for (int i = 0; !changed && i < values.length; ++i) {
            changed = this._readings[i].changed(this._sent[i], values[i]);
        }

        if (changed) {

            this._ticksSinceSync = 0;
            this._hasSent = true;
            System.arraycopy(values, 0, this._sent, 0, values.length);
        }

        return changed;
    }

    private final Reading[] _readings;
    private final double[] _sent;
    private boolean _hasSent;
    private int _ticksSinceSync = MIN_INTERVAL;
}

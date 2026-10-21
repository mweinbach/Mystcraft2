/*
 * Extreme Reactors 1.19.2 port - visual effects
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.effects;

/**
 * Recognizes a SCRAM from what a client sees: the reactor shuts down and every control rod goes from partly
 * withdrawn to fully inserted within {@link #WINDOW} ticks of each other. Extreme Reactors' SCRAM button does
 * exactly that, and the two changes reach the client as separate updates.
 */
public final class ScramDetector {

    public static final int WINDOW = 20;

    /**
     * Feed the reactor state once per client tick.
     *
     * @return true on the tick a SCRAM is recognized
     */
    public boolean update(final boolean active, final boolean allRodsInserted) {

        if (!this._initialized) {

            this._initialized = true;
            this._wasActive = active;
            this._wereRodsInserted = allRodsInserted;
            return false;
        }

        this._ticksSinceShutdown = this._wasActive && !active ? 0 : age(this._ticksSinceShutdown);
        this._ticksSinceRodsInserted = !this._wereRodsInserted && allRodsInserted ? 0 : age(this._ticksSinceRodsInserted);
        this._wasActive = active;
        this._wereRodsInserted = allRodsInserted;

        if (!active && allRodsInserted &&
                this._ticksSinceShutdown <= WINDOW && this._ticksSinceRodsInserted <= WINDOW) {

            this._ticksSinceShutdown = this._ticksSinceRodsInserted = NEVER;
            return true;
        }

        return false;
    }

    private static int age(final int ticks) {
        return NEVER == ticks ? NEVER : ticks + 1;
    }

    private static final int NEVER = Integer.MAX_VALUE;

    private boolean _initialized;
    private boolean _wasActive;
    private boolean _wereRodsInserted;
    private int _ticksSinceShutdown = NEVER;
    private int _ticksSinceRodsInserted = NEVER;
}

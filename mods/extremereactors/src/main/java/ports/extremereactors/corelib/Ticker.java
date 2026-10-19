/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib;

/**
 * A counter that runs from 0 to {@code period - 1} and then wraps around.
 */
public class Ticker {

    public Ticker(final int period) {

        if (period < 1) {
            throw new IllegalArgumentException("The period must be at least one tick");
        }

        this._period = period;
        this._ticks = 0;
    }

    /**
     * @return true if the counter wrapped around on this tick
     */
    public boolean tick() {

        if (++this._ticks >= this._period) {

            this._ticks = 0;
            return true;
        }

        return false;
    }

    public int getTicks() {
        return this._ticks;
    }

    public int getPeriod() {
        return this._period;
    }

    public void reset() {
        this._ticks = 0;
    }

    private final int _period;
    private int _ticks;
}

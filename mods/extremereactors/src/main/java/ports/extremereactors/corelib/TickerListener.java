/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib;

/**
 * A {@link Ticker} that runs a callback each time it wraps around.
 */
public class TickerListener
        extends Ticker {

    public static TickerListener singleListener(final int period, final Runnable listener) {
        return new TickerListener(period, listener);
    }

    @Override
    public boolean tick() {

        if (super.tick()) {

            this._listener.run();
            return true;
        }

        return false;
    }

    private TickerListener(final int period, final Runnable listener) {

        super(period);
        this._listener = listener;
    }

    private final Runnable _listener;
}

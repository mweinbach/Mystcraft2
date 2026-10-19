/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Subscribers may (un)subscribe while the event is being raised; the change applies from the next raise.
 */
public class Event<Handler>
        implements IEvent<Handler> {

    public Event() {
        this._handlers = new CopyOnWriteArrayList<>();
    }

    @Override
    public void subscribe(final Handler handler) {

        if (!this._handlers.contains(handler)) {
            this._handlers.add(handler);
        }
    }

    @Override
    public void unsubscribe(final Handler handler) {
        this._handlers.remove(handler);
    }

    @Override
    public void unsubscribeAll() {
        this._handlers.clear();
    }

    @Override
    public void raise(final Consumer<Handler> invoker) {

        for (final Handler handler : this._handlers) {
            invoker.accept(handler);
        }
    }

    @Override
    public boolean hasSubscribers() {
        return !this._handlers.isEmpty();
    }

    private final List<Handler> _handlers;
}

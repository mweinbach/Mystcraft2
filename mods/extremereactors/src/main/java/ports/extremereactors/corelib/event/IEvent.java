/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.event;

import java.util.function.Consumer;

/**
 * A simple multicast delegate. {@code Handler} is the functional interface subscribers implement.
 */
public interface IEvent<Handler> {

    void subscribe(Handler handler);

    void unsubscribe(Handler handler);

    void unsubscribeAll();

    /**
     * Invoke every subscriber, in subscription order.
     *
     * @param invoker calls a single subscriber with whatever arguments the event carries
     */
    void raise(Consumer<Handler> invoker);

    boolean hasSubscribers();
}

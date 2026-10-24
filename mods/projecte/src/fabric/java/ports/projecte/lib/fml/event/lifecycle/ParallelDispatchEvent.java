/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.fml.event.lifecycle;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import ports.projecte.lib.eventbus.api.Event;
import ports.projecte.lib.fml.ModContainer;

/**
 * A lifecycle event fired once per module on its mod bus. The port fires these on the main thread, so enqueued work
 * runs immediately.
 */
public class ParallelDispatchEvent extends Event {

    private final ModContainer container;

    public ParallelDispatchEvent(ModContainer container) {
        this.container = container;
    }

    public ModContainer getContainer() {
        return container;
    }

    public CompletableFuture<Void> enqueueWork(Runnable work) {
        try {
            work.run();
            return CompletableFuture.completedFuture(null);
        } catch (Throwable t) {
            return CompletableFuture.failedFuture(t);
        }
    }

    public <T> CompletableFuture<T> enqueueWork(Supplier<T> work) {
        try {
            return CompletableFuture.completedFuture(work.get());
        } catch (Throwable t) {
            return CompletableFuture.failedFuture(t);
        }
    }
}

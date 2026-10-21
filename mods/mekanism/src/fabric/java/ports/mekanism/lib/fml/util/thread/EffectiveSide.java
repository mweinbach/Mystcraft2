/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.fml.util.thread;

import ports.mekanism.lib.api.distmarker.Dist;
import ports.mekanism.lib.fml.LogicalSide;
import ports.mekanism.platform.Platform;

/**
 * Guesses the logical side from the current thread: only the client's render and network threads are client side.
 */
public final class EffectiveSide {

    private EffectiveSide() {
    }

    public static LogicalSide get() {
        if (Platform.dist() == Dist.DEDICATED_SERVER) {
            return LogicalSide.SERVER;
        }
        String name = Thread.currentThread().getName();
        return name.equals("Render thread") || name.startsWith("Netty Client IO") || name.startsWith("Netty Epoll Client IO") ? LogicalSide.CLIENT
                                                                                                                                 : LogicalSide.SERVER;
    }
}

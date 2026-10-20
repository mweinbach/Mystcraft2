/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.internal;

import ports.extremereactors.corelib.client.render.ModRenderHelper;
import ports.extremereactors.platform.ClientPlatform;

/**
 * Client half of {@link Lib}.
 */
public final class LibClient {

    /**
     * Set up the corelib client services. The loader client entrypoints call this once, after installing the client
     * platform backend and before the client proxy is created.
     */
    public static void initialize() {

        if (!s_initialized) {

            s_initialized = true;
            ClientPlatform.backend().onRenderFrameEnd(ModRenderHelper::onRenderFrameEnd);
        }
    }

    private static boolean s_initialized;

    private LibClient() {
    }
}

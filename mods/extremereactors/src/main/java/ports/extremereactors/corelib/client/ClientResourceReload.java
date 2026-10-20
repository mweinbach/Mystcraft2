/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.util.profiling.InactiveProfiler;

import java.util.concurrent.CompletableFuture;

/**
 * Runs a vanilla reload listener synchronously against the client resource manager. Client only.
 */
public final class ClientResourceReload {

    public static void reload(final PreparableReloadListener listener) {
        listener.reload(CompletableFuture::completedFuture, Minecraft.getInstance().getResourceManager(),
                InactiveProfiler.INSTANCE, InactiveProfiler.INSTANCE, Runnable::run, Runnable::run).join();
    }

    private ClientResourceReload() {
    }
}

/*
 * MetalRender - clean-room Apple Metal terrain renderer for Minecraft 1.19.2 (Fabric)
 * Copyright (c) 2026 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.metalrender;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entrypoint. MetalRender only renders on macOS clients; on dedicated servers (and in the
 * server GameTests) it loads, logs, and stays out of the way. All rendering code lives under
 * {@code ports.metalrender.client} and is reached only from the client entrypoint and client mixins.
 */
public final class MetalRenderMod implements ModInitializer {

    public static final String MOD_ID = "metalrender";
    public static final Logger LOGGER = LoggerFactory.getLogger("MetalRender");

    /** Set by the client entrypoint; stays false on dedicated servers. */
    private static volatile boolean clientInitialized;

    @Override
    public void onInitialize() {
        if (FabricLoader.getInstance().getEnvironmentType() != net.fabricmc.api.EnvType.CLIENT) {
            LOGGER.info("MetalRender is a client-side renderer; nothing to do on a dedicated server");
        }
    }

    public static boolean isClientInitialized() {
        return clientInitialized;
    }

    public static void markClientInitialized() {
        clientInitialized = true;
    }
}

/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.internal;

import net.minecraft.resources.ResourceLocation;
import ports.extremereactors.corelib.network.NetworkHandler;
import ports.extremereactors.corelib.network.messages.ContainerDataMessage;
import ports.extremereactors.corelib.network.messages.TileCommandMessage;
import ports.extremereactors.corelib.network.messages.TileSyncMessage;
import ports.extremereactors.multiblock.IMultiblockRegistry;
import ports.extremereactors.multiblock.registry.MultiblockRegistry;
import ports.extremereactors.platform.Platform;

public final class Lib {

    /**
     * Set up the corelib services. The loader entrypoints call this once, after installing the platform backend
     * and before any content is created.
     */
    public static void initialize() {

        if (null != s_network) {
            return;
        }

        s_network = new NetworkHandler(new ResourceLocation(Platform.MOD_ID, "corelib"), "1");
        s_network.registerMessage(TileSyncMessage.class, TileSyncMessage::new);
        s_network.registerMessage(TileCommandMessage.class, TileCommandMessage::new);
        s_network.registerMessage(ContainerDataMessage.class, ContainerDataMessage::new);
    }

    public static NetworkHandler network() {

        if (null == s_network) {
            throw new IllegalStateException("The corelib was not initialized");
        }

        return s_network;
    }

    /**
     * The single registry serves server and client levels alike: it keeps one world registry per level and is
     * driven by the platform's per-level tick and unload hooks on both sides.
     */
    @SuppressWarnings("rawtypes")
    public static IMultiblockRegistry createMultiblockRegistry() {
        return new MultiblockRegistry();
    }

    private static NetworkHandler s_network;

    private Lib() {
    }
}

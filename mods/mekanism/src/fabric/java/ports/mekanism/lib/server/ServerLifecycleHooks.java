/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.server;

import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;
import ports.mekanism.platform.Platform;

public final class ServerLifecycleHooks {

    private ServerLifecycleHooks() {
    }

    @Nullable
    public static MinecraftServer getCurrentServer() {
        return Platform.currentServer();
    }
}

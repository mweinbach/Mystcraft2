/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.server;

import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

public final class ServerLifecycleHooks {

    @Nullable
    private static volatile MinecraftServer currentServer;

    private ServerLifecycleHooks() {
    }

    @Nullable
    public static MinecraftServer getCurrentServer() {
        return currentServer;
    }

    public static void setCurrentServer(@Nullable MinecraftServer server) {
        currentServer = server;
    }
}

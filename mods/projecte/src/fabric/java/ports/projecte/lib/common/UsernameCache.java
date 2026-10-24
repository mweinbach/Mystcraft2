/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.common;

import com.mojang.authlib.GameProfile;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;
import ports.projecte.lib.server.ServerLifecycleHooks;

/**
 * The last name seen for each player: names of players who joined this session, then the server's profile cache.
 */
public final class UsernameCache {

    private static final Map<UUID, String> NAMES = new ConcurrentHashMap<>();

    private UsernameCache() {
    }

    public static void setUsername(UUID uuid, String username) {
        NAMES.put(uuid, username);
    }

    @Nullable
    public static String getLastKnownUsername(UUID uuid) {
        String name = NAMES.get(uuid);
        if (name == null) {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null && server.getProfileCache() != null) {
                name = server.getProfileCache().get(uuid).map(GameProfile::getName).orElse(null);
            }
        }
        return name;
    }

    public static boolean containsUUID(UUID uuid) {
        return getLastKnownUsername(uuid) != null;
    }
}

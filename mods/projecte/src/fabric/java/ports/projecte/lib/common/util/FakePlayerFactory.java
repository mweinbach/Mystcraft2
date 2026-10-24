/*
 * ProjectE 1.19.2 port - Fabric implementation of the Forge API shapes ProjectE uses.
 * Copyright (c) 2026 ProjectE 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.common.util;

import com.mojang.authlib.GameProfile;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;

/**
 * One cached {@link FakePlayer} per level and profile, as Forge keeps.
 */
public final class FakePlayerFactory {

    private static final Map<ServerLevel, Map<GameProfile, FakePlayer>> PLAYERS = new WeakHashMap<>();

    private FakePlayerFactory() {
    }

    public static synchronized FakePlayer get(ServerLevel level, GameProfile profile) {
        return PLAYERS.computeIfAbsent(level, l -> new java.util.HashMap<>()).computeIfAbsent(profile, p -> new FakePlayer(level, p));
    }

    public static synchronized void unloadLevel(ServerLevel level) {
        PLAYERS.remove(level);
    }
}

package net.mystcraft.fabric;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.mystcraft.minecraft.linking.LinkingRules;

/** Native Fabric callbacks are marshaled onto the server before reading/modifying dimension identity. */
public final class FabricLinkingRulesEvents {
    private FabricLinkingRulesEvents() {}
    public static void initialize() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onServer(handler.player,
                () -> LinkingRules.onLogin(handler.player)));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) ->
                onServer(player, () -> LinkingRules.onDimensionChanged(player)));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                onServer(newPlayer, () -> LinkingRules.onRespawn(newPlayer)));
    }
    private static void onServer(ServerPlayer player, Runnable action) {
        if (player.server.isSameThread()) action.run(); else player.server.execute(action);
    }
}

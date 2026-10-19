package net.mystcraft.fabric;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.mystcraft.integration.network.AgeVisualNetwork;

public final class FabricAgeVisualTransport implements AgeVisualNetwork.Transport {
    public static final ResourceLocation CHANNEL = new ResourceLocation("mystcraft", "age_visuals_v3");
    private static final Map<UUID, Integer> PENDING = new HashMap<>();
    private FabricAgeVisualTransport() {}
    public static void initialize() {
        AgeVisualNetwork.initialize(new FabricAgeVisualTransport());
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID playerId = handler.player.getUUID();
            server.execute(() -> PENDING.remove(playerId));
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> PENDING.clear());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            var pending = new java.util.ArrayList<>(PENDING.entrySet());
            for (var entry : pending) {
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player == null) { PENDING.remove(entry.getKey()); continue; }
                if (ServerPlayNetworking.canSend(player, CHANNEL)) {
                    PENDING.remove(entry.getKey()); AgeVisualNetwork.syncAll(player);
                } else if (server.getTickCount() >= entry.getValue()) {
                    PENDING.remove(entry.getKey());
                    player.connection.disconnect(Component.literal("Mystcraft visual protocol mismatch. Install the same Mystcraft version as this server."));
                }
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> AgeVisualNetwork.syncAll(handler.player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> AgeVisualNetwork.syncCurrent(newPlayer));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> AgeVisualNetwork.syncCurrent(player));
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, manager, success) -> { if (success) AgeVisualNetwork.syncAll(server); });
    }
    @Override public void sendToPlayer(ServerPlayer player, AgeVisualNetwork.Snapshot packet) {
        if (!ServerPlayNetworking.canSend(player, CHANNEL)) {
            // JOIN can precede Fabric's channel advertisement; allow the handshake two seconds.
            PENDING.putIfAbsent(player.getUUID(), player.server.getTickCount() + 40);
            return;
        }
        FriendlyByteBuf buffer = PacketByteBufs.create();
        AgeVisualNetwork.Snapshot.encode(packet, buffer);
        ServerPlayNetworking.send(player, CHANNEL, buffer);
    }
}

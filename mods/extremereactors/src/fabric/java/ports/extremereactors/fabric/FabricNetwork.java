/*
 * Extreme Reactors 1.19.2 port - Fabric adapter
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.fabric;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import ports.extremereactors.platform.Network;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Play-phase packets over Fabric's networking API, one channel per {@link ports.extremereactors.corelib.network.NetworkHandler}.
 */
final class FabricNetwork implements Network {

    void installClientSide(final FabricPlatform.ClientSide client) {

        this._client = client;
        this._clientReceivers.forEach(client::registerReceiver);
    }

    @Override
    public void registerServerbound(final ResourceLocation channel, final ServerReceiver receiver) {
        ServerPlayNetworking.registerGlobalReceiver(channel, (server, player, handler, buffer, sender) -> {

            final FriendlyByteBuf data = new FriendlyByteBuf(buffer.copy());

            server.execute(() -> receiver.receive(player, data));
        });
    }

    @Override
    public void registerClientbound(final ResourceLocation channel, final ClientReceiver receiver) {

        this._clientReceivers.put(channel, receiver);

        if (null != this._client) {
            this._client.registerReceiver(channel, receiver);
        }
    }

    @Override
    public void sendToPlayer(final ServerPlayer player, final ResourceLocation channel, final FriendlyByteBuf data) {
        ServerPlayNetworking.send(player, channel, data);
    }

    @Override
    public void sendToServer(final ResourceLocation channel, final FriendlyByteBuf data) {

        if (null != this._client) {
            this._client.sendToServer(channel, data);
        }
    }

    @Override
    public Optional<Level> clientLevel() {
        return null != this._client ? this._client.level() : Optional.empty();
    }

    @Override
    public Optional<Player> clientPlayer() {
        return null != this._client ? this._client.player() : Optional.empty();
    }

    private final Map<ResourceLocation, ClientReceiver> _clientReceivers = new LinkedHashMap<>();
    private FabricPlatform.ClientSide _client;
}

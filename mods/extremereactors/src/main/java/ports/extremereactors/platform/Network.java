/*
 * Extreme Reactors 1.19.2 port - loader services
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.platform;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * Raw play-phase packet transport. Receivers are always invoked on the main thread of the receiving side, with a
 * buffer they may read freely but must not retain.
 */
public interface Network {

    @FunctionalInterface
    interface ServerReceiver {
        void receive(ServerPlayer sender, FriendlyByteBuf data);
    }

    @FunctionalInterface
    interface ClientReceiver {
        void receive(FriendlyByteBuf data);
    }

    void registerServerbound(ResourceLocation channel, ServerReceiver receiver);

    /** Safe to call on a dedicated server, where it does nothing. */
    void registerClientbound(ResourceLocation channel, ClientReceiver receiver);

    void sendToPlayer(ServerPlayer player, ResourceLocation channel, FriendlyByteBuf data);

    /** Client only. */
    void sendToServer(ResourceLocation channel, FriendlyByteBuf data);

    /** The level the local player is in. Empty on a dedicated server or when no world is loaded. */
    Optional<Level> clientLevel();

    /** The local player. Empty on a dedicated server or when no world is loaded. */
    Optional<Player> clientPlayer();
}

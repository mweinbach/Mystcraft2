/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import ports.extremereactors.corelib.LogicalSide;
import ports.extremereactors.corelib.internal.Log;
import ports.extremereactors.platform.Platform;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * One packet channel carrying any number of message classes. Each message is prefixed by the index of its class
 * in registration order, so both sides must register the same classes in the same order.
 */
public class NetworkHandler {

    public NetworkHandler(final ResourceLocation channel, final String protocolVersion) {

        this._channel = channel;
        this._protocolVersion = protocolVersion;
        this._decoders = new ArrayList<>();
        this._indices = new HashMap<>();

        Platform.network().registerServerbound(channel, this::receiveFromClient);
        Platform.network().registerClientbound(channel, this::receiveFromServer);
    }

    public ResourceLocation getChannel() {
        return this._channel;
    }

    public String getProtocolVersion() {
        return this._protocolVersion;
    }

    public <T extends IModMessage> void registerMessage(final Class<T> messageClass, final Function<FriendlyByteBuf, T> decoder) {

        if (this._indices.containsKey(messageClass)) {
            throw new IllegalArgumentException("Message " + messageClass.getName() + " is already registered on " + this._channel);
        }

        this._indices.put(messageClass, this._decoders.size());
        this._decoders.add(decoder);
    }

    public void sendToServer(final IModMessage message) {
        Platform.network().sendToServer(this._channel, this.encode(message));
    }

    public void sendTo(final IModMessage message, final ServerPlayer player) {
        Platform.network().sendToPlayer(player, this._channel, this.encode(message));
    }

    public void sendToAllAround(final IModMessage message, final double x, final double y, final double z,
                                final double radius, final ResourceKey<Level> dimension) {

        final MinecraftServer server = Platform.currentServer();

        if (null == server) {
            return;
        }

        final ServerLevel level = server.getLevel(dimension);

        if (null == level) {
            return;
        }

        final double radiusSquared = radius * radius;

        for (final ServerPlayer player : level.players()) {
            if (player.distanceToSqr(x, y, z) <= radiusSquared) {
                this.sendTo(message, player);
            }
        }
    }

    /**
     * Send the message to every player that has the chunk containing the given position loaded.
     */
    public void sendToAllTracking(final IModMessage message, final ServerLevel level, final BlockPos position) {

        for (final ServerPlayer player : level.getChunkSource().chunkMap.getPlayers(new ChunkPos(position), false)) {
            this.sendTo(message, player);
        }
    }

    private FriendlyByteBuf encode(final IModMessage message) {

        final Integer index = this._indices.get(message.getClass());

        if (null == index) {
            throw new IllegalArgumentException("Message " + message.getClass().getName() + " is not registered on " + this._channel);
        }

        final FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        buffer.writeVarInt(index);
        message.encodeTo(buffer);
        return buffer;
    }

    private IModMessage decode(final FriendlyByteBuf buffer) {

        final int index = buffer.readVarInt();

        if (index < 0 || index >= this._decoders.size()) {
            throw new IllegalArgumentException("Unknown message index " + index + " on " + this._channel);
        }

        return this._decoders.get(index).apply(buffer);
    }

    private void receiveFromClient(final ServerPlayer sender, final FriendlyByteBuf buffer) {

        try {
            this.decode(buffer).processMessage(LogicalSide.CLIENT, sender);
        } catch (RuntimeException ex) {
            Log.LOGGER.warn(Log.NETWORK, "Dropping a malformed message from {} on {}", sender.getGameProfile().getName(), this._channel, ex);
        }
    }

    private void receiveFromServer(final FriendlyByteBuf buffer) {
        this.decode(buffer).processMessage(LogicalSide.SERVER, null);
    }

    private final ResourceLocation _channel;
    private final String _protocolVersion;
    private final List<Function<FriendlyByteBuf, ? extends IModMessage>> _decoders;
    private final Map<Class<?>, Integer> _indices;
}

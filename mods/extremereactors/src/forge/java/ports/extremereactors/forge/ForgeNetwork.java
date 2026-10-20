/*
 * Extreme Reactors 1.19.2 port - Forge adapter
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.forge;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import ports.extremereactors.platform.Network;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Play-phase packets over Forge {@link SimpleChannel}s, one channel per
 * {@link ports.extremereactors.corelib.network.NetworkHandler}, each carrying an opaque payload in each direction.
 */
final class ForgeNetwork implements Network {

    void installClientSide(final ForgePlatform.ClientSide client) {
        this._client = client;
    }

    @Override
    public void registerServerbound(final ResourceLocation channel, final ServerReceiver receiver) {
        this.channel(channel)._serverReceiver = receiver;
    }

    @Override
    public void registerClientbound(final ResourceLocation channel, final ClientReceiver receiver) {
        this.channel(channel)._clientReceiver = receiver;
    }

    @Override
    public void sendToPlayer(final ServerPlayer player, final ResourceLocation channel, final FriendlyByteBuf data) {
        this.channel(channel)._channel.send(PacketDistributor.PLAYER.with(() -> player), new ToClient(data));
    }

    @Override
    public void sendToServer(final ResourceLocation channel, final FriendlyByteBuf data) {

        if (null != this._client) {
            this.channel(channel)._channel.sendToServer(new ToServer(data));
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

    //region internals

    private record ToServer(FriendlyByteBuf data) {
    }

    private record ToClient(FriendlyByteBuf data) {
    }

    private static final class Channel {

        Channel(final ResourceLocation id) {

            this._channel = NetworkRegistry.newSimpleChannel(id, () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

            this._channel.messageBuilder(ToServer.class, 0, NetworkDirection.PLAY_TO_SERVER)
                    .encoder((message, buffer) -> write(message.data(), buffer))
                    .decoder(buffer -> new ToServer(read(buffer)))
                    .consumerMainThread(this::onServerbound)
                    .add();

            this._channel.messageBuilder(ToClient.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                    .encoder((message, buffer) -> write(message.data(), buffer))
                    .decoder(buffer -> new ToClient(read(buffer)))
                    .consumerMainThread(this::onClientbound)
                    .add();
        }

        private void onServerbound(final ToServer message, final Supplier<NetworkEvent.Context> context) {

            final ServerPlayer sender = context.get().getSender();

            if (null != sender && null != this._serverReceiver) {
                this._serverReceiver.receive(sender, message.data());
            }
        }

        private void onClientbound(final ToClient message, final Supplier<NetworkEvent.Context> context) {

            if (null != this._clientReceiver) {
                this._clientReceiver.receive(message.data());
            }
        }

        private static void write(final FriendlyByteBuf payload, final FriendlyByteBuf buffer) {
            buffer.writeBytes(payload, payload.readerIndex(), payload.readableBytes());
        }

        private static FriendlyByteBuf read(final FriendlyByteBuf buffer) {

            final byte[] bytes = new byte[buffer.readableBytes()];

            buffer.readBytes(bytes);
            return new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
        }

        private final SimpleChannel _channel;
        @Nullable
        private volatile ServerReceiver _serverReceiver;
        @Nullable
        private volatile ClientReceiver _clientReceiver;
    }

    private Channel channel(final ResourceLocation id) {

        synchronized (this._channels) {
            return this._channels.computeIfAbsent(id, Channel::new);
        }
    }

    private static final String PROTOCOL = "1";

    private final Map<ResourceLocation, Channel> _channels = new HashMap<>();
    @Nullable
    private ForgePlatform.ClientSide _client;

    //endregion
}

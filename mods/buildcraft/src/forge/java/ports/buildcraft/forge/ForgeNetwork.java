package ports.buildcraft.forge;

import io.netty.buffer.Unpooled;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.platform.Platform;

/** One channel carrying every message in each direction, tagged with the shared channel id it is for. */
final class ForgeNetwork {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(BuildCraft.id("main"), () -> PROTOCOL,
            PROTOCOL::equals, PROTOCOL::equals);
    private static BiConsumer<ResourceLocation, FriendlyByteBuf> clientReceiver = (channel, payload) -> {};

    private record Message(ResourceLocation channel, FriendlyByteBuf payload) {
        void encode(FriendlyByteBuf buffer) {
            buffer.writeResourceLocation(channel);
            buffer.writeVarInt(payload.readableBytes());
            buffer.writeBytes(payload, payload.readerIndex(), payload.readableBytes());
        }

        static Message decode(FriendlyByteBuf buffer) {
            ResourceLocation channel = buffer.readResourceLocation();
            FriendlyByteBuf payload = new FriendlyByteBuf(Unpooled.buffer());
            buffer.readBytes(payload, buffer.readVarInt());
            return new Message(channel, payload);
        }
    }

    private record ServerMessage(Message message) {
        void encode(FriendlyByteBuf buffer) {
            message.encode(buffer);
        }

        static ServerMessage decode(FriendlyByteBuf buffer) {
            return new ServerMessage(Message.decode(buffer));
        }
    }

    private ForgeNetwork() {}

    static void init() {
        CHANNEL.messageBuilder(Message.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Message::encode)
                .decoder(Message::decode)
                .consumerMainThread((message, context) -> {
                    clientReceiver.accept(message.channel(), message.payload());
                    context.get().setPacketHandled(true);
                })
                .add();
        Map<ResourceLocation, Platform.ServerReceiver> serverReceivers = BuildCraft.serverReceivers();
        CHANNEL.messageBuilder(ServerMessage.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ServerMessage::encode)
                .decoder(ServerMessage::decode)
                .consumerMainThread((message, context) -> {
                    Platform.ServerReceiver receiver = serverReceivers.get(message.message().channel());
                    ServerPlayer sender = context.get().getSender();
                    if (receiver != null && sender != null) receiver.receive(sender, message.message().payload());
                    context.get().setPacketHandled(true);
                })
                .add();
    }

    /** Installed by the client entrypoint; dedicated servers never receive these messages. */
    static void setClientReceiver(BiConsumer<ResourceLocation, FriendlyByteBuf> receiver) {
        clientReceiver = receiver;
    }

    static void send(ServerPlayer player, ResourceLocation channel, Consumer<FriendlyByteBuf> payload) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        payload.accept(buffer);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Message(channel, buffer));
    }

    static void sendToServer(ResourceLocation channel, Consumer<FriendlyByteBuf> payload) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        payload.accept(buffer);
        CHANNEL.sendToServer(new ServerMessage(new Message(channel, buffer)));
    }
}

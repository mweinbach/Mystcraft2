package ports.buildcraft.forge;

import io.netty.buffer.Unpooled;
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

/** One channel carrying every server-to-client message, tagged with the shared channel id it is for. */
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
}

package net.mystcraft.integration.network;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Forge packet dispatch; shared linking sessions contain no loader-specific behavior. */
public final class ForgeLinkingTransport implements LinkingNetwork.Transport {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("mystcraft", "linking"), () -> "3", "3"::equals, "3"::equals);
    private ForgeLinkingTransport() {}

    public static void register() {
        CHANNEL.messageBuilder(LinkingNetwork.OpenBook.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(LinkingNetwork.OpenBook::encode).decoder(LinkingNetwork.OpenBook::decode)
                .consumerMainThread((packet, context) -> {
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                            net.mystcraft.minecraft.client.LinkingBookClient.open(packet));
                    context.get().setPacketHandled(true);
                }).add();
        CHANNEL.messageBuilder(Link.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder((packet, buffer) -> buffer.writeUUID(packet.token()))
                .decoder(buffer -> new Link(buffer.readUUID()))
                .consumerMainThread((packet, context) -> {
                    ServerPlayer player = context.get().getSender();
                    if (player != null) LinkingNetwork.activate(player, packet.token());
                    context.get().setPacketHandled(true);
                }).add();
        CHANNEL.messageBuilder(PresentationOptions.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((packet, buffer) -> buffer.writeBoolean(packet.labels()))
                .decoder(buffer -> new PresentationOptions(buffer.readBoolean()))
                .consumerMainThread((packet, context) -> {
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> net.mystcraft.minecraft.presentation.BookPresentationOptions.acceptServer(packet.labels()));
                    context.get().setPacketHandled(true);
                }).add();
        MinecraftForge.EVENT_BUS.addListener(ForgeLinkingTransport::onLogout);
        MinecraftForge.EVENT_BUS.addListener(ForgeLinkingTransport::onLogin);
        LinkingNetwork.initialize(new ForgeLinkingTransport());
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LinkingNetwork.clear(event.getEntity().getUUID());
    }
    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new PresentationOptions(net.mystcraft.minecraft.presentation.BookPresentationOptions.readLabels(player.server.getServerDirectory().toPath())));
    }
    @Override public void sendToPlayer(ServerPlayer player, LinkingNetwork.OpenBook packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
    @Override public void sendRequest(UUID token) { CHANNEL.sendToServer(new Link(token)); }
    private record Link(UUID token) {}
    private record PresentationOptions(boolean labels) {}
}

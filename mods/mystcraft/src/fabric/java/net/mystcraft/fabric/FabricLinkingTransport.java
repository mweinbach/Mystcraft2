package net.mystcraft.fabric;

import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.mystcraft.integration.network.LinkingNetwork;

public final class FabricLinkingTransport implements LinkingNetwork.Transport {
    public static final ResourceLocation OPEN_BOOK = new ResourceLocation("mystcraft", "open_book_v3");
    public static final ResourceLocation LINK = new ResourceLocation("mystcraft", "link_v3");
    public static final ResourceLocation PRESENTATION_OPTIONS = new ResourceLocation("mystcraft", "book_presentation_v3");
    public static void initialize() {
        LinkingNetwork.initialize(new FabricLinkingTransport());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (ServerPlayNetworking.canSend(handler.player, PRESENTATION_OPTIONS)) {
                FriendlyByteBuf data = PacketByteBufs.create();
                data.writeBoolean(net.mystcraft.minecraft.presentation.BookPresentationOptions.readLabels(server.getServerDirectory().toPath()));
                ServerPlayNetworking.send(handler.player, PRESENTATION_OPTIONS, data);
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(LINK, (server, player, handler, data, response) -> {
            UUID token = data.readUUID();
            server.execute(() -> LinkingNetwork.activate(player, token));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID playerId = handler.player.getUUID();
            server.execute(() -> LinkingNetwork.clear(playerId));
        });
    }
    @Override public void sendToPlayer(ServerPlayer player, LinkingNetwork.OpenBook packet) {
        if (!ServerPlayNetworking.canSend(player, OPEN_BOOK)) {
            LinkingNetwork.clear(player.getUUID());
            player.displayClientMessage(Component.literal(
                    "Mystcraft client version mismatch. Install the same Mystcraft version as this server."), false);
            return;
        }
        FriendlyByteBuf data = PacketByteBufs.create();
        LinkingNetwork.OpenBook.encode(packet, data);
        ServerPlayNetworking.send(player, OPEN_BOOK, data);
    }
    @Override public void sendRequest(UUID token) { FabricMystcraftClient.sendRequest(token); }
}

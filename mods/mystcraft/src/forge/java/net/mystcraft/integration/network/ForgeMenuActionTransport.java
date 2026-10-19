package net.mystcraft.integration.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ForgeMenuActionTransport {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("mystcraft", "menus"), () -> "1", "1"::equals, "1"::equals);
    private ForgeMenuActionTransport() {}
    public static void initialize() {
        CHANNEL.messageBuilder(MenuActions.Action.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MenuActions.Action::encode).decoder(MenuActions.Action::decode)
                .consumerMainThread((packet, context) -> {
                    var player = context.get().getSender();
                    if (player != null) MenuActions.handle(player, packet);
                    context.get().setPacketHandled(true);
                }).add();
        MenuActions.initialize(CHANNEL::sendToServer);
    }
}

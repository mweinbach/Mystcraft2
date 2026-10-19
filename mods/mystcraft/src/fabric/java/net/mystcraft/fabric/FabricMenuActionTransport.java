package net.mystcraft.fabric;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.mystcraft.integration.network.MenuActions;

public final class FabricMenuActionTransport {
    private static final ResourceLocation ACTION = new ResourceLocation("mystcraft", "menu_action_v1");
    private FabricMenuActionTransport() {}
    public static void initialize() {
        ServerPlayNetworking.registerGlobalReceiver(ACTION, (server, player, handler, buffer, response) -> {
            MenuActions.Action packet = MenuActions.Action.decode(buffer);
            server.execute(() -> MenuActions.handle(player, packet));
        });
        MenuActions.initialize(packet -> {
            var buffer = PacketByteBufs.create();
            packet.encode(buffer);
            ClientPlayNetworking.send(ACTION, buffer);
        });
    }
}

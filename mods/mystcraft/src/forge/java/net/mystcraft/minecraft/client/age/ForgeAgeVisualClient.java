package net.mystcraft.minecraft.client.age;

import net.mystcraft.integration.network.AgeVisualNetwork;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;

/** Referenced only through the physical-client distribution gate. */
public final class ForgeAgeVisualClient {
    private ForgeAgeVisualClient() {}
    public static void initialize() { MinecraftForge.EVENT_BUS.addListener(ForgeAgeVisualClient::logout); }
    private static void logout(ClientPlayerNetworkEvent.LoggingOut event) { AgeVisualClient.clear(); }
    public static void receive(AgeVisualNetwork.Snapshot packet) {
        if (packet.reset()) AgeVisualClient.clear();
        for (var entry : packet.entries()) AgeVisualClient.receive(entry.dimension(), entry.settings());
    }
}

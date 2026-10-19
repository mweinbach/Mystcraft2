package net.mystcraft.fabric;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.mystcraft.integration.network.AgeVisualNetwork;
import net.mystcraft.minecraft.client.age.AgeVisualClient;

public final class FabricAgeVisualClient {
    private FabricAgeVisualClient() {}
    public static void initialize() {
        FabricAgeVisualEffects.initialize();
        ClientPlayNetworking.registerGlobalReceiver(FabricAgeVisualTransport.CHANNEL, (client, handler, buffer, response) -> {
            AgeVisualNetwork.Snapshot packet = AgeVisualNetwork.Snapshot.decode(buffer);
            client.execute(() -> {
                if (packet.reset()) AgeVisualClient.clear();
                for (var entry : packet.entries()) AgeVisualClient.receive(entry.dimension(), entry.settings());
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(AgeVisualClient::clear));
    }
}

package com.technovision.advancedgenetics.fabric.client;

import com.technovision.advancedgenetics.AdvancedGeneticsClient;
import com.technovision.advancedgenetics.client.ClientPacketHandler;
import com.technovision.advancedgenetics.network.KeyPressedPacket;
import com.technovision.advancedgenetics.network.ModNetwork;
import com.technovision.advancedgenetics.network.SyncGeneticsPacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/** Fabric client hooks for the shared client registration. */
public class AdvancedGeneticsFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        AdvancedGeneticsClient.KEY_MAPPINGS.forEach(KeyBindingHelper::registerKeyBinding);
        AdvancedGeneticsClient.registerScreensAndProperties();
        AdvancedGeneticsClient.registerItemColors((color, item) -> ColorProviderRegistry.ITEM.register(color, item));
        ClientTickEvents.END_CLIENT_TICK.register(client -> AdvancedGeneticsClient.tick(client, AdvancedGeneticsFabricClient::send));
        ClientPlayNetworking.registerGlobalReceiver(ModNetwork.SYNC_GENETICS, (client, handler, buf, sender) -> {
            CompoundTag genetics = SyncGeneticsPacket.decode(buf).genetics();
            client.execute(() -> ClientPacketHandler.handleGeneticsSync(genetics));
        });
    }

    private static void send(KeyPressedPacket packet) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        KeyPressedPacket.encode(packet, buf);
        ClientPlayNetworking.send(ModNetwork.KEY_PRESSED, buf);
    }
}

package com.technovision.advancedgenetics.network;

import com.technovision.advancedgenetics.AdvancedGenetics;
import com.technovision.advancedgenetics.registry.ComponentRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Forge networking replacing Cardinal Components auto-sync and Fabric key packets.
 */
public class ModNetwork {

    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(AdvancedGenetics.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(SyncGeneticsPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncGeneticsPacket::encode)
                .decoder(SyncGeneticsPacket::decode)
                .consumerMainThread(SyncGeneticsPacket::handle)
                .add();
        CHANNEL.messageBuilder(KeyPressedPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(KeyPressedPacket::encode)
                .decoder(KeyPressedPacket::decode)
                .consumerMainThread(KeyPressedPacket::handle)
                .add();
    }

    /**
     * Sends a player's genes to their own client, which needs them for climbing,
     * web walking, mob sight and key bindings.
     */
    public static void syncGenetics(ServerPlayer player) {
        if (player instanceof FakePlayer || player.connection == null) return;
        SyncGeneticsPacket packet = new SyncGeneticsPacket(ComponentRegistry.getGenetics(player).serializeNBT());
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}

package com.technovision.advancedgenetics.forge;

import com.technovision.advancedgenetics.AdvancedGenetics;
import com.technovision.advancedgenetics.client.ClientPacketHandler;
import com.technovision.advancedgenetics.network.KeyPressedPacket;
import com.technovision.advancedgenetics.network.SyncGeneticsPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/** Forge transport for the shared packets. */
public final class ForgeNetwork {

    private static final String PROTOCOL_VERSION = "1";
    public static final ResourceLocation CHANNEL_NAME = new ResourceLocation(AdvancedGenetics.MOD_ID, "main");
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            CHANNEL_NAME,
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private ForgeNetwork() { }

    static void register() {
        int id = 0;
        CHANNEL.messageBuilder(SyncGeneticsPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncGeneticsPacket::encode)
                .decoder(SyncGeneticsPacket::decode)
                .consumerMainThread(ForgeNetwork::handleSync)
                .add();
        CHANNEL.messageBuilder(KeyPressedPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(KeyPressedPacket::encode)
                .decoder(KeyPressedPacket::decode)
                .consumerMainThread(ForgeNetwork::handleKeyPressed)
                .add();
    }

    private static void handleSync(SyncGeneticsPacket packet, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleGeneticsSync(packet.genetics()));
        context.get().setPacketHandled(true);
    }

    private static void handleKeyPressed(KeyPressedPacket packet, Supplier<NetworkEvent.Context> context) {
        KeyPressedPacket.handle(packet, context.get().getSender());
        context.get().setPacketHandled(true);
    }
}

package com.technovision.advancedgenetics.network;

import com.technovision.advancedgenetics.events.KeyInputEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record KeyPressedPacket(String geneName) {

    private static final int MAX_LENGTH = 32;

    public static void encode(KeyPressedPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.geneName, MAX_LENGTH);
    }

    public static KeyPressedPacket decode(FriendlyByteBuf buf) {
        return new KeyPressedPacket(buf.readUtf(MAX_LENGTH));
    }

    public static void handle(KeyPressedPacket packet, Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null) {
            KeyInputEvents.onKeyPressed(player, packet.geneName);
        }
        context.get().setPacketHandled(true);
    }
}

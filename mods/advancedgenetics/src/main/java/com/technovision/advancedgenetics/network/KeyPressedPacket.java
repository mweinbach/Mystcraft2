package com.technovision.advancedgenetics.network;

import com.technovision.advancedgenetics.events.KeyInputEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public record KeyPressedPacket(String geneName) {

    private static final int MAX_LENGTH = 32;

    public static void encode(KeyPressedPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.geneName, MAX_LENGTH);
    }

    public static KeyPressedPacket decode(FriendlyByteBuf buf) {
        return new KeyPressedPacket(buf.readUtf(MAX_LENGTH));
    }

    /** Runs on the server thread. */
    public static void handle(KeyPressedPacket packet, ServerPlayer sender) {
        if (sender != null) {
            KeyInputEvents.onKeyPressed(sender, packet.geneName);
        }
    }
}

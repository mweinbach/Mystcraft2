/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.network;

import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import ports.mekanism.platform.Platform;

public final class NetworkEvent {

    private NetworkEvent() {
    }

    /**
     * The receiving side of one packet. Work queued with {@link #enqueueWork} runs on the game thread.
     */
    public static final class Context {

        private final NetworkDirection direction;
        @Nullable
        private final ServerPlayer sender;
        private boolean packetHandled;

        public Context(NetworkDirection direction, @Nullable ServerPlayer sender) {
            this.direction = direction;
            this.sender = sender;
        }

        public NetworkDirection getDirection() {
            return direction;
        }

        /**
         * The player who sent a client-to-server packet; null on the client.
         */
        @Nullable
        public ServerPlayer getSender() {
            return sender;
        }

        public CompletableFuture<Void> enqueueWork(Runnable runnable) {
            return Platform.executeOnGameThread(direction == NetworkDirection.PLAY_TO_SERVER, runnable);
        }

        public void setPacketHandled(boolean packetHandled) {
            this.packetHandled = packetHandled;
        }

        public boolean getPacketHandled() {
            return packetHandled;
        }
    }
}

/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import ports.extremereactors.corelib.LogicalSide;

import javax.annotation.Nullable;

/**
 * A message sent through a {@link NetworkHandler}. Implementations also need a constructor-like decoder taking a
 * {@link FriendlyByteBuf}, registered with {@link NetworkHandler#registerMessage}.
 */
public interface IModMessage {

    void encodeTo(FriendlyByteBuf buffer);

    /**
     * Process the message on the main thread of the receiving side.
     *
     * @param sourceSide the side that sent the message
     * @param sender     the player who sent the message, if it came from a client
     */
    void processMessage(LogicalSide sourceSide, @Nullable ServerPlayer sender);
}

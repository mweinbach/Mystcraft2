/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.network.messages;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import ports.extremereactors.corelib.LogicalSide;
import ports.extremereactors.corelib.item.inventory.container.ModContainer;
import ports.extremereactors.corelib.network.IModMessage;
import ports.extremereactors.platform.Platform;

import javax.annotation.Nullable;

/**
 * Server to client: changed container data values of the container the player has open.
 */
public class ContainerDataMessage
        implements IModMessage {

    public ContainerDataMessage(final int containerId, final FriendlyByteBuf payload) {

        this._containerId = containerId;
        this._payload = new byte[payload.readableBytes()];
        payload.getBytes(payload.readerIndex(), this._payload);
    }

    public ContainerDataMessage(final FriendlyByteBuf buffer) {

        this._containerId = buffer.readVarInt();
        this._payload = buffer.readByteArray(MAX_PAYLOAD);
    }

    @Override
    public void encodeTo(final FriendlyByteBuf buffer) {

        buffer.writeVarInt(this._containerId);
        buffer.writeByteArray(this._payload);
    }

    @Override
    public void processMessage(final LogicalSide sourceSide, @Nullable final ServerPlayer sender) {

        if (LogicalSide.SERVER != sourceSide) {
            return;
        }

        Platform.network().clientPlayer()
                .map((Player player) -> player.containerMenu)
                .filter(menu -> menu.containerId == this._containerId && menu instanceof ModContainer)
                .ifPresent(menu -> ((ModContainer) menu).onContainerDataUpdate(
                        new FriendlyByteBuf(Unpooled.wrappedBuffer(this._payload))));
    }

    private static final int MAX_PAYLOAD = 1 << 20;

    private final int _containerId;
    private final byte[] _payload;
}

/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import ports.extremereactors.corelib.LogicalSide;
import ports.extremereactors.platform.Platform;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * A message addressed to the block entity at a given position, in the sender's level (client to server) or in the
 * local player's level (server to client).
 */
public abstract class AbstractModTileMessage
        implements IModMessage {

    /** How far, in blocks, a player may be from the block entity a client message is addressed to. */
    public static final double MAX_CLIENT_REACH = 64.0;

    protected AbstractModTileMessage(final BlockPos position) {
        this._position = position.immutable();
    }

    protected AbstractModTileMessage(final FriendlyByteBuf buffer) {
        this._position = buffer.readBlockPos();
    }

    public BlockPos getTileEntityPosition() {
        return this._position;
    }

    @Override
    public void encodeTo(final FriendlyByteBuf buffer) {
        buffer.writeBlockPos(this._position);
    }

    @Override
    public final void processMessage(final LogicalSide sourceSide, @Nullable final ServerPlayer sender) {

        final Optional<Level> level;

        if (LogicalSide.CLIENT == sourceSide) {

            if (null == sender || sender.distanceToSqr(this._position.getX() + 0.5, this._position.getY() + 0.5,
                    this._position.getZ() + 0.5) > MAX_CLIENT_REACH * MAX_CLIENT_REACH) {
                return;
            }

            level = Optional.of(sender.getLevel());

        } else {

            level = Platform.network().clientLevel();
        }

        level.filter(l -> l.isLoaded(this._position))
                .map(l -> l.getBlockEntity(this._position))
                .ifPresent(te -> this.processTileEntityMessage(sourceSide, te, sender));
    }

    protected void processTileEntityMessage(final LogicalSide sourceSide, final BlockEntity tileEntity,
                                            @Nullable final ServerPlayer sender) {
        this.processTileEntityMessage(sourceSide, tileEntity);
    }

    protected void processTileEntityMessage(final LogicalSide sourceSide, final BlockEntity tileEntity) {
    }

    private final BlockPos _position;
}

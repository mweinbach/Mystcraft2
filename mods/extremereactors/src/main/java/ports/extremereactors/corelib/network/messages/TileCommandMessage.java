/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.network.messages;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import ports.extremereactors.corelib.LogicalSide;
import ports.extremereactors.corelib.block.AbstractModBlockEntity;
import ports.extremereactors.corelib.network.AbstractModTileMessage;

import javax.annotation.Nullable;

/**
 * A named command, with optional parameters, for the block entity at a position. Usually sent by a screen to the
 * server.
 */
public class TileCommandMessage extends AbstractModTileMessage {

    public TileCommandMessage(final BlockPos position, final String name, final CompoundTag parameters) {

        super(position);
        this._name = name;
        this._parameters = parameters;
    }

    public TileCommandMessage(final FriendlyByteBuf buffer) {

        super(buffer);
        this._name = buffer.readUtf(MAX_NAME_LENGTH);

        final CompoundTag parameters = buffer.readNbt();

        this._parameters = null != parameters ? parameters : new CompoundTag();
    }

    @Override
    public void encodeTo(final FriendlyByteBuf buffer) {

        super.encodeTo(buffer);
        buffer.writeUtf(this._name, MAX_NAME_LENGTH);
        buffer.writeNbt(this._parameters);
    }

    @Override
    protected void processTileEntityMessage(final LogicalSide sourceSide, final BlockEntity tileEntity,
                                            @Nullable final ServerPlayer sender) {

        if (tileEntity instanceof AbstractModBlockEntity) {
            ((AbstractModBlockEntity) tileEntity).handleCommand(sourceSide, this._name, this._parameters);
        }
    }

    private static final int MAX_NAME_LENGTH = 256;

    private final String _name;
    private final CompoundTag _parameters;
}

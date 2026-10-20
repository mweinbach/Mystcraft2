/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.network.messages;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.entity.BlockEntity;
import ports.extremereactors.corelib.LogicalSide;
import ports.extremereactors.corelib.data.nbt.ISyncableEntity;
import ports.extremereactors.corelib.network.AbstractModTileMessage;

/**
 * Server to client: a {@link ISyncableEntity.SyncReason#NetworkUpdate} payload for the block entity at a position.
 */
public class TileSyncMessage extends AbstractModTileMessage {

    public TileSyncMessage(final BlockPos position, final CompoundTag data) {

        super(position);
        this._data = data;
    }

    public TileSyncMessage(final FriendlyByteBuf buffer) {

        super(buffer);
        this._data = buffer.readNbt();
    }

    @Override
    public void encodeTo(final FriendlyByteBuf buffer) {

        super.encodeTo(buffer);
        buffer.writeNbt(this._data);
    }

    @Override
    protected void processTileEntityMessage(final LogicalSide sourceSide, final BlockEntity tileEntity) {

        if (LogicalSide.SERVER == sourceSide && null != this._data && tileEntity instanceof ISyncableEntity) {
            ((ISyncableEntity) tileEntity).syncDataFrom(this._data, ISyncableEntity.SyncReason.NetworkUpdate);
        }
    }

    private final CompoundTag _data;
}

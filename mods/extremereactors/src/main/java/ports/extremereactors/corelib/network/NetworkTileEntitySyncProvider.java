/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import ports.extremereactors.corelib.data.nbt.ISyncableEntity;
import ports.extremereactors.corelib.internal.Lib;
import ports.extremereactors.corelib.network.messages.TileSyncMessage;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Sends the network-update data of an {@link ISyncableEntity} to the enlisted players, addressed to the block
 * entity at a (possibly moving) position.
 */
public class NetworkTileEntitySyncProvider implements INetworkTileEntitySyncProvider {

    public static INetworkTileEntitySyncProvider create(final Supplier<BlockPos> position, final ISyncableEntity entity) {
        return new NetworkTileEntitySyncProvider(position, entity, null);
    }

    /**
     * @param wrapKey if not null, the payload is sent as a child of a compound with this key
     */
    public static INetworkTileEntitySyncProvider create(final Supplier<BlockPos> position, final ISyncableEntity entity,
                                                        @Nullable final String wrapKey) {
        return new NetworkTileEntitySyncProvider(position, entity, wrapKey);
    }

    @Override
    public void enlistForUpdates(final ServerPlayer player, final boolean updateNow) {

        synchronized (this._players) {

            if (!this._players.contains(player)) {
                this._players.add(player);
            }
        }

        if (updateNow) {
            Lib.network().sendTo(this.createMessage(), player);
        }
    }

    @Override
    public void delistFromUpdates(final ServerPlayer player) {

        synchronized (this._players) {
            this._players.remove(player);
        }
    }

    @Override
    public void sendUpdates() {

        final List<ServerPlayer> targets;

        synchronized (this._players) {

            this._players.removeIf(ServerPlayer::hasDisconnected);

            if (this._players.isEmpty()) {
                return;
            }

            targets = new ArrayList<>(this._players);
        }

        final TileSyncMessage message = this.createMessage();

        for (final ServerPlayer player : targets) {
            Lib.network().sendTo(message, player);
        }
    }

    //region internals

    private NetworkTileEntitySyncProvider(final Supplier<BlockPos> position, final ISyncableEntity entity,
                                          @Nullable final String wrapKey) {

        this._position = position;
        this._entity = entity;
        this._wrapKey = wrapKey;
        this._players = new ArrayList<>(2);
    }

    private TileSyncMessage createMessage() {

        CompoundTag data = this._entity.syncDataTo(new CompoundTag(), ISyncableEntity.SyncReason.NetworkUpdate);

        if (null != this._wrapKey) {

            final CompoundTag wrapper = new CompoundTag();

            wrapper.put(this._wrapKey, data);
            data = wrapper;
        }

        return new TileSyncMessage(this._position.get(), data);
    }

    private final Supplier<BlockPos> _position;
    private final ISyncableEntity _entity;
    @Nullable
    private final String _wrapKey;
    private final List<ServerPlayer> _players;

    //endregion
}

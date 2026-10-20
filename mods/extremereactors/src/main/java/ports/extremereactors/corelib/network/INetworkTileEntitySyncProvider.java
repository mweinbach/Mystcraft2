/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.network;

import net.minecraft.server.level.ServerPlayer;

/**
 * Something that sends periodic {@link ports.extremereactors.corelib.data.nbt.ISyncableEntity.SyncReason#NetworkUpdate}
 * data to the players that asked for it, usually the players looking at its screen.
 */
public interface INetworkTileEntitySyncProvider {

    /**
     * @param updateNow if true, send an update to the player right away
     */
    void enlistForUpdates(ServerPlayer player, boolean updateNow);

    void delistFromUpdates(ServerPlayer player);

    void sendUpdates();
}

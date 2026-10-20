/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.item.inventory.container;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;
import ports.extremereactors.corelib.block.AbstractModBlockEntity;
import ports.extremereactors.corelib.network.INetworkTileEntitySyncProvider;

import javax.annotation.Nullable;

/**
 * A container for the screen of a block entity.
 * <p>
 * On the server, a block entity that is an {@link INetworkTileEntitySyncProvider} sends its updates to the player for
 * as long as the container is open.
 */
public class ModTileContainer<T extends AbstractModBlockEntity>
        extends ModContainer {

    /** How far, in blocks, a player may move from the block entity before the container closes. */
    public static final double MAX_DISTANCE = 8.0;

    public static <T extends AbstractModBlockEntity> ModTileContainer<T> empty(@Nullable final MenuType<?> type,
                                                                              final int windowId, final T tile,
                                                                              final ServerPlayer player) {
        return new ModTileContainer<>(ContainerFactory.EMPTY, type, windowId, player.getInventory(), tile);
    }

    public static <T extends AbstractModBlockEntity> ModTileContainer<T> empty(@Nullable final MenuType<?> type,
                                                                              final int windowId,
                                                                              final Inventory playerInventory,
                                                                              final T tile) {
        return new ModTileContainer<>(ContainerFactory.EMPTY, type, windowId, playerInventory, tile);
    }

    /** Client side: the block entity position comes from the data the server sent when opening the screen. */
    public static <T extends AbstractModBlockEntity> ModTileContainer<T> empty(@Nullable final MenuType<?> type,
                                                                              final int windowId,
                                                                              final Inventory playerInventory,
                                                                              final FriendlyByteBuf networkData) {
        return empty(type, windowId, playerInventory, AbstractModBlockEntity.<T>getGuiClientBlockEntity(networkData));
    }

    public ModTileContainer(final ContainerFactory factory, @Nullable final MenuType<?> type, final int windowId,
                            final Inventory playerInventory, final T tile) {

        super(factory, type, windowId, playerInventory);
        this._tile = tile;

        if (playerInventory.player instanceof ServerPlayer player && tile instanceof INetworkTileEntitySyncProvider provider) {
            provider.enlistForUpdates(player, true);
        }
    }

    public T getTileEntity() {
        return this._tile;
    }

    //region AbstractContainerMenu

    @Override
    public boolean stillValid(final Player player) {

        final Level level = this._tile.getLevel();

        return !this._tile.isRemoved() && null != level &&
                level.getBlockEntity(this._tile.getBlockPos()) == this._tile &&
                player.distanceToSqr(this._tile.getBlockPos().getX() + 0.5, this._tile.getBlockPos().getY() + 0.5,
                        this._tile.getBlockPos().getZ() + 0.5) <= MAX_DISTANCE * MAX_DISTANCE;
    }

    @Override
    public void removed(final Player player) {

        super.removed(player);

        if (player instanceof ServerPlayer serverPlayer && this._tile instanceof INetworkTileEntitySyncProvider provider) {
            provider.delistFromUpdates(serverPlayer);
        }
    }

    //endregion
    //region internals

    private final T _tile;

    //endregion
}

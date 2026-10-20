/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.client.gui;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import ports.extremereactors.corelib.block.AbstractModBlockEntity;
import ports.extremereactors.corelib.internal.Lib;
import ports.extremereactors.corelib.item.inventory.container.ModTileContainer;
import ports.extremereactors.corelib.network.messages.TileCommandMessage;

/**
 * The screen of a block entity. It is updated when the block entity data changes, and sends commands to the server
 * side block entity.
 */
public class ModTileContainerScreen<T extends AbstractModBlockEntity, C extends ModTileContainer<T>>
        extends ModContainerScreen<C> {

    public ModTileContainerScreen(final C container, final Inventory inventory, final Component title,
                                  final int guiWidth, final int guiHeight) {
        super(container, inventory, title, guiWidth, guiHeight);
    }

    public T getTileEntity() {
        return this.getMenu().getTileEntity();
    }

    /**
     * Send a command to the block entity on the server. Commands are dropped while the screen is being updated, as
     * they would only echo the data that was just received.
     */
    protected void sendCommandToServer(final String name) {
        this.sendCommandToServer(name, new CompoundTag());
    }

    protected void sendCommandToServer(final String name, final CompoundTag parameters) {

        if (!this.isDataUpdateInProgress()) {
            Lib.network().sendToServer(new TileCommandMessage(this.getTileEntity().getBlockPos(), name, parameters));
        }
    }

    //region ModContainerScreen

    @Override
    protected void subscribeDataUpdateEvents(final Runnable handler) {

        super.subscribeDataUpdateEvents(handler);
        this.getTileEntity().DataUpdated.subscribe(handler);
    }

    @Override
    protected void unsubscribeDataUpdateEvents(final Runnable handler) {

        this.getTileEntity().DataUpdated.unsubscribe(handler);
        super.unsubscribeDataUpdateEvents(handler);
    }

    //endregion
}

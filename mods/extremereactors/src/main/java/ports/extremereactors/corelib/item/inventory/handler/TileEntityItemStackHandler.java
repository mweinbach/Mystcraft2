/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.item.inventory.handler;

import net.minecraft.world.level.block.entity.BlockEntity;

/** An {@link ItemStackHandler} that marks its block entity as changed whenever a slot changes. */
public class TileEntityItemStackHandler extends ItemStackHandler {

    public TileEntityItemStackHandler(final BlockEntity owner, final int size) {

        super(size);
        this._owner = owner;
    }

    @Override
    protected void onContentsChanged(final int slot) {
        this._owner.setChanged();
    }

    private final BlockEntity _owner;
}

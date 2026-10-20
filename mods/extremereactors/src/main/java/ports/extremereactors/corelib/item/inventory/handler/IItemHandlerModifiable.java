/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.item.inventory.handler;

import net.minecraft.world.item.ItemStack;

public interface IItemHandlerModifiable extends IItemHandler {

    /** Replaces the slot contents without any validity check. */
    void setStackInSlot(int slot, ItemStack stack);
}

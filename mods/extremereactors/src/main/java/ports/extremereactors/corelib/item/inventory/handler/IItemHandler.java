/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.item.inventory.handler;

import net.minecraft.world.item.ItemStack;

/** A fixed set of item slots that can be inserted into and extracted from. */
public interface IItemHandler {

    int getSlots();

    /** The slot contents; callers must not modify the returned stack. */
    ItemStack getStackInSlot(int slot);

    /** @return what could not be inserted */
    ItemStack insertItem(int slot, ItemStack stack, boolean simulate);

    /** @return what was extracted */
    ItemStack extractItem(int slot, int amount, boolean simulate);

    int getSlotLimit(int slot);

    boolean isItemValid(int slot, ItemStack stack);
}

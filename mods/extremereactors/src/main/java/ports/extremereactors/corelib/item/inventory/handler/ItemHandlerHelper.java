/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.item.inventory.handler;

import net.minecraft.world.item.ItemStack;

public final class ItemHandlerHelper {

    /** Inserts into the first slots that accept the stack; returns what is left over. */
    public static ItemStack insertItem(final IItemHandler destination, ItemStack stack, final boolean simulate) {

        for (int slot = 0; slot < destination.getSlots() && !stack.isEmpty(); ++slot) {
            stack = destination.insertItem(slot, stack, simulate);
        }

        return stack;
    }

    public static boolean canItemStacksStack(final ItemStack a, final ItemStack b) {
        return !a.isEmpty() && !b.isEmpty() && ItemStack.isSameItemSameTags(a, b) && a.isStackable();
    }

    public static ItemStack copyStackWithSize(final ItemStack stack, final int size) {

        if (size <= 0 || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        final ItemStack copy = stack.copy();

        copy.setCount(size);
        return copy;
    }

    private ItemHandlerHelper() {
    }
}

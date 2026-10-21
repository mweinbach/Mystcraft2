/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.items;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ItemHandlerHelper {

    private ItemHandlerHelper() {
    }

    @NotNull
    public static ItemStack insertItem(IItemHandler dest, @NotNull ItemStack stack, boolean simulate) {
        if (dest == null || stack.isEmpty()) {
            return stack;
        }
        for (int i = 0; i < dest.getSlots(); i++) {
            stack = dest.insertItem(i, stack, simulate);
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }
        return stack;
    }

    /**
     * Same item and tag; the counts may differ.
     */
    public static boolean canItemStacksStack(@NotNull ItemStack a, @NotNull ItemStack b) {
        if (a.isEmpty() || !a.sameItem(b) || a.hasTag() != b.hasTag()) {
            return false;
        }
        return !a.hasTag() || a.getTag().equals(b.getTag());
    }

    public static boolean canItemStacksStackRelaxed(@NotNull ItemStack a, @NotNull ItemStack b) {
        if (a.isEmpty() || b.isEmpty() || a.getItem() != b.getItem() || !a.isStackable()) {
            return false;
        }
        return ItemStack.tagMatches(a, b);
    }

    @NotNull
    public static ItemStack copyStackWithSize(@NotNull ItemStack stack, int size) {
        if (size == 0) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = stack.copy();
        copy.setCount(size);
        return copy;
    }

    /**
     * Inserts into slots that already hold a matching stack first, then into empty ones.
     */
    @NotNull
    public static ItemStack insertItemStacked(@Nullable IItemHandler inventory, @NotNull ItemStack stack, boolean simulate) {
        if (inventory == null || stack.isEmpty()) {
            return stack;
        }
        if (!stack.isStackable()) {
            return insertItem(inventory, stack, simulate);
        }
        int slots = inventory.getSlots();
        for (int i = 0; i < slots; i++) {
            if (canItemStacksStackRelaxed(inventory.getStackInSlot(i), stack)) {
                stack = inventory.insertItem(i, stack, simulate);
                if (stack.isEmpty()) {
                    return ItemStack.EMPTY;
                }
            }
        }
        for (int i = 0; i < slots; i++) {
            if (inventory.getStackInSlot(i).isEmpty()) {
                stack = inventory.insertItem(i, stack, simulate);
                if (stack.isEmpty()) {
                    return ItemStack.EMPTY;
                }
            }
        }
        return stack;
    }
}

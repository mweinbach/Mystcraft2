/*
 * ProjectE 1.19.2 port - Fabric implementation of the Forge API shapes ProjectE uses.
 * Copyright (c) 2026 ProjectE 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.items.wrapper;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import ports.projecte.lib.items.IItemHandlerModifiable;
import ports.projecte.lib.items.ItemHandlerHelper;

/**
 * Exposes a vanilla {@link Container} as an item handler. Stack limits are the container's and the item's, and only
 * stacks the container accepts are inserted.
 */
public class InvWrapper implements IItemHandlerModifiable {

    private final Container inv;

    public InvWrapper(Container inv) {
        this.inv = inv;
    }

    public Container getInv() {
        return inv;
    }

    @Override
    public int getSlots() {
        return inv.getContainerSize();
    }

    @NotNull
    @Override
    public ItemStack getStackInSlot(int slot) {
        return inv.getItem(slot);
    }

    @NotNull
    @Override
    public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack existing = inv.getItem(slot);
        if (!existing.isEmpty()) {
            if (!ItemHandlerHelper.canItemStacksStack(stack, existing) || !inv.canPlaceItem(slot, stack)) {
                return stack;
            }
            int space = Math.min(existing.getMaxStackSize(), getSlotLimit(slot)) - existing.getCount();
            if (space <= 0) {
                return stack;
            }
            int moved = Math.min(space, stack.getCount());
            if (!simulate) {
                ItemStack grown = existing.copy();
                grown.grow(moved);
                inv.setItem(slot, grown);
                inv.setChanged();
            }
            return moved == stack.getCount() ? ItemStack.EMPTY : ItemHandlerHelper.copyStackWithSize(stack, stack.getCount() - moved);
        }
        if (!inv.canPlaceItem(slot, stack)) {
            return stack;
        }
        int moved = Math.min(stack.getCount(), Math.min(stack.getMaxStackSize(), getSlotLimit(slot)));
        if (!simulate) {
            inv.setItem(slot, ItemHandlerHelper.copyStackWithSize(stack, moved));
            inv.setChanged();
        }
        return moved == stack.getCount() ? ItemStack.EMPTY : ItemHandlerHelper.copyStackWithSize(stack, stack.getCount() - moved);
    }

    @NotNull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount == 0) {
            return ItemStack.EMPTY;
        }
        ItemStack existing = inv.getItem(slot);
        if (existing.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int extracted = Math.min(amount, existing.getCount());
        if (simulate) {
            return ItemHandlerHelper.copyStackWithSize(existing, extracted);
        }
        ItemStack result = inv.removeItem(slot, extracted);
        inv.setChanged();
        return result;
    }

    @Override
    public void setStackInSlot(int slot, @NotNull ItemStack stack) {
        inv.setItem(slot, stack);
    }

    @Override
    public int getSlotLimit(int slot) {
        return inv.getMaxStackSize();
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return inv.canPlaceItem(slot, stack);
    }
}

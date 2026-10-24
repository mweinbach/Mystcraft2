/*
 * ProjectE 1.19.2 port - Fabric implementation of the Forge API shapes ProjectE uses.
 * Copyright (c) 2026 ProjectE 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.items.wrapper;

import net.minecraft.core.Direction;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ports.projecte.lib.items.IItemHandlerModifiable;
import ports.projecte.lib.items.ItemHandlerHelper;

/**
 * Exposes the slots a {@link WorldlyContainer} offers on one side, honouring its insert and extract rules.
 */
public class SidedInvWrapper implements IItemHandlerModifiable {

    protected final WorldlyContainer inv;
    @Nullable
    protected final Direction side;

    public SidedInvWrapper(WorldlyContainer inv, @Nullable Direction side) {
        this.inv = inv;
        this.side = side;
    }

    public static int getSlot(WorldlyContainer inv, int slot, @Nullable Direction side) {
        int[] slots = inv.getSlotsForFace(side == null ? Direction.UP : side);
        return slot < slots.length ? slots[slot] : -1;
    }

    private int slot(int slot) {
        return getSlot(inv, slot, side);
    }

    @Override
    public int getSlots() {
        return inv.getSlotsForFace(side == null ? Direction.UP : side).length;
    }

    @NotNull
    @Override
    public ItemStack getStackInSlot(int slot) {
        int index = slot(slot);
        return index == -1 ? ItemStack.EMPTY : inv.getItem(index);
    }

    @NotNull
    @Override
    public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        int index = slot(slot);
        if (index == -1 || !inv.canPlaceItem(index, stack) || !inv.canPlaceItemThroughFace(index, stack, side)) {
            return stack;
        }
        ItemStack existing = inv.getItem(index);
        if (!existing.isEmpty() && !ItemHandlerHelper.canItemStacksStack(stack, existing)) {
            return stack;
        }
        int limit = Math.min(stack.getMaxStackSize(), getSlotLimit(slot)) - existing.getCount();
        if (limit <= 0) {
            return stack;
        }
        int moved = Math.min(limit, stack.getCount());
        if (!simulate) {
            ItemStack placed = existing.isEmpty() ? ItemHandlerHelper.copyStackWithSize(stack, moved) : existing.copy();
            if (!existing.isEmpty()) {
                placed.grow(moved);
            }
            inv.setItem(index, placed);
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
        int index = slot(slot);
        if (index == -1) {
            return ItemStack.EMPTY;
        }
        ItemStack existing = inv.getItem(index);
        if (existing.isEmpty() || !inv.canTakeItemThroughFace(index, existing, side)) {
            return ItemStack.EMPTY;
        }
        int extracted = Math.min(amount, existing.getCount());
        if (simulate) {
            return ItemHandlerHelper.copyStackWithSize(existing, extracted);
        }
        ItemStack result = inv.removeItem(index, extracted);
        inv.setChanged();
        return result;
    }

    @Override
    public void setStackInSlot(int slot, @NotNull ItemStack stack) {
        int index = slot(slot);
        if (index != -1) {
            inv.setItem(index, stack);
        }
    }

    @Override
    public int getSlotLimit(int slot) {
        return inv.getMaxStackSize();
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        int index = slot(slot);
        return index != -1 && inv.canPlaceItem(index, stack);
    }
}

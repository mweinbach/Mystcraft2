/*
 * ProjectE 1.19.2 port - Fabric implementation of the Forge API shapes ProjectE uses.
 * Copyright (c) 2026 ProjectE 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.items.wrapper;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import ports.projecte.lib.items.IItemHandlerModifiable;

/**
 * The slots {@code [minSlot, maxSlot)} of another item handler, renumbered from zero.
 */
public class RangedWrapper implements IItemHandlerModifiable {

    private final IItemHandlerModifiable compose;
    private final int minSlot;
    private final int maxSlot;

    public RangedWrapper(IItemHandlerModifiable compose, int minSlot, int maxSlotExclusive) {
        if (maxSlotExclusive < minSlot) {
            throw new IllegalArgumentException("Max slot must be greater than or equal to min slot");
        }
        this.compose = compose;
        this.minSlot = minSlot;
        this.maxSlot = maxSlotExclusive;
    }

    private boolean inRange(int slot) {
        return slot >= 0 && slot < maxSlot - minSlot;
    }

    @Override
    public int getSlots() {
        return maxSlot - minSlot;
    }

    @NotNull
    @Override
    public ItemStack getStackInSlot(int slot) {
        return inRange(slot) ? compose.getStackInSlot(slot + minSlot) : ItemStack.EMPTY;
    }

    @NotNull
    @Override
    public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        return inRange(slot) ? compose.insertItem(slot + minSlot, stack, simulate) : stack;
    }

    @NotNull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return inRange(slot) ? compose.extractItem(slot + minSlot, amount, simulate) : ItemStack.EMPTY;
    }

    @Override
    public void setStackInSlot(int slot, @NotNull ItemStack stack) {
        if (inRange(slot)) {
            compose.setStackInSlot(slot + minSlot, stack);
        }
    }

    @Override
    public int getSlotLimit(int slot) {
        return inRange(slot) ? compose.getSlotLimit(slot + minSlot) : 0;
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return inRange(slot) && compose.isItemValid(slot + minSlot, stack);
    }
}

/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.item.inventory.handler;

import java.util.Objects;
import net.minecraft.world.item.ItemStack;

/** Forwards every call to a handler that can be swapped at any time, so a capability can stay valid. */
public class ItemHandlerModifiableForwarder implements IItemHandlerModifiable {

    public ItemHandlerModifiableForwarder(final IItemHandlerModifiable handler) {
        this._handler = Objects.requireNonNull(handler);
    }

    public IItemHandlerModifiable getHandler() {
        return this._handler;
    }

    public void setHandler(final IItemHandlerModifiable handler) {
        this._handler = Objects.requireNonNull(handler);
    }

    @Override
    public int getSlots() {
        return this.getHandler().getSlots();
    }

    @Override
    public ItemStack getStackInSlot(final int slot) {
        return this.getHandler().getStackInSlot(slot);
    }

    @Override
    public void setStackInSlot(final int slot, final ItemStack stack) {
        this.getHandler().setStackInSlot(slot, stack);
    }

    @Override
    public ItemStack insertItem(final int slot, final ItemStack stack, final boolean simulate) {
        return this.getHandler().insertItem(slot, stack, simulate);
    }

    @Override
    public ItemStack extractItem(final int slot, final int amount, final boolean simulate) {
        return this.getHandler().extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(final int slot) {
        return this.getHandler().getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(final int slot, final ItemStack stack) {
        return this.getHandler().isItemValid(slot, stack);
    }

    private IItemHandlerModifiable _handler;
}

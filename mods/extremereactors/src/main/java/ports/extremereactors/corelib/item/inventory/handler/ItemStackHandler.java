/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.item.inventory.handler;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import ports.extremereactors.corelib.data.nbt.ICompoundSerializable;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/** A plain item inventory backed by a list of stacks. */
public class ItemStackHandler implements IItemHandlerModifiable, ICompoundSerializable {

    public ItemStackHandler() {
        this(1);
    }

    public ItemStackHandler(final int size) {
        this._stacks = NonNullList.withSize(size, ItemStack.EMPTY);
    }

    public void setSize(final int size) {
        this._stacks = NonNullList.withSize(size, ItemStack.EMPTY);
    }

    @Override
    public int getSlots() {
        return this._stacks.size();
    }

    @Override
    public ItemStack getStackInSlot(final int slot) {
        return this.isValidSlot(slot) ? this._stacks.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public void setStackInSlot(final int slot, final ItemStack stack) {

        if (this.isValidSlot(slot)) {

            this._stacks.set(slot, stack);
            this.onContentsChanged(slot);
        }
    }

    @Override
    public ItemStack insertItem(final int slot, final ItemStack stack, final boolean simulate) {

        if (stack.isEmpty() || !this.isValidSlot(slot) || !this.isItemValid(slot, stack)) {
            return stack;
        }

        final ItemStack existing = this._stacks.get(slot);
        final int limit = Math.min(this.getSlotLimit(slot), stack.getMaxStackSize());

        if (!existing.isEmpty() && !ItemHandlerHelper.canItemStacksStack(stack, existing)) {
            return stack;
        }

        final int space = limit - existing.getCount();

        if (space <= 0) {
            return stack;
        }

        final int inserted = Math.min(space, stack.getCount());

        if (!simulate) {

            if (existing.isEmpty()) {
                this._stacks.set(slot, ItemHandlerHelper.copyStackWithSize(stack, inserted));
            } else {
                existing.grow(inserted);
            }

            this.onContentsChanged(slot);
        }

        return inserted >= stack.getCount() ? ItemStack.EMPTY : ItemHandlerHelper.copyStackWithSize(stack, stack.getCount() - inserted);
    }

    @Override
    public ItemStack extractItem(final int slot, final int amount, final boolean simulate) {

        if (amount <= 0 || !this.isValidSlot(slot)) {
            return ItemStack.EMPTY;
        }

        final ItemStack existing = this._stacks.get(slot);

        if (existing.isEmpty()) {
            return ItemStack.EMPTY;
        }

        final int extracted = Math.min(amount, Math.min(existing.getCount(), existing.getMaxStackSize()));

        if (!simulate) {

            if (extracted >= existing.getCount()) {
                this._stacks.set(slot, ItemStack.EMPTY);
            } else {
                this._stacks.set(slot, ItemHandlerHelper.copyStackWithSize(existing, existing.getCount() - extracted));
            }

            this.onContentsChanged(slot);
        }

        return ItemHandlerHelper.copyStackWithSize(existing, extracted);
    }

    @Override
    public int getSlotLimit(final int slot) {
        return 64;
    }

    @Override
    public boolean isItemValid(final int slot, final ItemStack stack) {
        return true;
    }

    public CompoundTag serializeNBT() {

        final ListTag list = new ListTag();

        for (int slot = 0; slot < this._stacks.size(); ++slot) {

            if (!this._stacks.get(slot).isEmpty()) {

                final CompoundTag entry = new CompoundTag();

                entry.putInt("Slot", slot);
                this._stacks.get(slot).save(entry);
                list.add(entry);
            }
        }

        final CompoundTag data = new CompoundTag();

        data.put("Items", list);
        data.putInt("Size", this._stacks.size());
        return data;
    }

    public void deserializeNBT(final CompoundTag data) {

        this.setSize(data.contains("Size", Tag.TAG_INT) ? data.getInt("Size") : this._stacks.size());

        final ListTag list = data.getList("Items", Tag.TAG_COMPOUND);

        for (int i = 0; i < list.size(); ++i) {

            final CompoundTag entry = list.getCompound(i);
            final int slot = entry.getInt("Slot");

            if (this.isValidSlot(slot)) {
                this._stacks.set(slot, ItemStack.of(entry));
            }
        }

        this.onLoad();
    }

    protected void onContentsChanged(final int slot) {
    }

    protected void onLoad() {
    }

    protected NonNullList<ItemStack> getStacks() {
        return this._stacks;
    }

    private boolean isValidSlot(final int slot) {
        return slot >= 0 && slot < this._stacks.size();
    }

    private NonNullList<ItemStack> _stacks;
}

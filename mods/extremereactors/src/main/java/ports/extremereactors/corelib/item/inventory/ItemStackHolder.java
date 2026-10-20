/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.item.inventory;

import java.util.function.BiPredicate;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import ports.extremereactors.corelib.data.nbt.ICompoundSerializable;
import ports.extremereactors.corelib.data.nbt.ISyncableEntity;
import ports.extremereactors.corelib.data.stack.AbstractStackHolder;
import ports.extremereactors.corelib.item.inventory.handler.IItemHandlerModifiable;
import ports.extremereactors.corelib.item.inventory.handler.ItemHandlerHelper;

/** A fixed number of item slots with change notifications and an optional per-slot validator. */
public class ItemStackHolder extends AbstractStackHolder<ItemStackHolder, ItemStack>
        implements IItemHandlerModifiable, ISyncableEntity, ICompoundSerializable {

    public ItemStackHolder(final int size) {
        this(size, (slot, stack) -> true);
    }

    public ItemStackHolder(final int size, final BiPredicate<Integer, ItemStack> validator) {

        this._stacks = NonNullList.withSize(size, ItemStack.EMPTY);
        this._validator = validator;
    }

    //region IStackHolder

    @Override
    public int getSlots() {
        return this._stacks.size();
    }

    @Override
    public ItemStack getStackAt(final int index) {
        return this.getStackInSlot(index);
    }

    @Override
    public int getAmount(final int index) {
        return this.getStackInSlot(index).getCount();
    }

    @Override
    public boolean isEmpty(final int index) {
        return this.getStackInSlot(index).isEmpty();
    }

    //endregion
    //region IItemHandlerModifiable

    @Override
    public ItemStack getStackInSlot(final int slot) {
        return this.isValidSlot(slot) ? this._stacks.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public void setStackInSlot(final int slot, final ItemStack stack) {

        if (!this.isValidSlot(slot)) {
            return;
        }

        final ItemStack old = this._stacks.get(slot);

        this._stacks.set(slot, stack);
        this.onChange(changeTypeOf(old.isEmpty(), stack.isEmpty(), ItemStack.isSameItemSameTags(old, stack)), slot);
    }

    @Override
    public ItemStack insertItem(final int slot, final ItemStack stack, final boolean simulate) {

        if (stack.isEmpty() || !this.isValidSlot(slot) || !this.isItemValid(slot, stack)) {
            return stack;
        }

        final ItemStack existing = this._stacks.get(slot);

        if (!existing.isEmpty() && !ItemHandlerHelper.canItemStacksStack(stack, existing)) {
            return stack;
        }

        final int space = Math.min(this.getSlotLimit(slot), stack.getMaxStackSize()) - existing.getCount();
        final int inserted = Math.min(Math.max(0, space), stack.getCount());

        if (inserted <= 0) {
            return stack;
        }

        if (!simulate) {

            if (existing.isEmpty()) {
                this._stacks.set(slot, ItemHandlerHelper.copyStackWithSize(stack, inserted));
                this.onChange(ChangeType.Added, slot);
            } else {
                existing.grow(inserted);
                this.onChange(ChangeType.Modified, slot);
            }
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

        final int extracted = Math.min(amount, existing.getCount());
        final ItemStack result = ItemHandlerHelper.copyStackWithSize(existing, extracted);

        if (!simulate) {

            if (extracted >= existing.getCount()) {
                this._stacks.set(slot, ItemStack.EMPTY);
                this.onChange(ChangeType.Removed, slot);
            } else {
                existing.shrink(extracted);
                this.onChange(ChangeType.Modified, slot);
            }
        }

        return result;
    }

    @Override
    public int getSlotLimit(final int slot) {
        return 64;
    }

    @Override
    public boolean isItemValid(final int slot, final ItemStack stack) {
        return this._validator.test(slot, stack);
    }

    //endregion
    //region ISyncableEntity

    @Override
    public void syncDataFrom(final CompoundTag data, final SyncReason syncReason) {
        this.deserializeNBT(data);
    }

    @Override
    public CompoundTag syncDataTo(final CompoundTag data, final SyncReason syncReason) {

        data.merge(this.serializeNBT());
        return data;
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
        return data;
    }

    public void deserializeNBT(final CompoundTag data) {

        for (int slot = 0; slot < this._stacks.size(); ++slot) {
            this._stacks.set(slot, ItemStack.EMPTY);
        }

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

    //endregion

    private boolean isValidSlot(final int slot) {
        return slot >= 0 && slot < this._stacks.size();
    }

    private final NonNullList<ItemStack> _stacks;
    private final BiPredicate<Integer, ItemStack> _validator;
}

/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.item.inventory.container.slot;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import ports.extremereactors.corelib.item.inventory.container.slot.type.SlotType;
import ports.extremereactors.corelib.item.inventory.handler.IItemHandler;
import ports.extremereactors.corelib.item.inventory.handler.IItemHandlerModifiable;

/**
 * A container slot backed by a slot of an {@link IItemHandler} instead of a vanilla container.
 */
public class ItemHandlerSlot
        extends Slot {

    public ItemHandlerSlot(final IItemHandler handler, final int index, final SlotTemplate template,
                           final int x, final int y) {

        super(NO_CONTAINER, index, x, y);
        this._handler = handler;
        this._index = index;
        this._template = template;
    }

    public IItemHandler getItemHandler() {
        return this._handler;
    }

    public SlotTemplate getTemplate() {
        return this._template;
    }

    /** A slot for the same handler slot at another position. */
    public ItemHandlerSlot copyAt(final int x, final int y) {
        return new ItemHandlerSlot(this._handler, this._index, this._template, x, y);
    }

    /**
     * Insert as much of the stack as the handler accepts, honoring the template.
     *
     * @return what was not inserted
     */
    public ItemStack insert(final ItemStack stack, final boolean simulate) {

        if (stack.isEmpty() || !this.mayPlace(stack)) {
            return stack;
        }

        final ItemStack remainder = this._handler.insertItem(this._index, stack, simulate);

        if (!simulate && remainder.getCount() != stack.getCount()) {
            this.setChanged();
        }

        return remainder;
    }

    //region Slot

    @Override
    public boolean mayPlace(final ItemStack stack) {
        return !stack.isEmpty() && this._template.canPlace(this._index, stack) &&
                this._handler.isItemValid(this._index, stack);
    }

    @Override
    public boolean mayPickup(final Player player) {
        return SlotType.Static != this._template.getType() && !this._handler.extractItem(this._index, 1, true).isEmpty();
    }

    @Override
    public ItemStack getItem() {
        return this._handler.getStackInSlot(this._index);
    }

    @Override
    public void set(final ItemStack stack) {

        if (this._handler instanceof IItemHandlerModifiable modifiable) {

            modifiable.setStackInSlot(this._index, stack);

        } else {

            final ItemStack current = this._handler.getStackInSlot(this._index);

            if (!current.isEmpty()) {
                this._handler.extractItem(this._index, current.getCount(), false);
            }

            if (!stack.isEmpty()) {
                this._handler.insertItem(this._index, stack, false);
            }
        }

        this.setChanged();
    }

    @Override
    public void initialize(final ItemStack stack) {
        this.set(stack);
    }

    @Override
    public int getMaxStackSize() {
        return this._handler.getSlotLimit(this._index);
    }

    @Override
    public int getMaxStackSize(final ItemStack stack) {
        return Math.min(stack.getMaxStackSize(), this.getMaxStackSize());
    }

    @Override
    public ItemStack remove(final int amount) {
        return this._handler.extractItem(this._index, amount, false);
    }

    /** Overrides the Forge slot method on Forge; unused on Fabric. */
    public boolean isSameInventory(final Slot other) {
        return other instanceof ItemHandlerSlot slot && slot._handler == this._handler;
    }

    //endregion
    //region internals

    private static final SimpleContainer NO_CONTAINER = new SimpleContainer(0);

    private final IItemHandler _handler;
    private final int _index;
    private final SlotTemplate _template;

    //endregion
}

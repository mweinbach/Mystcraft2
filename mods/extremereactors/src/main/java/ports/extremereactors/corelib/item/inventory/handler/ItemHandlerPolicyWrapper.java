/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.item.inventory.handler;

import java.util.function.BiPredicate;
import net.minecraft.world.item.ItemStack;

/** Restricts which operations, and which items, a wrapped handler accepts from outside. */
public class ItemHandlerPolicyWrapper extends ItemHandlerModifiableForwarder {

    public static IItemHandlerModifiable inputOnly(final IItemHandlerModifiable handler,
                                                   final BiPredicate<Integer, ItemStack> validator) {
        return new ItemHandlerPolicyWrapper(handler, true, false, validator);
    }

    public static IItemHandlerModifiable outputOnly(final IItemHandlerModifiable handler) {
        return new ItemHandlerPolicyWrapper(handler, false, true, (slot, stack) -> false);
    }

    public static IItemHandlerModifiable twoWay(final IItemHandlerModifiable handler,
                                                final BiPredicate<Integer, ItemStack> validator) {
        return new ItemHandlerPolicyWrapper(handler, true, true, validator);
    }

    protected ItemHandlerPolicyWrapper(final IItemHandlerModifiable handler, final boolean canInsert,
                                       final boolean canExtract, final BiPredicate<Integer, ItemStack> validator) {

        super(handler);
        this._canInsert = canInsert;
        this._canExtract = canExtract;
        this._validator = validator;
    }

    @Override
    public ItemStack insertItem(final int slot, final ItemStack stack, final boolean simulate) {
        return this.isItemValid(slot, stack) ? super.insertItem(slot, stack, simulate) : stack;
    }

    @Override
    public ItemStack extractItem(final int slot, final int amount, final boolean simulate) {
        return this._canExtract ? super.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
    }

    @Override
    public boolean isItemValid(final int slot, final ItemStack stack) {
        return this._canInsert && this._validator.test(slot, stack) && super.isItemValid(slot, stack);
    }

    private final boolean _canInsert;
    private final boolean _canExtract;
    private final BiPredicate<Integer, ItemStack> _validator;
}

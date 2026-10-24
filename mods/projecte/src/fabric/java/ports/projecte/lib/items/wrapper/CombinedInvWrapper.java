/*
 * ProjectE 1.19.2 port - Fabric implementation of the Forge API shapes ProjectE uses.
 * Copyright (c) 2026 ProjectE 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.items.wrapper;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import ports.projecte.lib.items.IItemHandlerModifiable;

/**
 * Several item handlers presented as one, their slots numbered one after another.
 */
public class CombinedInvWrapper implements IItemHandlerModifiable {

    protected final IItemHandlerModifiable[] itemHandler;
    protected final int[] baseIndex;
    protected final int slotCount;

    public CombinedInvWrapper(IItemHandlerModifiable... itemHandler) {
        this.itemHandler = itemHandler;
        this.baseIndex = new int[itemHandler.length];
        int index = 0;
        for (int i = 0; i < itemHandler.length; i++) {
            index += itemHandler[i].getSlots();
            baseIndex[i] = index;
        }
        this.slotCount = index;
    }

    protected int getIndexForSlot(int slot) {
        if (slot < 0) {
            return -1;
        }
        for (int i = 0; i < baseIndex.length; i++) {
            if (slot < baseIndex[i]) {
                return i;
            }
        }
        return -1;
    }

    protected IItemHandlerModifiable getHandlerFromIndex(int index) {
        return index < 0 || index >= itemHandler.length ? EmptyHandler.INSTANCE : itemHandler[index];
    }

    protected int getSlotFromIndex(int slot, int index) {
        return index <= 0 || index >= baseIndex.length ? slot : slot - baseIndex[index - 1];
    }

    @Override
    public void setStackInSlot(int slot, @NotNull ItemStack stack) {
        int index = getIndexForSlot(slot);
        getHandlerFromIndex(index).setStackInSlot(getSlotFromIndex(slot, index), stack);
    }

    @Override
    public int getSlots() {
        return slotCount;
    }

    @NotNull
    @Override
    public ItemStack getStackInSlot(int slot) {
        int index = getIndexForSlot(slot);
        return getHandlerFromIndex(index).getStackInSlot(getSlotFromIndex(slot, index));
    }

    @NotNull
    @Override
    public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        int index = getIndexForSlot(slot);
        return getHandlerFromIndex(index).insertItem(getSlotFromIndex(slot, index), stack, simulate);
    }

    @NotNull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        int index = getIndexForSlot(slot);
        return getHandlerFromIndex(index).extractItem(getSlotFromIndex(slot, index), amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        int index = getIndexForSlot(slot);
        return getHandlerFromIndex(index).getSlotLimit(getSlotFromIndex(slot, index));
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        int index = getIndexForSlot(slot);
        return getHandlerFromIndex(index).isItemValid(getSlotFromIndex(slot, index), stack);
    }
}

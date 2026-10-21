/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.items;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * A menu slot backed by one slot of an {@link IItemHandler}.
 */
public class SlotItemHandler extends Slot {

    private static final Container EMPTY_CONTAINER = new SimpleContainer(0);

    private final IItemHandler itemHandler;
    private final int index;

    public SlotItemHandler(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
        super(EMPTY_CONTAINER, index, xPosition, yPosition);
        this.itemHandler = itemHandler;
        this.index = index;
    }

    public IItemHandler getItemHandler() {
        return itemHandler;
    }

    @Override
    public boolean mayPlace(@NotNull ItemStack stack) {
        return !stack.isEmpty() && itemHandler.isItemValid(index, stack);
    }

    @NotNull
    @Override
    public ItemStack getItem() {
        return itemHandler.getStackInSlot(index);
    }

    @Override
    public void set(@NotNull ItemStack stack) {
        ((IItemHandlerModifiable) itemHandler).setStackInSlot(index, stack);
        setChanged();
    }

    @Override
    public void onQuickCraft(@NotNull ItemStack oldStack, @NotNull ItemStack newStack) {
    }

    @Override
    public int getMaxStackSize() {
        return itemHandler.getSlotLimit(index);
    }

    @Override
    public int getMaxStackSize(@NotNull ItemStack stack) {
        ItemStack probe = stack.copy();
        probe.setCount(stack.getMaxStackSize());
        ItemStack remainder = itemHandler.insertItem(index, probe, true);
        return getItem().getCount() + probe.getCount() - remainder.getCount();
    }

    @Override
    public boolean mayPickup(Player player) {
        return !itemHandler.extractItem(index, 1, true).isEmpty();
    }

    @NotNull
    @Override
    public ItemStack remove(int amount) {
        return itemHandler.extractItem(index, amount, false);
    }
}

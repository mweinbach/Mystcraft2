/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import ports.extremereactors.corelib.item.inventory.handler.IItemHandler;
import ports.extremereactors.corelib.item.inventory.handler.IItemHandlerModifiable;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * Small helpers around item stacks and inventories.
 */
public final class ItemHelper {

    /**
     * An inventory without slots.
     */
    public static final IItemHandlerModifiable EMPTY_ITEM_HANDLER = new IItemHandlerModifiable() {

        @Override
        public void setStackInSlot(final int slot, final ItemStack stack) {
        }

        @Override
        public int getSlots() {
            return 0;
        }

        @Override
        public ItemStack getStackInSlot(final int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(final int slot, final ItemStack stack, final boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(final int slot, final int amount, final boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(final int slot) {
            return 0;
        }

        @Override
        public boolean isItemValid(final int slot, final ItemStack stack) {
            return false;
        }
    };

    //region stacks

    public static ItemStack stackFrom(final ItemLike item) {
        return new ItemStack(item);
    }

    public static ItemStack stackFrom(final ItemLike item, final int amount) {
        return new ItemStack(item, amount);
    }

    public static <T extends ItemLike> ItemStack stackFrom(final Supplier<T> item) {
        return new ItemStack(item.get());
    }

    /**
     * @return a copy of the given stack with a different size, or an empty stack if the size is not positive
     */
    public static ItemStack stackFrom(final ItemStack stack, final int amount) {

        if (stack.isEmpty() || amount <= 0) {
            return ItemStack.EMPTY;
        }

        final ItemStack copy = stack.copy();

        copy.setCount(amount);
        return copy;
    }

    public static boolean stackIsNotEmpty(final ItemStack stack) {
        return !stack.isEmpty();
    }

    public static int stackGetSize(final ItemStack stack) {
        return stack.isEmpty() ? 0 : stack.getCount();
    }

    public static void stackSetSize(final ItemStack stack, final int size) {
        stack.setCount(size);
    }

    //endregion
    //region items

    public static Component getItemName(final ItemStack stack) {
        return stack.getHoverName();
    }

    public static ResourceLocation getItemId(final ItemStack stack) {
        return Registry.ITEM.getKey(stack.getItem());
    }

    /**
     * @return the item with the given id, or null if there is none
     */
    @Nullable
    public static Item getItemFrom(final String id) {

        final ResourceLocation location = ResourceLocation.tryParse(id);

        if (null == location || !Registry.ITEM.containsKey(location)) {
            return null;
        }

        final Item item = Registry.ITEM.get(location);

        return Items.AIR == item ? null : item;
    }

    //endregion
    //region inventories

    /**
     * Drop the whole content of an inventory in the world, emptying it.
     */
    public static void inventoryDropItems(final IItemHandler inventory, final Level world, final BlockPos position) {

        for (int slot = 0; slot < inventory.getSlots(); ++slot) {

            final ItemStack stack = inventory.getStackInSlot(slot);

            if (stack.isEmpty()) {
                continue;
            }

            final ItemStack dropped;

            if (inventory instanceof IItemHandlerModifiable modifiable) {

                dropped = stack.copy();
                modifiable.setStackInSlot(slot, ItemStack.EMPTY);

            } else {

                dropped = inventory.extractItem(slot, stack.getCount(), false);
            }

            Containers.dropItemStack(world, position.getX(), position.getY(), position.getZ(), dropped);
        }
    }

    //endregion

    private ItemHelper() {
    }
}

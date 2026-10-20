/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.item.inventory.container.data;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class ItemStackData
        extends AbstractData<ItemStack> {

    public ItemStackData(final Supplier<ItemStack> source, final Consumer<ItemStack> consumer) {
        super(source, consumer);
    }

    @Override
    protected void write(final FriendlyByteBuf buffer, final ItemStack value) {
        buffer.writeItem(value);
    }

    @Override
    protected ItemStack read(final FriendlyByteBuf buffer) {
        return buffer.readItem();
    }

    @Override
    protected boolean isSame(final ItemStack previous, final ItemStack current) {
        return ItemStack.matches(previous, current);
    }

    @Override
    protected ItemStack copy(final ItemStack value) {
        return value.copy();
    }
}

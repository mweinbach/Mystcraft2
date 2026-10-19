/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.data.stack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import ports.extremereactors.corelib.fluid.FluidStack;

/** {@link IStackAdapter}s for the vanilla-like stack types. */
public final class StackAdapters {

    public static final IStackAdapter<FluidStack, Fluid> FLUIDSTACK = new IStackAdapter<>() {

        @Override
        public Optional<Fluid> getContent(final FluidStack stack) {
            return stack.isEmpty() ? Optional.empty() : Optional.of(stack.getFluid());
        }

        @Override
        public int getAmount(final FluidStack stack) {
            return stack.getAmount();
        }

        @Override
        public FluidStack setAmount(final FluidStack stack, final int amount) {

            stack.setAmount(amount);
            return stack;
        }

        @Override
        public FluidStack modifyAmount(final FluidStack stack, final int delta) {

            stack.grow(delta);
            return stack;
        }

        @Override
        public FluidStack getEmptyStack() {
            return FluidStack.EMPTY;
        }

        @Override
        public boolean isEmpty(final FluidStack stack) {
            return stack.isEmpty();
        }

        @Override
        public boolean isStackContentEqual(final FluidStack stack1, final FluidStack stack2) {
            return stack1.isFluidEqual(stack2);
        }

        @Override
        public boolean isContentEqual(final Fluid content1, final Fluid content2) {
            return content1.isSame(content2);
        }

        @Override
        public boolean areIdentical(final FluidStack stack1, final FluidStack stack2) {
            return stack1.isFluidStackIdentical(stack2);
        }

        @Override
        public FluidStack create(final Fluid content, final int amount) {
            return new FluidStack(content, amount);
        }

        @Override
        public FluidStack create(final FluidStack stack) {
            return stack.isEmpty() ? FluidStack.EMPTY : new FluidStack(stack, stack.getAmount());
        }

        @Override
        public FluidStack[] createArray(final int length) {
            return new FluidStack[length];
        }

        @Override
        public List<FluidStack> createList() {
            return new ArrayList<>();
        }

        @Override
        public Set<FluidStack> createSet() {
            return new HashSet<>();
        }

        @Override
        public FluidStack readFrom(final CompoundTag data) {
            return FluidStack.loadFluidStackFromNBT(data);
        }

        @Override
        public CompoundTag writeTo(final FluidStack stack, final CompoundTag data) {
            return stack.writeToNBT(data);
        }

        @Override
        public String toString(final FluidStack stack) {
            return stack.toString();
        }

        @Override
        public <T> T map(final FluidStack stack, final Function<Fluid, T> mapper, final T defaultValue) {
            return stack.isEmpty() ? defaultValue : mapper.apply(stack.getFluid());
        }

        @Override
        public <T> T map(final FluidStack stack, final IntFunction<T> mapper, final T defaultValue) {
            return stack.isEmpty() ? defaultValue : mapper.apply(stack.getAmount());
        }

        @Override
        public <T> T map(final FluidStack stack, final BiFunction<Fluid, Integer, T> mapper, final T defaultValue) {
            return stack.isEmpty() ? defaultValue : mapper.apply(stack.getFluid(), stack.getAmount());
        }

        @Override
        public void accept(final FluidStack stack, final Consumer<Fluid> consumer) {

            if (!stack.isEmpty()) {
                consumer.accept(stack.getFluid());
            }
        }

        @Override
        public void accept(final FluidStack stack, final IntConsumer consumer) {

            if (!stack.isEmpty()) {
                consumer.accept(stack.getAmount());
            }
        }

        @Override
        public void accept(final FluidStack stack, final BiConsumer<Fluid, Integer> consumer) {

            if (!stack.isEmpty()) {
                consumer.accept(stack.getFluid(), stack.getAmount());
            }
        }
    };

    public static final IStackAdapter<ItemStack, Item> ITEMSTACK = new IStackAdapter<>() {

        @Override
        public Optional<Item> getContent(final ItemStack stack) {
            return stack.isEmpty() ? Optional.empty() : Optional.of(stack.getItem());
        }

        @Override
        public int getAmount(final ItemStack stack) {
            return stack.getCount();
        }

        @Override
        public ItemStack setAmount(final ItemStack stack, final int amount) {

            stack.setCount(amount);
            return stack;
        }

        @Override
        public ItemStack modifyAmount(final ItemStack stack, final int delta) {

            stack.grow(delta);
            return stack;
        }

        @Override
        public ItemStack getEmptyStack() {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean isEmpty(final ItemStack stack) {
            return stack.isEmpty();
        }

        @Override
        public boolean isStackContentEqual(final ItemStack stack1, final ItemStack stack2) {
            return ItemStack.isSameItemSameTags(stack1, stack2);
        }

        @Override
        public boolean isContentEqual(final Item content1, final Item content2) {
            return content1 == content2;
        }

        @Override
        public boolean areIdentical(final ItemStack stack1, final ItemStack stack2) {
            return ItemStack.matches(stack1, stack2);
        }

        @Override
        public ItemStack create(final Item content, final int amount) {
            return new ItemStack(content, amount);
        }

        @Override
        public ItemStack create(final ItemStack stack) {
            return stack.copy();
        }

        @Override
        public ItemStack[] createArray(final int length) {
            return new ItemStack[length];
        }

        @Override
        public List<ItemStack> createList() {
            return new ArrayList<>();
        }

        @Override
        public Set<ItemStack> createSet() {
            return new HashSet<>();
        }

        @Override
        public ItemStack readFrom(final CompoundTag data) {
            return ItemStack.of(data);
        }

        @Override
        public CompoundTag writeTo(final ItemStack stack, final CompoundTag data) {
            return stack.save(data);
        }

        @Override
        public String toString(final ItemStack stack) {
            return stack.toString();
        }

        @Override
        public <T> T map(final ItemStack stack, final Function<Item, T> mapper, final T defaultValue) {
            return stack.isEmpty() ? defaultValue : mapper.apply(stack.getItem());
        }

        @Override
        public <T> T map(final ItemStack stack, final IntFunction<T> mapper, final T defaultValue) {
            return stack.isEmpty() ? defaultValue : mapper.apply(stack.getCount());
        }

        @Override
        public <T> T map(final ItemStack stack, final BiFunction<Item, Integer, T> mapper, final T defaultValue) {
            return stack.isEmpty() ? defaultValue : mapper.apply(stack.getItem(), stack.getCount());
        }

        @Override
        public void accept(final ItemStack stack, final Consumer<Item> consumer) {

            if (!stack.isEmpty()) {
                consumer.accept(stack.getItem());
            }
        }

        @Override
        public void accept(final ItemStack stack, final IntConsumer consumer) {

            if (!stack.isEmpty()) {
                consumer.accept(stack.getCount());
            }
        }

        @Override
        public void accept(final ItemStack stack, final BiConsumer<Item, Integer> consumer) {

            if (!stack.isEmpty()) {
                consumer.accept(stack.getItem(), stack.getCount());
            }
        }
    };

    private StackAdapters() {
    }
}

/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.fluid;

import java.util.Arrays;
import java.util.function.BiPredicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import ports.extremereactors.corelib.data.nbt.ISyncableEntity;
import ports.extremereactors.corelib.data.stack.AbstractStackHolder;
import ports.extremereactors.corelib.fluid.handler.IFluidHandler;

/** A fixed number of fluid tanks sharing the same maximum capacity. */
public class FluidStackHolder extends AbstractStackHolder<FluidStackHolder, FluidStack>
        implements IFluidHandler, ISyncableEntity {

    public FluidStackHolder(final int size) {
        this(size, (slot, stack) -> true);
    }

    public FluidStackHolder(final int size, final BiPredicate<Integer, FluidStack> validator) {

        this._stacks = new FluidStack[size];
        this._validator = validator;
        this._maxCapacity = 1000;
        Arrays.fill(this._stacks, FluidStack.EMPTY);
    }

    public FluidStackHolder setMaxCapacity(final int capacity) {

        this._maxCapacity = Math.max(0, capacity);
        return this;
    }

    public void setStackAt(final int index, final FluidStack stack) {

        final FluidStack old = this._stacks[index];

        this._stacks[index] = stack.isEmpty() ? FluidStack.EMPTY : stack.copy();
        this.onChange(changeTypeOf(old.isEmpty(), this._stacks[index].isEmpty(), old.isFluidEqual(stack)), index);
    }

    //region IStackHolder

    @Override
    public int getSlots() {
        return this._stacks.length;
    }

    @Override
    public FluidStack getStackAt(final int index) {
        return index >= 0 && index < this._stacks.length ? this._stacks[index] : FluidStack.EMPTY;
    }

    @Override
    public int getAmount(final int index) {
        return this.getStackAt(index).getAmount();
    }

    @Override
    public boolean isEmpty(final int index) {
        return this.getStackAt(index).isEmpty();
    }

    //endregion
    //region IFluidHandler

    @Override
    public int getTanks() {
        return this._stacks.length;
    }

    @Override
    public FluidStack getFluidInTank(final int tank) {
        return this.getStackAt(tank);
    }

    @Override
    public int getTankCapacity(final int tank) {
        return this._maxCapacity;
    }

    @Override
    public boolean isFluidValid(final int tank, final FluidStack stack) {
        return tank >= 0 && tank < this._stacks.length && this._validator.test(tank, stack);
    }

    @Override
    public int fill(final FluidStack resource, final FluidAction action) {

        if (resource.isEmpty()) {
            return 0;
        }

        // prefer a tank that already holds the fluid, then the first empty one
        int target = -1;

        for (int tank = 0; tank < this._stacks.length; ++tank) {

            if (!this._stacks[tank].isEmpty() && this._stacks[tank].isFluidEqual(resource) && this.isFluidValid(tank, resource)) {
                target = tank;
                break;
            }
        }

        for (int tank = 0; -1 == target && tank < this._stacks.length; ++tank) {

            if (this._stacks[tank].isEmpty() && this.isFluidValid(tank, resource)) {
                target = tank;
            }
        }

        if (-1 == target) {
            return 0;
        }

        final FluidStack current = this._stacks[target];
        final int filled = Math.min(resource.getAmount(), this._maxCapacity - current.getAmount());

        if (filled > 0 && action.execute()) {

            if (current.isEmpty()) {
                this._stacks[target] = new FluidStack(resource, filled);
                this.onChange(ChangeType.Added, target);
            } else {
                current.grow(filled);
                this.onChange(ChangeType.Modified, target);
            }
        }

        return Math.max(0, filled);
    }

    @Override
    public FluidStack drain(final FluidStack resource, final FluidAction action) {

        if (resource.isEmpty()) {
            return FluidStack.EMPTY;
        }

        for (int tank = 0; tank < this._stacks.length; ++tank) {
            if (this._stacks[tank].isFluidEqual(resource)) {
                return this.drainTank(tank, resource.getAmount(), action);
            }
        }

        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(final int maxDrain, final FluidAction action) {

        for (int tank = 0; tank < this._stacks.length; ++tank) {
            if (!this._stacks[tank].isEmpty()) {
                return this.drainTank(tank, maxDrain, action);
            }
        }

        return FluidStack.EMPTY;
    }

    //endregion
    //region ISyncableEntity

    @Override
    public void syncDataFrom(final CompoundTag data, final SyncReason syncReason) {

        Arrays.fill(this._stacks, FluidStack.EMPTY);

        if (data.contains("capacity")) {
            this._maxCapacity = data.getInt("capacity");
        }

        final ListTag list = data.getList("stacks", Tag.TAG_COMPOUND);

        for (int i = 0; i < list.size(); ++i) {

            final CompoundTag entry = list.getCompound(i);
            final int slot = entry.getInt("slot");

            if (slot >= 0 && slot < this._stacks.length) {
                this._stacks[slot] = FluidStack.loadFluidStackFromNBT(entry);
            }
        }

        this.onLoad();
    }

    @Override
    public CompoundTag syncDataTo(final CompoundTag data, final SyncReason syncReason) {

        final ListTag list = new ListTag();

        for (int slot = 0; slot < this._stacks.length; ++slot) {

            if (!this._stacks[slot].isEmpty()) {

                final CompoundTag entry = this._stacks[slot].writeToNBT(new CompoundTag());

                entry.putInt("slot", slot);
                list.add(entry);
            }
        }

        data.putInt("capacity", this._maxCapacity);
        data.put("stacks", list);
        return data;
    }

    //endregion

    private FluidStack drainTank(final int tank, final int maxDrain, final FluidAction action) {

        final FluidStack current = this._stacks[tank];
        final int drained = Math.min(maxDrain, current.getAmount());

        if (drained <= 0) {
            return FluidStack.EMPTY;
        }

        final FluidStack result = new FluidStack(current, drained);

        if (action.execute()) {

            current.shrink(drained);

            if (current.isEmpty()) {
                this._stacks[tank] = FluidStack.EMPTY;
                this.onChange(ChangeType.Removed, tank);
            } else {
                this.onChange(ChangeType.Modified, tank);
            }
        }

        return result;
    }

    private final FluidStack[] _stacks;
    private final BiPredicate<Integer, FluidStack> _validator;
    private int _maxCapacity;
}

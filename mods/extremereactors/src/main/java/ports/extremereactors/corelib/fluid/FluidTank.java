/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.fluid;

import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import ports.extremereactors.corelib.IDebugMessages;
import ports.extremereactors.corelib.IDebuggable;
import ports.extremereactors.corelib.LogicalSide;
import ports.extremereactors.corelib.data.nbt.ISyncableEntity;
import ports.extremereactors.corelib.data.stack.AbstractStackHolder;
import ports.extremereactors.corelib.fluid.handler.IFluidHandler;

/** A single fluid tank. */
public class FluidTank extends AbstractStackHolder<FluidTank, FluidStack>
        implements IFluidHandler, ISyncableEntity, IDebuggable {

    public FluidTank(final int capacity) {
        this(capacity, stack -> true);
    }

    public FluidTank(final int capacity, final Predicate<FluidStack> validator) {

        this._capacity = Math.max(0, capacity);
        this._validator = validator;
        this._fluid = FluidStack.EMPTY;
    }

    public FluidTank setValidator(final Predicate<FluidStack> validator) {

        this._validator = validator;
        return this;
    }

    public FluidStack getFluid() {
        return this._fluid;
    }

    public int getFluidAmount() {
        return this._fluid.getAmount();
    }

    /** The filled fraction, from 0 to 1. */
    public double getFluidAmountPercentage() {
        return this._capacity <= 0 ? 0.0 : Math.min(1.0, (double) this.getFluidAmount() / this._capacity);
    }

    public int getCapacity() {
        return this._capacity;
    }

    public FluidTank setCapacity(final int capacity) {

        this._capacity = Math.max(0, capacity);
        return this;
    }

    public int getSpace() {
        return Math.max(0, this._capacity - this.getFluidAmount());
    }

    public boolean isFluidValid(final FluidStack stack) {
        return this._validator.test(stack);
    }

    /** Replaces the contents without any capacity or validity check. */
    public void setContent(final FluidStack stack) {

        final FluidStack old = this._fluid;

        this._fluid = stack.isEmpty() ? FluidStack.EMPTY : stack.copy();

        if (!old.isFluidStackIdentical(this._fluid)) {
            this.onChange(changeTypeOf(old.isEmpty(), this._fluid.isEmpty(), old.isFluidEqual(this._fluid)), 0);
        }
    }

    //region IStackHolder

    @Override
    public int getSlots() {
        return 1;
    }

    @Override
    public FluidStack getStackAt(final int index) {
        return 0 == index ? this._fluid : FluidStack.EMPTY;
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
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(final int tank) {
        return this.getStackAt(tank);
    }

    @Override
    public int getTankCapacity(final int tank) {
        return 0 == tank ? this._capacity : 0;
    }

    @Override
    public boolean isFluidValid(final int tank, final FluidStack stack) {
        return 0 == tank && this.isFluidValid(stack);
    }

    @Override
    public int fill(final FluidStack resource, final FluidAction action) {

        if (resource.isEmpty() || !this.isFluidValid(resource)) {
            return 0;
        }

        final boolean wasEmpty = this._fluid.isEmpty();

        if (!wasEmpty && !this._fluid.isFluidEqual(resource)) {
            return 0;
        }

        final int filled = Math.min(this.getSpace(), resource.getAmount());

        if (filled > 0 && action.execute()) {

            if (wasEmpty) {
                this._fluid = new FluidStack(resource, filled);
            } else {
                this._fluid.grow(filled);
            }

            this.onChange(wasEmpty ? ChangeType.Added : ChangeType.Modified, 0);
        }

        return filled;
    }

    @Override
    public FluidStack drain(final FluidStack resource, final FluidAction action) {
        return resource.isEmpty() || !resource.isFluidEqual(this._fluid) ? FluidStack.EMPTY : this.drain(resource.getAmount(), action);
    }

    @Override
    public FluidStack drain(final int maxDrain, final FluidAction action) {

        if (maxDrain <= 0 || this._fluid.isEmpty()) {
            return FluidStack.EMPTY;
        }

        final int drained = Math.min(maxDrain, this._fluid.getAmount());
        final FluidStack result = new FluidStack(this._fluid, drained);

        if (action.execute()) {

            this._fluid.shrink(drained);

            if (this._fluid.isEmpty()) {
                this._fluid = FluidStack.EMPTY;
            }

            this.onChange(this._fluid.isEmpty() ? ChangeType.Removed : ChangeType.Modified, 0);
        }

        return result;
    }

    //endregion
    //region ISyncableEntity

    @Override
    public void syncDataFrom(final CompoundTag data, final SyncReason syncReason) {

        if (data.contains("capacity")) {
            this._capacity = Math.max(0, data.getInt("capacity"));
        }

        this._fluid = data.contains("fluid") ? FluidStack.loadFluidStackFromNBT(data.getCompound("fluid")) : FluidStack.EMPTY;
        this.onLoad();
    }

    @Override
    public CompoundTag syncDataTo(final CompoundTag data, final SyncReason syncReason) {

        data.putInt("capacity", this._capacity);

        if (!this._fluid.isEmpty()) {
            data.put("fluid", this._fluid.writeToNBT(new CompoundTag()));
        }

        return data;
    }

    //endregion
    //region IDebuggable

    @Override
    public void getDebugMessages(final LogicalSide side, final IDebugMessages messages) {
        messages.addUnlocalized("Tank: %s / %d mB", this._fluid, this._capacity);
    }

    //endregion

    private int _capacity;
    private Predicate<FluidStack> _validator;
    private FluidStack _fluid;
}

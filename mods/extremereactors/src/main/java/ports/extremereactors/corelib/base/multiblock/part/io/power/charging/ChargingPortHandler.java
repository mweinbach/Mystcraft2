/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.base.multiblock.part.io.power.charging;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import ports.extremereactors.corelib.base.multiblock.part.AbstractMultiblockEntity;
import ports.extremereactors.corelib.base.multiblock.part.io.AbstractIOPortHandler;
import ports.extremereactors.corelib.capability.Capability;
import ports.extremereactors.corelib.capability.CapabilityHelper;
import ports.extremereactors.corelib.capability.LazyOptional;
import ports.extremereactors.corelib.data.IoDirection;
import ports.extremereactors.corelib.data.IoMode;
import ports.extremereactors.corelib.data.WideAmount;
import ports.extremereactors.corelib.energy.EnergySystem;
import ports.extremereactors.corelib.energy.IEnergyStorage;
import ports.extremereactors.corelib.energy.IWideEnergyStorage2;
import ports.extremereactors.corelib.item.inventory.handler.IItemHandler;
import ports.extremereactors.corelib.item.inventory.handler.IItemHandlerModifiable;
import ports.extremereactors.corelib.item.inventory.handler.ItemHandlerHelper;
import ports.extremereactors.corelib.item.inventory.handler.TileEntityItemStackHandler;
import ports.extremereactors.multiblock.cuboid.AbstractCuboidMultiblockController;
import ports.extremereactors.platform.Platform;

import javax.annotation.Nullable;

/**
 * Charges the items in the input slots with the energy the machine offers to the port.
 *
 * <p>The port also exposes an item handler on every side so automation can insert into the input slots and
 * extract from the output slots.</p>
 */
class ChargingPortHandler<Controller extends AbstractCuboidMultiblockController<Controller> & IWideEnergyStorage2,
                          T extends AbstractMultiblockEntity<Controller> & IChargingPort>
        extends AbstractIOPortHandler<Controller, T>
        implements IChargingPortHandler {

    ChargingPortHandler(final EnergySystem system, final T part, final int inputSlotsCount, final int outputSlotsCount) {

        super(part, IoMode.Active);
        this._system = system;
        this._input = new TileEntityItemStackHandler(part, inputSlotsCount);
        this._output = new TileEntityItemStackHandler(part, outputSlotsCount);
        this._automation = null;
    }

    //region IChargingPortHandler

    @Override
    public IItemHandlerModifiable getItemStackHandler(final IoDirection direction) {
        return direction.isInput() ? this._input : this._output;
    }

    @Override
    public void eject() {

        for (int slot = 0; slot < this._input.getSlots(); ++slot) {
            this.moveToOutput(slot);
        }
    }

    //endregion
    //region IPowerPortHandler

    @Override
    public EnergySystem getEnergySystem() {
        return this._system;
    }

    @Override
    public WideAmount outputEnergy(final WideAmount amount) {

        if (amount.isZero()) {
            return WideAmount.ZERO;
        }

        final int slots = this._input.getSlots();
        int available = (int) Math.min(Integer.MAX_VALUE, this._system.convertTo(EnergySystem.ForgeEnergy, amount.doubleValue()));
        int accepted = 0;

        for (int slot = 0; slot < slots && available > 0; ++slot) {

            final IEnergyStorage item = Platform.backend().itemEnergy(this._input, slot);

            if (null == item || !item.canReceive()) {
                continue;
            }

            final int received = item.receiveEnergy(available, false);

            available -= received;
            accepted += received;

            if (item.getEnergyStored() >= item.getMaxEnergyStored() || 0 == item.receiveEnergy(1, true)) {
                this.moveToOutput(slot);
            }
        }

        return accepted > 0 ? WideAmount.from(EnergySystem.ForgeEnergy.convertTo(this._system, accepted)) : WideAmount.ZERO;
    }

    //endregion
    //region IIOPortHandler

    /**
     * @return true if an item in the input slots can still be charged
     */
    @Override
    public boolean isConnected() {

        for (int slot = 0; slot < this._input.getSlots(); ++slot) {

            if (!this._input.getStackInSlot(slot).isEmpty()) {

                final IEnergyStorage item = Platform.backend().itemEnergy(this._input, slot);

                if (null != item && item.canReceive()) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public void checkConnections(@Nullable final Level world, final BlockPos position) {
    }

    @Override
    public void invalidate() {

        if (null != this._automation) {

            this._automation.invalidate();
            this._automation = null;
        }
    }

    @Nullable
    @Override
    public <C> LazyOptional<C> getCapability(final Capability<C> capability, @Nullable final Direction direction) {

        if (CapabilityHelper.ITEM_HANDLER != capability) {
            return null;
        }

        if (null == this._automation) {
            this._automation = LazyOptional.of(() -> new AutomationHandler(this._input, this._output));
        }

        return this._automation.cast();
    }

    //endregion
    //region ISyncableEntity

    @Override
    public void syncDataFrom(final CompoundTag data, final SyncReason syncReason) {

        this.syncDataElementFrom(KEY_INPUT, data, this._input);
        this.syncDataElementFrom(KEY_OUTPUT, data, this._output);
    }

    @Override
    public CompoundTag syncDataTo(final CompoundTag data, final SyncReason syncReason) {

        this.syncDataElementTo(KEY_INPUT, data, this._input);
        this.syncDataElementTo(KEY_OUTPUT, data, this._output);
        return data;
    }

    //endregion
    //region internals

    private void moveToOutput(final int inputSlot) {

        final ItemStack stack = this._input.getStackInSlot(inputSlot);

        if (stack.isEmpty()) {
            return;
        }

        final ItemStack remainder = ItemHandlerHelper.insertItem(this._output, stack.copy(), false);

        if (remainder.getCount() != stack.getCount()) {
            this._input.setStackInSlot(inputSlot, remainder);
        }
    }

    /**
     * Input slots first, then output slots. Only the input slots accept items and only the output slots give them.
     */
    private record AutomationHandler(IItemHandlerModifiable input, IItemHandlerModifiable output)
            implements IItemHandler {

        @Override
        public int getSlots() {
            return this.input.getSlots() + this.output.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(final int slot) {
            return slot < this.input.getSlots() ? this.input.getStackInSlot(slot) :
                    this.output.getStackInSlot(slot - this.input.getSlots());
        }

        @Override
        public ItemStack insertItem(final int slot, final ItemStack stack, final boolean simulate) {
            return slot < this.input.getSlots() ? this.input.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(final int slot, final int amount, final boolean simulate) {
            return slot < this.input.getSlots() ? ItemStack.EMPTY :
                    this.output.extractItem(slot - this.input.getSlots(), amount, simulate);
        }

        @Override
        public int getSlotLimit(final int slot) {
            return slot < this.input.getSlots() ? this.input.getSlotLimit(slot) :
                    this.output.getSlotLimit(slot - this.input.getSlots());
        }

        @Override
        public boolean isItemValid(final int slot, final ItemStack stack) {
            return slot < this.input.getSlots() && this.input.isItemValid(slot, stack);
        }
    }

    private static final String KEY_INPUT = "chargingInput";
    private static final String KEY_OUTPUT = "chargingOutput";

    private final EnergySystem _system;
    private final TileEntityItemStackHandler _input;
    private final TileEntityItemStackHandler _output;
    @Nullable
    private LazyOptional<IItemHandler> _automation;

    //endregion
}

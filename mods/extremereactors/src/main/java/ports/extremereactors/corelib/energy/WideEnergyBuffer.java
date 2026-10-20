/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.energy;

import net.minecraft.nbt.CompoundTag;
import ports.extremereactors.corelib.IDebugMessages;
import ports.extremereactors.corelib.IDebuggable;
import ports.extremereactors.corelib.LogicalSide;
import ports.extremereactors.corelib.data.WideAmount;
import ports.extremereactors.corelib.data.nbt.ISyncableEntity;
import ports.extremereactors.corelib.data.stack.OperationMode;

/** A {@link WideAmount} energy store with per-operation insert and extract limits. */
public class WideEnergyBuffer implements IWideEnergyStorage2, ISyncableEntity, IDebuggable {

    public WideEnergyBuffer(final EnergySystem system, final WideAmount capacity) {
        this(system, capacity, WideAmount.MAX_VALUE, WideAmount.MAX_VALUE);
    }

    public WideEnergyBuffer(final EnergySystem system, final WideAmount capacity, final WideAmount maxTransfer) {
        this(system, capacity, maxTransfer, maxTransfer);
    }

    public WideEnergyBuffer(final EnergySystem system, final WideAmount capacity, final WideAmount maxInsert,
                            final WideAmount maxExtract) {

        this._system = system;
        this._capacity = nonNegative(capacity);
        this._maxInsert = nonNegative(maxInsert);
        this._maxExtract = nonNegative(maxExtract);
        this._stored = WideAmount.ZERO;
    }

    public WideAmount getEnergyStored() {
        return this._stored;
    }

    public WideAmount getCapacity() {
        return this._capacity;
    }

    public double getEnergyStoredPercentage() {
        return this._stored.percentage(this._capacity);
    }

    public void setCapacity(final WideAmount capacity) {

        this._capacity = nonNegative(capacity);
        this._stored = WideAmount.min(this._stored, this._capacity);
    }

    public void setMaxInsert(final WideAmount maxInsert) {
        this._maxInsert = nonNegative(maxInsert);
    }

    public void setMaxExtract(final WideAmount maxExtract) {
        this._maxExtract = nonNegative(maxExtract);
    }

    public void setEnergyStored(final WideAmount amount, final EnergySystem system) {
        this.setStored(WideAmount.from(system.convertTo(this._system, amount.doubleValue())));
    }

    /** Adds energy, ignoring the transfer limits; anything above the capacity is lost. */
    public void grow(final double amount) {
        this.setStored(this._stored.add(amount));
    }

    public void grow(final WideAmount amount) {
        this.setStored(this._stored.add(amount));
    }

    public void shrink(final double amount) {
        this.setStored(this._stored.subtract(amount));
    }

    public void shrink(final WideAmount amount) {
        this.setStored(this._stored.subtract(amount));
    }

    public void empty() {
        this._stored = WideAmount.ZERO;
    }

    //region IWideEnergyStorage2

    @Override
    public EnergySystem getEnergySystem() {
        return this._system;
    }

    @Override
    public WideAmount insertEnergy(final EnergySystem system, final WideAmount maxAmount, final OperationMode mode) {

        final WideAmount requested = WideAmount.from(system.convertTo(this._system, maxAmount.doubleValue()));
        final WideAmount accepted = nonNegative(WideAmount.min(WideAmount.min(requested, this._maxInsert),
                this._capacity.subtract(this._stored)));

        if (mode == OperationMode.Execute) {
            this._stored = this._stored.add(accepted);
        }

        return WideAmount.from(this._system.convertTo(system, accepted.doubleValue()));
    }

    @Override
    public WideAmount extractEnergy(final EnergySystem system, final WideAmount maxAmount, final OperationMode mode) {

        final WideAmount requested = WideAmount.from(system.convertTo(this._system, maxAmount.doubleValue()));
        final WideAmount extracted = nonNegative(WideAmount.min(WideAmount.min(requested, this._maxExtract), this._stored));

        if (mode == OperationMode.Execute) {
            this._stored = this._stored.subtract(extracted);
        }

        return WideAmount.from(this._system.convertTo(system, extracted.doubleValue()));
    }

    @Override
    public WideAmount getEnergyStored(final EnergySystem system) {
        return WideAmount.from(this._system.convertTo(system, this._stored.doubleValue()));
    }

    @Override
    public WideAmount getCapacity(final EnergySystem system) {
        return WideAmount.from(this._system.convertTo(system, this._capacity.doubleValue()));
    }

    //endregion
    //region ISyncableEntity

    @Override
    public void syncDataFrom(final CompoundTag data, final SyncReason syncReason) {

        if (data.contains("capacity")) {
            this._capacity = nonNegative(WideAmount.from(data, "capacity"));
        }

        this._stored = nonNegative(WideAmount.from(data, "stored"));
    }

    @Override
    public CompoundTag syncDataTo(final CompoundTag data, final SyncReason syncReason) {

        this._capacity.serializeTo(data, "capacity");
        this._stored.serializeTo(data, "stored");
        return data;
    }

    //endregion
    //region IDebuggable

    @Override
    public void getDebugMessages(final LogicalSide side, final IDebugMessages messages) {
        messages.addUnlocalized("Stored: %s / %s %s (in %s, out %s)", this._stored, this._capacity,
                this._system.getUnit(), this._maxInsert, this._maxExtract);
    }

    //endregion

    private void setStored(final WideAmount amount) {
        this._stored = nonNegative(WideAmount.min(amount, this._capacity));
    }

    private static WideAmount nonNegative(final WideAmount amount) {
        return amount.isPositive() ? amount : WideAmount.ZERO;
    }

    private final EnergySystem _system;
    private WideAmount _capacity;
    private WideAmount _maxInsert;
    private WideAmount _maxExtract;
    private WideAmount _stored;
}

/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.energy;

import net.minecraft.nbt.CompoundTag;
import ports.extremereactors.corelib.IDebugMessages;
import ports.extremereactors.corelib.IDebuggable;
import ports.extremereactors.corelib.LogicalSide;
import ports.extremereactors.corelib.data.nbt.ISyncableEntity;

/** A floating point energy store with per-operation insert and extract limits. */
public class EnergyBuffer implements IWideEnergyStorage, ISyncableEntity, IDebuggable {

    public EnergyBuffer(final EnergySystem system, final double capacity) {
        this(system, capacity, Double.MAX_VALUE);
    }

    public EnergyBuffer(final EnergySystem system, final double capacity, final double maxTransfer) {
        this(system, capacity, maxTransfer, maxTransfer);
    }

    public EnergyBuffer(final EnergySystem system, final double capacity, final double maxInsert, final double maxExtract) {

        this._system = system;
        this._capacity = Math.max(0, capacity);
        this._maxInsert = Math.max(0, maxInsert);
        this._maxExtract = Math.max(0, maxExtract);
    }

    public double getEnergyStored() {
        return this._stored;
    }

    public double getCapacity() {
        return this._capacity;
    }

    public double getEnergyStoredPercentage() {
        return this._capacity <= 0 ? 0.0 : Math.min(1.0, this._stored / this._capacity);
    }

    public void setCapacity(final double capacity) {

        this._capacity = Math.max(0, capacity);
        this._stored = Math.min(this._stored, this._capacity);
    }

    public void setMaxInsert(final double maxInsert) {
        this._maxInsert = Math.max(0, maxInsert);
    }

    public void setMaxExtract(final double maxExtract) {
        this._maxExtract = Math.max(0, maxExtract);
    }

    public void setEnergyStored(final double amount) {
        this._stored = Math.max(0, Math.min(this._capacity, amount));
    }

    /** Adds energy, ignoring the transfer limits; anything above the capacity is lost. */
    public void grow(final double amount) {
        this.setEnergyStored(this._stored + amount);
    }

    public void shrink(final double amount) {
        this.setEnergyStored(this._stored - amount);
    }

    public void empty() {
        this._stored = 0;
    }

    //region IWideEnergyStorage

    @Override
    public EnergySystem getEnergySystem() {
        return this._system;
    }

    @Override
    public double insertEnergy(final EnergySystem system, final double maxAmount, final boolean simulate) {

        final double requested = system.convertTo(this._system, maxAmount);
        final double accepted = Math.max(0, Math.min(Math.min(requested, this._maxInsert), this._capacity - this._stored));

        if (!simulate) {
            this._stored += accepted;
        }

        return this._system.convertTo(system, accepted);
    }

    @Override
    public double extractEnergy(final EnergySystem system, final double maxAmount, final boolean simulate) {

        final double requested = system.convertTo(this._system, maxAmount);
        final double extracted = Math.max(0, Math.min(Math.min(requested, this._maxExtract), this._stored));

        if (!simulate) {
            this._stored -= extracted;
        }

        return this._system.convertTo(system, extracted);
    }

    @Override
    public double getEnergyStored(final EnergySystem system) {
        return this._system.convertTo(system, this._stored);
    }

    @Override
    public double getCapacity(final EnergySystem system) {
        return this._system.convertTo(system, this._capacity);
    }

    //endregion
    //region ISyncableEntity

    @Override
    public void syncDataFrom(final CompoundTag data, final SyncReason syncReason) {

        if (data.contains("capacity")) {
            this._capacity = data.getDouble("capacity");
        }

        this._stored = Math.max(0, data.getDouble("stored"));
    }

    @Override
    public CompoundTag syncDataTo(final CompoundTag data, final SyncReason syncReason) {

        data.putDouble("capacity", this._capacity);
        data.putDouble("stored", this._stored);
        return data;
    }

    //endregion
    //region IDebuggable

    @Override
    public void getDebugMessages(final LogicalSide side, final IDebugMessages messages) {
        messages.addUnlocalized("Stored: %.2f / %.2f %s (in %.2f, out %.2f)", this._stored, this._capacity,
                this._system.getUnit(), this._maxInsert, this._maxExtract);
    }

    //endregion

    private final EnergySystem _system;
    private double _capacity;
    private double _maxInsert;
    private double _maxExtract;
    private double _stored;
}

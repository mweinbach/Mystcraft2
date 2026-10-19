package com.technovision.advancedgenetics.api.blockentity;

/**
 * A machine's energy buffer, matching upstream's {@code SimpleEnergyStorage(capacity, capacity, capacity)}:
 * a single transfer can fill or drain the whole buffer. Each loader exposes it through its energy API.
 */
public final class MachineEnergy {

    private final int capacity;
    private final Runnable onChanged;
    private int energy;

    public MachineEnergy(int capacity, Runnable onChanged) {
        this.capacity = Math.max(0, capacity);
        this.onChanged = onChanged;
    }

    public int getEnergyStored() {
        return energy;
    }

    public int getMaxEnergyStored() {
        return capacity;
    }

    public int receiveEnergy(int maxReceive, boolean simulate) {
        int received = Math.max(0, Math.min(capacity - energy, maxReceive));
        if (received > 0 && !simulate) {
            energy += received;
            onChanged.run();
        }
        return received;
    }

    public int extractEnergy(int maxExtract, boolean simulate) {
        int extracted = Math.max(0, Math.min(energy, maxExtract));
        if (extracted > 0 && !simulate) {
            energy -= extracted;
            onChanged.run();
        }
        return extracted;
    }

    /** Sets the stored energy, clamped to the buffer, and marks the machine changed. */
    public void setEnergy(int energy) {
        restore(energy);
        onChanged.run();
    }

    /** Sets the stored energy without marking the machine changed, for transaction rollbacks. */
    public void restore(int energy) {
        this.energy = Math.max(0, Math.min(energy, capacity));
    }
}

package ports.buildcraft.lib;

import net.minecraft.nbt.CompoundTag;
import ports.buildcraft.platform.EnergyPort;

/**
 * Upstream {@code MjBattery} in micro-MJ, exposed to the loader energy API in whole units. Upstream
 * accepted power until full and bled any overflow; loader energy APIs expect a capped receiver, so
 * {@link #receive} accepts at most the free space.
 */
public final class MjBattery {
    private final long capacity;
    private long stored;

    public MjBattery(long capacity) {
        this.capacity = capacity;
    }

    public long capacity() { return capacity; }
    public long stored() { return stored; }
    public boolean isFull() { return stored >= capacity; }

    public long space() {
        return Math.max(0, capacity - stored);
    }

    public void addPower(long microJoules) {
        stored += microJoules;
    }

    public long extractPower(long min, long max) {
        if (stored < min) return 0;
        long extracting = Math.min(stored, max);
        stored -= extracting;
        return extracting;
    }

    /** Upstream {@code tick}: bleeds power stored beyond twice the capacity. */
    public void tick() {
        if (stored > capacity * 2) {
            long diff = stored - capacity * 2;
            stored -= (diff + 31) / 32;
        }
    }

    public void set(long microJoules) {
        stored = microJoules;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("stored", stored);
        return tag;
    }

    public void load(CompoundTag tag) {
        stored = tag.getLong("stored");
    }

    /** A receive-only energy face for this battery. */
    public EnergyPort receiver(boolean acceptsRedstoneEngine, Runnable onChange) {
        return new EnergyPort() {
            @Override
            public long receive(long amount, boolean simulate) {
                if (amount <= 0 || isFull()) return 0;
                long accepted = Math.min(amount, Power.toUnits(space()));
                if (!simulate && accepted > 0) {
                    addPower(Power.fromUnits(accepted));
                    onChange.run();
                }
                return accepted;
            }

            @Override public long extract(long amount, boolean simulate) { return 0; }
            @Override public long stored() { return Power.toUnits(stored); }
            @Override public long capacity() { return Power.toUnits(capacity); }
            @Override public boolean canReceive() { return true; }
            @Override public boolean canExtract() { return false; }
            @Override public boolean acceptsRedstoneEngine() { return acceptsRedstoneEngine; }
            @Override public Object snapshot() { return stored; }
            @Override public void restore(Object snapshot) { stored = (Long) snapshot; }
            @Override public void onCommitted() { onChange.run(); }
        };
    }
}

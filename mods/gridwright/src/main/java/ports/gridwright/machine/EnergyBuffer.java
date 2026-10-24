package ports.gridwright.machine;

/**
 * A generator's energy store, in the units both loaders' energy APIs use (Forge Energy and Team
 * Reborn Energy are both counted 1:1 here). Other mods may only extract from it.
 */
public final class EnergyBuffer {

    private final long capacity;
    private final long maxExtract;
    private long stored;
    private Runnable onChange = () -> { };

    public EnergyBuffer(long capacity, long maxExtract) {
        this.capacity = capacity;
        this.maxExtract = maxExtract;
    }

    void onChange(Runnable listener) {
        this.onChange = listener;
    }

    public long stored() {
        return stored;
    }

    public long capacity() {
        return capacity;
    }

    public long maxExtract() {
        return maxExtract;
    }

    public long space() {
        return capacity - stored;
    }

    /** Adds generated energy, up to capacity; returns how much was added. */
    long fill(long amount) {
        long added = Math.max(0, Math.min(amount, space()));
        if (added > 0) {
            stored += added;
            onChange.run();
        }
        return added;
    }

    /** Takes up to {@code amount}, at most {@link #maxExtract()} per call; returns how much was (or would be) taken. */
    public long extract(long amount, boolean simulate) {
        long taken = Math.max(0, Math.min(Math.min(amount, maxExtract), stored));
        if (!simulate && taken > 0) {
            stored -= taken;
            onChange.run();
        }
        return taken;
    }

    /** Restores a saved or rolled-back amount (Fabric transactions use this). */
    public void restore(long amount) {
        stored = Math.max(0, Math.min(capacity, amount));
    }

    /** Marks the owner changed after a committed external change. */
    public void markChanged() {
        onChange.run();
    }
}

package ports.gridwright.machine;

/**
 * A machine's energy store, in the units both loaders' energy APIs use (Forge Energy and Team Reborn
 * Energy are both counted 1:1 here). Generators let other mods extract only; consumers such as the
 * ender quarry let other mods insert only. A limit of 0 closes that direction.
 */
public final class EnergyBuffer {

    private final long capacity;
    private final long maxExtract;
    private final long maxReceive;
    private long stored;
    private Runnable onChange = () -> { };

    /** An extract-only store (a generator's). */
    public EnergyBuffer(long capacity, long maxExtract) {
        this(capacity, maxExtract, 0);
    }

    public EnergyBuffer(long capacity, long maxExtract, long maxReceive) {
        this.capacity = capacity;
        this.maxExtract = maxExtract;
        this.maxReceive = maxReceive;
    }

    public void onChange(Runnable listener) {
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

    public long maxReceive() {
        return maxReceive;
    }

    public long space() {
        return capacity - stored;
    }

    /** Adds generated energy, up to capacity; returns how much was added. */
    public long fill(long amount) {
        long added = Math.max(0, Math.min(amount, space()));
        if (added > 0) {
            stored += added;
            onChange.run();
        }
        return added;
    }

    /** Spends {@code amount} if all of it is stored; returns whether it was spent. */
    public boolean consume(long amount) {
        if (amount < 0 || amount > stored) return false;
        if (amount > 0) {
            stored -= amount;
            onChange.run();
        }
        return true;
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

    /** Accepts up to {@code amount} from another mod, at most {@link #maxReceive()} per call; returns how much was (or would be) accepted. */
    public long receive(long amount, boolean simulate) {
        long accepted = Math.max(0, Math.min(Math.min(amount, maxReceive), space()));
        if (!simulate && accepted > 0) {
            stored += accepted;
            onChange.run();
        }
        return accepted;
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

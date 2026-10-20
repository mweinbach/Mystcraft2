package ports.buildcraft.builders.snapshot;

/** Upstream {@code EnumSnapshotType}, with how many blocks the architect table scans per tick for each. */
public enum EnumSnapshotType {
    TEMPLATE(900),
    BLUEPRINT(300);

    public final int maxPerTick;

    EnumSnapshotType(int maxPerTick) {
        this.maxPerTick = maxPerTick;
    }
}

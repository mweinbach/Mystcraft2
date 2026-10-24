package ports.gridwright.transfer;

import java.util.Locale;

/**
 * Node upgrades. A node works at its base rate for free; upgrades make it faster, move whole stacks or
 * let it act on the world, and each installed upgrade asks the owner's grid for Grid Power. While that
 * grid is short of power the node falls back to its base behaviour. The numbers are Gridwright's own.
 */
public enum UpgradeKind {
    /** One more operation per second each, up to 64 counted. */
    SPEED(0.25, 64),
    /** A full stack (or a bucket of fluid) per operation instead of one item (or 100 mB). */
    STACK(2.0, 1),
    /** Mines, places, pumps or pours at the node's front when no inventory or tank is there. */
    WORLD_INTERACTION(4.0, 1);

    private final double gp;
    private final int maxCounted;

    UpgradeKind(double gp, int maxCounted) {
        this.gp = gp;
        this.maxCounted = maxCounted;
    }

    /** Grid Power asked for each counted upgrade. */
    public double gp() {
        return gp;
    }

    /** How many of this upgrade a node counts; more do nothing and cost nothing. */
    public int maxCounted() {
        return maxCounted;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT) + "_upgrade";
    }
}

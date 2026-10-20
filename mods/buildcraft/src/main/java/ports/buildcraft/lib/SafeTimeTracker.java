package ports.buildcraft.lib;

import net.minecraft.world.level.Level;

/**
 * Upstream {@code SafeTimeTracker}: fires once {@code delay} ticks of game time have passed since it last fired,
 * plus a random extra of up to {@code random} ticks chosen each time it fires, so that many machines placed
 * together do not all act on the same tick.
 */
public final class SafeTimeTracker {
    private final long delay;
    private final long random;
    private long lastMark = Long.MIN_VALUE;
    private long lastRandomDelay;

    public SafeTimeTracker(long delay, long random) {
        this.delay = delay;
        this.random = random;
    }

    public boolean markTimeIfDelay(Level level) {
        long now = level.getGameTime();
        if (now < lastMark) {
            lastMark = now;
            return false;
        }
        if (lastMark + delay + lastRandomDelay <= now) {
            lastMark = now;
            lastRandomDelay = (int) (Math.random() * random);
            return true;
        }
        return false;
    }
}

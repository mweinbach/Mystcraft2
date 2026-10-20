package ports.buildcraft.builders;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Upstream {@code ClientArchitectTables}: the boxes architect tables are scanning, and the blocks they scanned
 * recently, each with how many more client ticks to show it. Entries keep the game time they run out at
 * instead of upstream's per-tick countdown, and belong to the level they were added in.
 */
public final class ClientArchitectTables {
    public static final int START_BOX_VALUE = 3;
    public static final int START_SCANNED_BLOCK_VALUE = 50;
    public static final Map<AABB, Long> BOXES = new ConcurrentHashMap<>();
    public static final Map<BlockPos, Long> SCANNED_BLOCKS = new ConcurrentHashMap<>();
    private static WeakReference<Level> current = new WeakReference<>(null);

    private ClientArchitectTables() {}

    public static void putBox(Level level, AABB box) {
        switchTo(level);
        BOXES.put(box, level.getGameTime() + START_BOX_VALUE);
    }

    public static void putScannedBlock(Level level, BlockPos pos) {
        switchTo(level);
        SCANNED_BLOCKS.put(pos.immutable(), level.getGameTime() + START_SCANNED_BLOCK_VALUE);
    }

    /**
     * Drops entries that ran out. The server's time updates can set the client's clock back a little, so
     * entries ahead of it are kept and capped by {@link #scannedBlockValue}.
     */
    public static void expire(Level level) {
        switchTo(level);
        long now = level.getGameTime();
        BOXES.values().removeIf(end -> end <= now);
        SCANNED_BLOCKS.values().removeIf(end -> end <= now);
    }

    /** The countdown upstream would show for a scanned block at {@code now}, or 0 once it has run out. */
    public static int scannedBlockValue(BlockPos pos, long now) {
        Long end = SCANNED_BLOCKS.get(pos);
        return end == null ? 0 : (int) Math.max(0, Math.min(START_SCANNED_BLOCK_VALUE, end - now));
    }

    private static void switchTo(Level level) {
        if (current.get() == level) return;
        BOXES.clear();
        SCANNED_BLOCKS.clear();
        current = new WeakReference<>(level);
    }
}

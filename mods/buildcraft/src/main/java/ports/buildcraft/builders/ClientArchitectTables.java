package ports.buildcraft.builders;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * Upstream {@code ClientArchitectTables}: the boxes architect tables are scanning, and the blocks they scanned
 * recently, each with how many more client ticks to show it. Entries keep the game time they run out at
 * instead of upstream's per-tick countdown.
 */
public final class ClientArchitectTables {
    public static final int START_BOX_VALUE = 3;
    public static final int START_SCANNED_BLOCK_VALUE = 50;
    public static final Map<AABB, Long> BOXES = new ConcurrentHashMap<>();
    public static final Map<BlockPos, Long> SCANNED_BLOCKS = new ConcurrentHashMap<>();

    private ClientArchitectTables() {}

    public static void putBox(AABB box, long now) {
        BOXES.put(box, now + START_BOX_VALUE);
    }

    public static void putScannedBlock(BlockPos pos, long now) {
        SCANNED_BLOCKS.put(pos.immutable(), now + START_SCANNED_BLOCK_VALUE);
    }

    /** Drops entries that ran out, and those from a level whose clock is behind the one they were added in. */
    public static void expire(long now) {
        BOXES.values().removeIf(end -> end <= now || end > now + START_BOX_VALUE);
        SCANNED_BLOCKS.values().removeIf(end -> end <= now || end > now + START_SCANNED_BLOCK_VALUE);
    }

    /** The countdown upstream would show for a scanned block at {@code now}, or 0 once it has run out. */
    public static int scannedBlockValue(BlockPos pos, long now) {
        Long end = SCANNED_BLOCKS.get(pos);
        return end == null ? 0 : (int) Math.max(0, Math.min(START_SCANNED_BLOCK_VALUE, end - now));
    }
}

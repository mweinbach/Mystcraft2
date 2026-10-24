package ports.buildcraft.builders;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Not upstream. Survival players can craft electronic libraries, and an upload costs the server memory while its
 * parts arrive, time to inflate and read, and disk once stored. So each player, over every library together, may
 * send at most {@value #MAX_BYTES} bytes of upload parts and have at most {@value #MAX_READS} uploads read in any
 * {@value #WINDOW_MILLIS} ms window; parts or reads past that are refused until the window ends. Server-wide,
 * {@link ports.buildcraft.builders.snapshot.SnapshotStorage#hasRoomForUploads} refuses uploads once the stored
 * snapshots fill their share of the disk.
 */
public final class UploadQuota {
    /** How long a player's usage counts. */
    public static final long WINDOW_MILLIS = 10 * 60 * 1000L;
    /** Upload part bytes a player may send in a window: the largest single upload twice over. */
    public static final long MAX_BYTES = 64L * 1024 * 1024;
    /** Uploads a player may have read in a window. */
    public static final int MAX_READS = 8;
    private static final Map<UUID, Usage> USAGE = new HashMap<>();

    private static final class Usage {
        long windowStart;
        long bytes;
        int reads;
    }

    private UploadQuota() {}

    private static Usage usage(UUID player, long now) {
        if (USAGE.size() > 256) USAGE.values().removeIf(usage -> now - usage.windowStart >= WINDOW_MILLIS);
        Usage usage = USAGE.computeIfAbsent(player, id -> new Usage());
        if (now - usage.windowStart >= WINDOW_MILLIS) {
            usage.windowStart = now;
            usage.bytes = 0;
            usage.reads = 0;
        }
        return usage;
    }

    /** Counts {@code bytes} of upload parts from {@code player}; false, counting nothing, if that would pass the quota. */
    public static synchronized boolean tryReceive(UUID player, long bytes, long now) {
        Usage usage = usage(player, now);
        if (usage.bytes + bytes > MAX_BYTES) return false;
        usage.bytes += bytes;
        return true;
    }

    /** Counts one upload read for {@code player}; false, counting nothing, if that would pass the quota. */
    public static synchronized boolean tryRead(UUID player, long now) {
        Usage usage = usage(player, now);
        if (usage.reads >= MAX_READS) return false;
        usage.reads++;
        return true;
    }
}

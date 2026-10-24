package ports.gridwright.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Where the loaded sound mufflers are, per level, and how much they quieten a sound. A sound whose
 * source is within {@link #RADIUS} blocks of a muffler's centre plays at {@link #FACTOR} of its volume;
 * several mufflers do not stack. Each loader calls {@link #volume} where the server plays a sound.
 */
public final class SoundMuffling {

    public static final double RADIUS = 8.0;
    public static final float FACTOR = 0.1F;

    private static final Map<Level, Set<BlockPos>> MUFFLERS = Collections.synchronizedMap(new WeakHashMap<>());

    private SoundMuffling() { }

    static void add(Level level, BlockPos pos) {
        MUFFLERS.computeIfAbsent(level, key -> Collections.synchronizedSet(new HashSet<>())).add(pos.immutable());
    }

    static void remove(Level level, BlockPos pos) {
        Set<BlockPos> positions = MUFFLERS.get(level);
        if (positions != null) positions.remove(pos);
    }

    /** Whether a sound at {@code (x, y, z)} in {@code level} is muffled. */
    public static boolean muffled(Level level, double x, double y, double z) {
        Set<BlockPos> positions = MUFFLERS.get(level);
        if (positions == null || positions.isEmpty()) return false;
        synchronized (positions) {
            for (BlockPos pos : positions) {
                if (pos.distToCenterSqr(x, y, z) <= RADIUS * RADIUS) return true;
            }
        }
        return false;
    }

    /** The volume a sound at {@code (x, y, z)} should play at. */
    public static float volume(Level level, double x, double y, double z, float volume) {
        return muffled(level, x, y, z) ? volume * FACTOR : volume;
    }
}

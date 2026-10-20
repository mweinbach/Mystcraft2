package ports.buildcraft.core.marker;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import ports.buildcraft.lib.marker.MarkerCache;

/** Upstream's {@code VolumeCache} and {@code PathCache}, registered in upstream's order. */
public final class MarkerCaches {
    /** Upstream {@code BCCoreConfig.markerMaxDistance} default. */
    public static final int MAX_DISTANCE = 64;

    public static final MarkerCache<VolumeSubCache> VOLUME = new MarkerCache<>("volume", "buildcraft_marker_volume", VolumeSubCache::new);
    public static final MarkerCache<PathSubCache> PATH = new MarkerCache<>("path", "buildcraft_marker_path", PathSubCache::new);

    private MarkerCaches() {}

    /** Upstream {@code PositionUtil.getDirectFacingOffset}: the direction from one position to the other along a single axis. */
    @Nullable
    public static Direction directOffset(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        int dz = to.getZ() - from.getZ();
        boolean x = dx != 0, y = dy != 0, z = dz != 0;
        if (x && y || x && z || y && z) return null;
        if (x) return dx > 0 ? Direction.EAST : Direction.WEST;
        if (y) return dy > 0 ? Direction.UP : Direction.DOWN;
        if (z) return dz > 0 ? Direction.SOUTH : Direction.NORTH;
        return null;
    }
}

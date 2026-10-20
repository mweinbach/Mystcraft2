package ports.buildcraft.lib;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;

/** Upstream {@code PositionUtil}'s path helpers. */
public final class PositionUtil {
    private PositionUtil() {}

    /**
     * Upstream {@code getAllOnPath}: every block a straight line from {@code from} to {@code to} passes
     * through, comparing against block centres, excluding {@code from} and ending at {@code to}.
     */
    public static List<BlockPos> getAllOnPath(BlockPos from, BlockPos to) {
        List<BlockPos> path = new ArrayList<>();
        BlockPos difference = to.subtract(from);
        int ax = Math.abs(difference.getX());
        int ay = Math.abs(difference.getY());
        int az = Math.abs(difference.getZ());
        int count = ax + ay + az;
        BlockPos current = from;
        int ddx = difference.getX() > 0 ? 1 : -1;
        int ddy = difference.getY() > 0 ? 1 : -1;
        int ddz = difference.getZ() > 0 ? 1 : -1;
        int dx = count / 2;
        int dy = count / 2;
        int dz = count / 2;
        for (int j = 0; j < count; j++) {
            dx += ax;
            dy += ay;
            dz += az;
            boolean changed = false;
            if (dx >= count) {
                changed = true;
                dx -= count;
                current = current.offset(ddx, 0, 0);
            }
            if (dy >= count) {
                changed = true;
                dy -= count;
                current = current.offset(0, ddy, 0);
            }
            if (dz >= count) {
                changed = true;
                dz -= count;
                current = current.offset(0, 0, ddz);
            }
            if (changed) path.add(current);
        }
        return path;
    }
}

package ports.buildcraft.lib;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * Upstream {@code VolumeUtil.iterateCone} with its square cone, the only one lasers used (upstream ignored the
 * {@code edges} flag that would have picked the pyramid): square slices that grow by one block each step away
 * from {@code start}. A position is visible when every block on the straight path from it back to
 * {@code start}, apart from itself, is air.
 */
public final class VolumeUtil {
    @FunctionalInterface
    public interface VolumeIterator {
        void takePos(BlockPos pos, boolean visible);
    }

    private VolumeUtil() {}

    public static void iterateCone(Level level, BlockPos start, Direction direction, int distance, VolumeIterator iterator) {
        Direction.Axis axisI, axisJ;
        switch (direction.getAxis()) {
            case X -> {
                axisI = Direction.Axis.Y;
                axisJ = Direction.Axis.Z;
            }
            case Y -> {
                axisI = Direction.Axis.X;
                axisJ = Direction.Axis.Z;
            }
            default -> {
                axisI = Direction.Axis.X;
                axisJ = Direction.Axis.Y;
            }
        }
        Direction faceI = Direction.fromAxisAndDirection(axisI, Direction.AxisDirection.POSITIVE);
        Direction faceJ = Direction.fromAxisAndDirection(axisJ, Direction.AxisDirection.POSITIVE);
        Set<BlockPos> allVisible = new HashSet<>();
        allVisible.add(start);
        BlockPos coneCenter = start;
        for (int d = 0; d < distance; d++) {
            coneCenter = coneCenter.relative(direction);
            for (int i = -d; i <= d; i++) {
                for (int j = -d; j <= d; j++) {
                    BlockPos pos = coneCenter.relative(faceI, i).relative(faceJ, j);
                    iterator.takePos(pos, isVisible(level, PositionUtil.getAllOnPath(pos, start), allVisible));
                }
            }
        }
    }

    private static boolean isVisible(Level level, List<BlockPos> required, Set<BlockPos> allVisible) {
        for (BlockPos pos : required) {
            if (allVisible.contains(pos)) continue;
            if (!level.getBlockState(pos).isAir()) return false;
            allVisible.add(pos);
        }
        return true;
    }
}

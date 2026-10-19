package ports.jurassicraft.tourrail;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Resolve reciprocal neighboring tracks without loading an unvisited chunk. */
public final class TourRailConnections {
    private TourRailConnections() {}
    public static boolean isRail(Level level, BlockPos pos) {
        return level.hasChunkAt(pos) && level.getBlockState(pos).getBlock() instanceof TourRailBlock;
    }
    @Nullable public static BlockPos neighbor(Level level, BlockPos pos, TourRailShape.End end) {
        int[] heights = end.rise() == 1 ? new int[] {1} : new int[] {0, -1};
        for (int dy : heights) {
            BlockPos candidate = pos.offset(end.x(), dy, end.z());
            if (!isRail(level, candidate)) continue;
            BlockState other = level.getBlockState(candidate);
            TourRailShape shape = other.getValue(TourRailBlock.SHAPE);
            for (TourRailShape.End back : new TourRailShape.End[] {shape.first(), shape.second()}) {
                if (back.x() == -end.x() && back.z() == -end.z()
                        && ((dy == 0 && end.rise() == 0 && back.rise() == 0)
                        || (dy == 1 && end.rise() == 1 && back.rise() == 0)
                        || (dy == -1 && end.rise() == 0 && back.rise() == 1))) return candidate;
            }
        }
        return null;
    }
    public static boolean connected(Level level, BlockPos from, BlockPos to) {
        if (!isRail(level, from)) return false;
        TourRailShape shape = level.getBlockState(from).getValue(TourRailBlock.SHAPE);
        return to.equals(neighbor(level, from, shape.first())) || to.equals(neighbor(level, from, shape.second()));
    }
}

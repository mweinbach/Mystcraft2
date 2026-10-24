package ports.gridwright.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The surface a wand works on: starting from the clicked block, the connected blocks of the same type
 * in the plane of the clicked face whose clicked side is open (air, water, grass and other replaceable
 * blocks), nearest first.
 */
public final class WandSurface {

    private WandSurface() { }

    /** Up to {@code limit} positions; {@code accept} adds wand-specific rules for each one. */
    public static List<BlockPos> find(Level level, BlockPos origin, Direction face, int limit, Predicate<BlockPos> accept) {
        List<BlockPos> found = new ArrayList<>();
        Block block = level.getBlockState(origin).getBlock();
        if (limit <= 0 || !open(level, origin, face) || !accept.test(origin)) return found;
        List<Direction> spread = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            if (direction.getAxis() != face.getAxis()) spread.add(direction);
        }
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin.immutable());
        seen.add(origin.immutable());
        while (!queue.isEmpty() && found.size() < limit) {
            BlockPos pos = queue.poll();
            found.add(pos);
            for (Direction direction : spread) {
                BlockPos next = pos.relative(direction);
                if (!seen.add(next) || !level.isLoaded(next)) continue;
                if (level.getBlockState(next).is(block) && open(level, next, face) && accept.test(next)) queue.add(next);
            }
        }
        return found;
    }

    /** Whether the block in front of {@code pos}'s {@code face} can be replaced by placing a block. */
    public static boolean open(Level level, BlockPos pos, Direction face) {
        BlockState front = level.getBlockState(pos.relative(face));
        return front.getMaterial().isReplaceable();
    }
}

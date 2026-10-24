package ports.gridwright.transfer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Finds what a node's pipes reach. Starting from the pipes on the node's back and sides, it walks
 * connected transfer pipes (up to {@link #MAX_PIPES}) and collects every inventory or tank touching
 * them (or tank, or energy store, depending on the node), nearest first. Nodes are never targets, and neither is the block the node itself faces.
 *
 * <p>Nodes cache the result until any pipe, node or block beside a pipe changes ({@link #changed()})
 * or a few seconds pass, which also catches inventories whose capabilities change in place.
 */
public final class PipeNetwork {

    public static final int MAX_PIPES = 1024;

    private static final AtomicLong EPOCH = new AtomicLong();

    /** An inventory or tank on the network and the face the pipe touches. */
    public record Target(BlockPos pos, Direction face) { }

    private PipeNetwork() { }

    /** Marks every cached network stale. */
    public static void changed() {
        EPOCH.incrementAndGet();
    }

    public static long epoch() {
        return EPOCH.get();
    }

    public static List<Target> targets(ServerLevel level, BlockPos node, Direction front, boolean fluid) {
        return targets(level, node, front, fluid ? NodeKind.Resource.FLUIDS : NodeKind.Resource.ITEMS);
    }

    public static List<Target> targets(ServerLevel level, BlockPos node, Direction front, NodeKind.Resource resource) {
        BlockPos attached = node.relative(front);
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> pipes = new HashSet<>();
        for (Direction direction : Direction.values()) {
            if (direction == front) continue;
            BlockPos next = node.relative(direction);
            if (isPipe(level, next) && pipes.add(next)) queue.add(next);
        }
        // Keyed by position and face: a sided inventory touched on two faces offers different slots on each.
        Set<Target> found = new LinkedHashSet<>();
        while (!queue.isEmpty()) {
            BlockPos pipe = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = pipe.relative(direction);
                if (pipes.contains(next) || next.equals(attached) || !level.hasChunkAt(next)) continue;
                BlockState state = level.getBlockState(next);
                if (state.getBlock() instanceof TransferPipeBlock) {
                    if (pipes.size() < MAX_PIPES) {
                        pipes.add(next);
                        queue.add(next);
                    }
                    continue;
                }
                if (state.getBlock() instanceof NodeBlock) continue;
                Direction face = direction.getOpposite();
                Target target = new Target(next.immutable(), face);
                if (found.contains(target)) continue;
                boolean serves = switch (resource) {
                    case ITEMS -> TransferPlatform.hasItems(level, next, face);
                    case FLUIDS -> TransferPlatform.hasFluids(level, next, face);
                    case ENERGY -> TransferPlatform.hasEnergy(level, next, face);
                };
                if (serves) {
                    found.add(target);
                }
            }
        }
        return List.copyOf(found);
    }

    private static boolean isPipe(ServerLevel level, BlockPos pos) {
        return level.hasChunkAt(pos) && level.getBlockState(pos).getBlock() instanceof TransferPipeBlock;
    }
}

package ports.jurassicraft.plants;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Wood behavior for the original trees; flammability is added in {@link PlantRegistry#setup()}. */
final class WoodBlocks {
    private WoodBlocks() {}
    static final class Log extends RotatedPillarBlock {
        private final PlantSpecies species;
        Log(PlantSpecies species, BlockBehaviour.Properties properties) { super(properties); this.species = species; }
        @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
            if (!state.is(replacement.getBlock()) && level instanceof ServerLevel server)
                Leaves.recheckNearby(server, pos, PlantRegistry.leaves(species).get());
            super.onRemove(state, level, pos, replacement, moved);
        }
    }
    static final class Leaves extends LeavesBlock {
        private static final int RADIUS = 7, SIDE = 2 * RADIUS + 1, VOLUME = SIDE * SIDE * SIDE;
        private static final ThreadLocal<NaturalDecay> NATURAL_DECAY = new ThreadLocal<>();
        private final PlantSpecies species;
        Leaves(PlantSpecies species, BlockBehaviour.Properties properties) { super(properties); this.species = species; }

        private static final class NaturalDecay {
            final Level level;
            final BlockPos pos;
            boolean consumed;
            NaturalDecay(Level level, BlockPos pos) { this.level = level; this.pos = pos.immutable(); }
        }

        // A removal can invoke nested callbacks, including another leaf decay.
        // Scope the exemption to one removal at this position in this world.
        static void withNaturalDecay(Level level, BlockPos pos, Runnable action) {
            NaturalDecay previous = NATURAL_DECAY.get();
            NATURAL_DECAY.set(new NaturalDecay(level, pos));
            try { action.run(); }
            finally {
                if (previous == null) NATURAL_DECAY.remove();
                else NATURAL_DECAY.set(previous);
            }
        }
        static boolean consumeNaturalDecay(Level level, BlockPos pos) {
            NaturalDecay current = NATURAL_DECAY.get();
            if (current == null || current.consumed || current.level != level || !current.pos.equals(pos)) return false;
            current.consumed = true;
            return true;
        }

        // Reuse distance=7 for leaves awaiting a check. Keeping waterlogging and
        // persistence preserves vanilla behavior for player-placed leaves.
        @Override public boolean isRandomlyTicking(BlockState state) { return !state.getValue(PERSISTENT); }
        @Override public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            // Vanilla's scheduled distance propagation ignores diagonal leaves.
        }
        @Override public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            if (state.getValue(PERSISTENT) || (state.getValue(DISTANCE) != 7 && random.nextInt(32) != 0)) return;
            if (!PlantChunks.isAreaLoaded(level, pos, RADIUS + 2)) return;
            if (hasSupport(level, pos, this, PlantRegistry.log(species).get())) {
                if (state.getValue(DISTANCE) != 1) level.setBlock(pos, state.setValue(DISTANCE, 1), Block.UPDATE_CLIENTS);
            } else {
                Block.dropResources(state, level, pos);
                // If this leaf has no support path of at most seven steps,
                // no supported neighbor can depend on a path through it.
                withNaturalDecay(level, pos, () -> level.removeBlock(pos, false));
            }
        }
        @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
            if (!state.is(replacement.getBlock()) && level instanceof ServerLevel server && !consumeNaturalDecay(level, pos))
                recheckNearby(server, pos, this);
            super.onRemove(state, level, pos, replacement, moved);
        }

        /** Upstream supports up to seven steps through matching leaves in any of 26 directions. */
        static boolean hasSupport(BlockGetter world, BlockPos pos, Block leaves, Block log) {
            boolean[] visited = new boolean[VOLUME];
            int[] queue = new int[VOLUME];
            byte[] distance = new byte[VOLUME];
            int start = index(0, 0, 0), head = 0, tail = 0;
            visited[start] = true;
            queue[tail++] = start;
            BlockPos.MutableBlockPos target = new BlockPos.MutableBlockPos();
            while (head < tail) {
                int current = queue[head++];
                int x = current / (SIDE * SIDE) - RADIUS;
                int y = (current / SIDE) % SIDE - RADIUS;
                int z = current % SIDE - RADIUS;
                int nextDistance = distance[current] + 1;
                for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    int nx = x + dx, ny = y + dy, nz = z + dz;
                    if (Math.abs(nx) > RADIUS || Math.abs(ny) > RADIUS || Math.abs(nz) > RADIUS) continue;
                    int next = index(nx, ny, nz);
                    if (visited[next]) continue;
                    visited[next] = true;
                    BlockState neighbor = world.getBlockState(target.set(pos.getX() + nx, pos.getY() + ny, pos.getZ() + nz));
                    if (neighbor.is(log)) return true;
                    if (nextDistance < RADIUS && neighbor.is(leaves)) {
                        distance[next] = (byte) nextDistance;
                        queue[tail++] = next;
                    }
                }
            }
            return false;
        }
        private static int index(int x, int y, int z) { return ((x + RADIUS) * SIDE + y + RADIUS) * SIDE + z + RADIUS; }

        // A removed log or connecting leaf can affect diagonal branches as far
        // as seven blocks away. Never load chunks solely to update decay.
        static void recheckNearby(ServerLevel level, BlockPos removed, Block leaves) {
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int x = -RADIUS; x <= RADIUS; x++) for (int z = -RADIUS; z <= RADIUS; z++) {
                cursor.set(removed.getX() + x, removed.getY(), removed.getZ() + z);
                if (!level.hasChunkAt(cursor)) continue;
                for (int y = -RADIUS; y <= RADIUS; y++) {
                    cursor.setY(removed.getY() + y);
                    if (level.isOutsideBuildHeight(cursor)) continue;
                    BlockState state = level.getBlockState(cursor);
                    if (state.is(leaves) && !state.getValue(PERSISTENT) && state.getValue(DISTANCE) != 7)
                        level.setBlock(cursor, state.setValue(DISTANCE, 7), Block.UPDATE_CLIENTS);
                }
            }
        }
    }
    static final class Planks extends Block {
        Planks(BlockBehaviour.Properties properties) { super(properties); }
    }
    static final class Stair extends StairBlock {
        Stair(Supplier<BlockState> state, BlockBehaviour.Properties properties) { super(state.get(), properties); }
    }
    static final class Slab extends SlabBlock {
        Slab(BlockBehaviour.Properties properties) { super(properties); }
    }
    static final class Fence extends FenceBlock {
        Fence(BlockBehaviour.Properties properties) { super(properties); }
    }
    static final class Gate extends FenceGateBlock {
        Gate(BlockBehaviour.Properties properties) { super(properties); }
    }
}

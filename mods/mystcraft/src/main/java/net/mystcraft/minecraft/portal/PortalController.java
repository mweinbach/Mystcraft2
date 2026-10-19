package net.mystcraft.minecraft.portal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.mystcraft.integration.registry.PortalRegistry;
import net.mystcraft.minecraft.item.LinkingBookItem;

/**
 * Classic PortalUtils growth/tension/source-path rules, adapted to loaded 1.19.2 blocks.
 * Frames need not be rectangular or vertical. There is no invented portal-size limit.
 */
public final class PortalController {
    /** Explicit port safety budgets, absent from the unbounded legacy implementation. */
    public static final int MAX_NETWORK_BLOCKS = 4096;
    public static final int MAX_QUEUE_STEPS = 131072;
    private static boolean updating;
    private PortalController() {}
    public static boolean isNetwork(BlockState state) {
        return state.is(PortalRegistry.CRYSTAL.get()) || state.is(PortalRegistry.LINK_PORTAL.get());
    }
    private static boolean loaded(Level level, BlockPos pos) { return level.hasChunkAt(pos) && !level.isOutsideBuildHeight(pos); }
    private static boolean networkAt(Level level, BlockPos pos) { return loaded(level, pos) && isNetwork(level.getBlockState(pos)); }
    public static BookReceptacleBlockEntity source(Level level, BlockPos start) {
        Set<BlockPos> visited = new HashSet<>();
        BlockPos pos = start;
        while (visited.size() < MAX_NETWORK_BLOCKS && loaded(level, pos) && visited.add(pos)) {
            BlockState state = level.getBlockState(pos);
            if (state.is(PortalRegistry.BOOK_RECEPTACLE.get())) {
                return level.getBlockEntity(pos) instanceof BookReceptacleBlockEntity receptacle ? receptacle : null;
            }
            if (!isNetwork(state) || !state.getValue(CrystalBlock.ACTIVE)) return null;
            pos = pos.relative(state.getValue(CrystalBlock.SOURCE));
        }
        return null;
    }
    private static boolean validSource(BookReceptacleBlockEntity source) {
        return source != null && source.getBook().getItem() instanceof LinkingBookItem;
    }
    public static boolean tension(Level level, BlockPos pos) {
        int pairs = 0;
        if (networkAt(level, pos.east()) && networkAt(level, pos.west())) pairs++;
        if (networkAt(level, pos.above()) && networkAt(level, pos.below())) pairs++;
        if (networkAt(level, pos.north()) && networkAt(level, pos.south())) pairs++;
        return pairs > 1;
    }
    private static void surrounding(ArrayDeque<BlockPos> queue, BlockPos center) {
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
            int distance = Math.abs(x) + Math.abs(y) + Math.abs(z);
            if (distance == 1 || distance == 2) queue.add(center.offset(x, y, z));
        }
    }
    public static void activate(Level level, BlockPos receptaclePos) {
        if (level.isClientSide || updating || !(level.getBlockEntity(receptaclePos) instanceof BookReceptacleBlockEntity receptacle)
                || !validSource(receptacle)) return;
        updating = true;
        try {
            Direction facing = level.getBlockState(receptaclePos).getValue(BookReceptacleBlock.FACING);
            BlockPos base = receptaclePos.relative(facing.getOpposite());
            if (!loaded(level, base) || !level.getBlockState(base).is(PortalRegistry.CRYSTAL.get())) return;
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            List<BlockPos> created = new ArrayList<>();
            surrounding(queue, base);
            int work = 0;
            while (!queue.isEmpty()) {
                if (++work > MAX_QUEUE_STEPS || created.size() >= MAX_NETWORK_BLOCKS) {
                    for (BlockPos pos : created) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    return;
                }
                BlockPos pos = queue.removeFirst();
                if (!loaded(level, pos) || !level.isEmptyBlock(pos)) continue;
                int adjacent = 0;
                for (Direction direction : Direction.values()) if (networkAt(level, pos.relative(direction))) adjacent++;
                if (adjacent > 1) {
                    level.setBlock(pos, PortalRegistry.LINK_PORTAL.get().defaultBlockState(), 2);
                    created.add(pos);
                    surrounding(queue, pos);
                }
            }
            // Like the legacy stack pass, prune newly grown cells from newest to oldest.
            for (int i = created.size() - 1; i >= 0; i--) {
                BlockPos pos = created.get(i);
                if (!tension(level, pos)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
            // A directed spanning tree resolves any active crystal/portal to its book receptacle.
            queue.add(receptaclePos);
            Set<BlockPos> seen = new HashSet<>();
            List<BlockPos> directed = new ArrayList<>();
            while (!queue.isEmpty()) {
                if (seen.size() >= MAX_NETWORK_BLOCKS) {
                    for (BlockPos pos : directed) {
                        BlockState state = level.getBlockState(pos);
                        if (isNetwork(state)) level.setBlock(pos, state.setValue(CrystalBlock.ACTIVE, false), 2);
                    }
                    for (BlockPos pos : created) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    return;
                }
                BlockPos parent = queue.removeFirst();
                if (!seen.add(parent)) continue;
                for (Direction direction : Direction.values()) {
                    BlockPos pos = parent.relative(direction);
                    if (!networkAt(level, pos)) continue;
                    BlockState state = level.getBlockState(pos);
                    if (state.getValue(CrystalBlock.ACTIVE)) continue;
                    state = state.setValue(CrystalBlock.ACTIVE, true).setValue(CrystalBlock.SOURCE, direction.getOpposite());
                    if (state.is(PortalRegistry.LINK_PORTAL.get())) state = state.setValue(LinkPortalBlock.AXIS, planeAxis(level, pos))
                            .setValue(LinkPortalBlock.PLANAR, !allAxes(level, pos));
                    level.setBlock(pos, state, 2);
                    directed.add(pos);
                    queue.add(pos);
                }
            }
            // Unreachable or unsupported cells never become usable portal entrances.
            for (BlockPos pos : created) {
                if (level.getBlockState(pos).is(PortalRegistry.LINK_PORTAL.get())
                        && (!tension(level, pos) || !validSource(source(level, pos)))) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        } finally { updating = false; }
    }
    private static boolean allAxes(Level level, BlockPos pos) {
        return networkAt(level, pos.east()) && networkAt(level, pos.west())
                && networkAt(level, pos.above()) && networkAt(level, pos.below())
                && networkAt(level, pos.north()) && networkAt(level, pos.south());
    }
    private static Direction.Axis planeAxis(Level level, BlockPos pos) {
        if (networkAt(level, pos.east()) && networkAt(level, pos.west())
                && networkAt(level, pos.above()) && networkAt(level, pos.below())) return Direction.Axis.Z;
        if (networkAt(level, pos.north()) && networkAt(level, pos.south())
                && networkAt(level, pos.above()) && networkAt(level, pos.below())) return Direction.Axis.X;
        return Direction.Axis.Y;
    }
    public static void shutdown(Level level, BlockPos receptaclePos) {
        if (level.isClientSide || updating) return;
        updating = true;
        try {
            Set<BlockPos> connected = connected(level, receptaclePos);
            // Resolve ownership before clearing source pointers, so shared crystal networks do
            // not lose another receptacle's still-valid portal.
            List<BlockPos> owned = new ArrayList<>();
            for (BlockPos pos : connected) {
                BookReceptacleBlockEntity owner = source(level, pos);
                if (owner != null && owner.getBlockPos().equals(receptaclePos)) owned.add(pos);
            }
            for (BlockPos pos : owned) {
                BlockState state = level.getBlockState(pos);
                level.setBlock(pos, state.is(PortalRegistry.LINK_PORTAL.get()) ? Blocks.AIR.defaultBlockState()
                        : state.setValue(CrystalBlock.ACTIVE, false).setValue(CrystalBlock.SOURCE, Direction.DOWN), 2);
            }
        } finally { updating = false; }
    }
    private static Set<BlockPos> connected(Level level, BlockPos start) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (Direction direction : Direction.values()) queue.add(start.relative(direction));
        while (!queue.isEmpty() && seen.size() < MAX_NETWORK_BLOCKS) {
            BlockPos pos = queue.removeFirst();
            if (seen.contains(pos) || !networkAt(level, pos)) continue;
            seen.add(pos);
            for (Direction direction : Direction.values()) queue.add(pos.relative(direction));
        }
        return seen;
    }
    public static void validate(Level level, BlockPos start) {
        if (level.isClientSide || updating) return;
        updating = true;
        try {
            Set<BlockPos> component = connected(level, start);
            if (networkAt(level, start)) component.add(start);
            boolean changed;
            int work = 0;
            do {
                changed = false;
                for (BlockPos pos : component) {
                    if (++work > MAX_QUEUE_STEPS) return;
                    BlockState state = level.getBlockState(pos);
                    if (!isNetwork(state)) continue;
                    boolean valid = validSource(source(level, pos));
                    if (state.is(PortalRegistry.LINK_PORTAL.get()) && (!tension(level, pos) || !valid)) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2); changed = true;
                    } else if (state.is(PortalRegistry.CRYSTAL.get()) && state.getValue(CrystalBlock.ACTIVE) && !valid) {
                        level.setBlock(pos, state.setValue(CrystalBlock.ACTIVE, false), 2); changed = true;
                    }
                }
            } while (changed);
        } finally { updating = false; }
    }
}

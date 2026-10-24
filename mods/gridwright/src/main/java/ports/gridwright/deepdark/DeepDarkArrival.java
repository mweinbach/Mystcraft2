package ports.gridwright.deepdark;

import ports.gridwright.block.DeepDarkPortalBlock;
import ports.gridwright.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Finds, or makes, a safe place to arrive: two blocks of air over a sturdy floor, no lava or fire
 * within a block, and a return portal within reach. Nothing here removes bedrock, unbreakable blocks
 * or block entities.
 */
public final class DeepDarkArrival {

    /** How far the search for an existing pocket reaches sideways and up/down from the requested spot. */
    public static final int HORIZONTAL_RADIUS = 3;
    public static final int VERTICAL_RANGE = 12;
    /** Feet never go lower than this above the level's bottom (clear of the bedrock floor band). */
    public static final int FLOOR_MARGIN = 6;
    /** Feet never go higher than this below the level's top (the room's top stays clear of the roof band). */
    public static final int ROOF_MARGIN = 9;

    /** Where the player stands, where their return portal is, and whether a room had to be dug. */
    public record Arrival(BlockPos feet, BlockPos portal, boolean carved) { }

    private DeepDarkArrival() { }

    /** Arrival near {@code requested} in {@code level}, using the default search box and level margins. */
    @Nullable
    public static Arrival prepare(ServerLevel level, BlockPos requested) {
        return prepare(level, requested, HORIZONTAL_RADIUS, VERTICAL_RANGE,
                level.getMinBuildHeight() + FLOOR_MARGIN, level.getMaxBuildHeight() - ROOF_MARGIN);
    }

    /**
     * Searches the box of {@code radius} around {@code requested} (feet height clamped to
     * {@code [minFeetY, maxFeetY]}), nearest first, for a safe pocket; failing that, digs a 3×3×3 room at
     * the nearest spot where digging can work. Places a return portal unless one is already in reach.
     * Returns {@code null} only if every spot is walled in by blocks that must not be removed.
     */
    @Nullable
    public static Arrival prepare(ServerLevel level, BlockPos requested, int radius, int vertical, int minFeetY, int maxFeetY) {
        if (minFeetY > maxFeetY) return null;
        BlockPos inside = level.getWorldBorder().clampToBounds(requested.getX(), requested.getY(), requested.getZ());
        BlockPos center = new BlockPos(inside.getX(), Mth.clamp(requested.getY(), minFeetY, maxFeetY), inside.getZ());
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos offset : offsets(radius, vertical)) {
            BlockPos feet = center.offset(offset);
            if (feet.getY() >= minFeetY && feet.getY() <= maxFeetY && withinBorder(level.getWorldBorder(), feet)) candidates.add(feet);
        }
        for (BlockPos feet : candidates) {
            if (standable(level, feet) && portalSpot(level, feet) != null) return new Arrival(feet, placePortal(level, feet), false);
        }
        for (BlockPos feet : candidates) {
            if (!canCarve(level, feet)) continue;
            carve(level, feet);
            if (!standable(level, feet)) continue;
            BlockPos portal = portalSpot(level, feet);
            if (portal != null) return new Arrival(feet, placePortal(level, feet), true);
        }
        return null;
    }

    /** Offsets in a box, nearest first; ties prefer the same height, then lower x, y and z. */
    static List<BlockPos> offsets(int radius, int vertical) {
        List<BlockPos> offsets = new ArrayList<>();
        for (int dy = -vertical; dy <= vertical; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) offsets.add(new BlockPos(dx, dy, dz));
            }
        }
        offsets.sort(Comparator.<BlockPos>comparingInt(p -> p.getX() * p.getX() + p.getY() * p.getY() + p.getZ() * p.getZ())
                .thenComparingInt(p -> Math.abs(p.getY())).thenComparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ));
        return offsets;
    }

    /**
     * True if the 3×3 room around {@code feet} (and so a standing player and their return portal) lies
     * wholly inside {@code border}, so nobody arrives outside it or in its damage zone.
     */
    public static boolean withinBorder(WorldBorder border, BlockPos feet) {
        return contains(border, new AABB(feet.getX() - 1, feet.getY(), feet.getZ() - 1, feet.getX() + 2, feet.getY() + 2, feet.getZ() + 2));
    }

    /** True if all of {@code box} is inside {@code border} (vanilla's AABB check only tests overlap). */
    public static boolean contains(WorldBorder border, AABB box) {
        return box.minX >= border.getMinX() && box.maxX <= border.getMaxX()
                && box.minZ >= border.getMinZ() && box.maxZ <= border.getMaxZ();
    }

    /**
     * Two blocks of air over a sturdy, harmless floor, with no lava or fire touching that space, inside
     * the world border.
     */
    public static boolean standable(ServerLevel level, BlockPos feet) {
        if (!withinBorder(level.getWorldBorder(), feet)) return false;
        if (!level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir()) return false;
        BlockPos floor = feet.below();
        BlockState floorState = level.getBlockState(floor);
        if (!floorState.isFaceSturdy(level, floor, Direction.UP) || harmful(floorState)) return false;
        for (BlockPos near : BlockPos.betweenClosed(feet.offset(-1, -1, -1), feet.offset(1, 2, 1))) {
            BlockState state = level.getBlockState(near);
            if (state.getFluidState().is(FluidTags.LAVA) || state.is(BlockTags.FIRE)) return false;
        }
        return true;
    }

    /** Blocks that hurt or trap a player standing on or in them. */
    public static boolean harmful(BlockState state) {
        return state.is(Blocks.MAGMA_BLOCK) || state.is(BlockTags.CAMPFIRES) || state.is(BlockTags.FIRE)
                || state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE)
                || state.is(Blocks.POWDER_SNOW) || state.getFluidState().is(FluidTags.LAVA);
    }

    /** Bedrock, unbreakable blocks (barriers, portals frames…) and anything with a block entity. */
    public static boolean protectedBlock(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(Blocks.BEDROCK) || state.getDestroySpeed(level, pos) < 0 || level.getBlockEntity(pos) != null;
    }

    /**
     * Where the return portal for {@code feet} is or would go: an existing portal touching the two-high
     * standing space (beside the feet or head, or under the feet, so it can be used without digging), else
     * the first air block beside the feet (north, east, south, west), else the floor itself.
     */
    @Nullable
    static BlockPos portalSpot(ServerLevel level, BlockPos feet) {
        BlockPos existing = existingPortal(level, feet);
        if (existing != null) return existing;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (level.getBlockState(feet.relative(side)).isAir()) return feet.relative(side);
        }
        return protectedBlock(level, feet.below()) ? null : feet.below();
    }

    /** A portal that touches the standing space at {@code feet}: under the feet, or beside the feet or head. */
    @Nullable
    public static BlockPos existingPortal(ServerLevel level, BlockPos feet) {
        List<BlockPos> touching = new ArrayList<>();
        touching.add(feet.below());
        for (Direction side : Direction.Plane.HORIZONTAL) {
            touching.add(feet.relative(side));
            touching.add(feet.above().relative(side));
        }
        for (BlockPos near : touching) {
            if (level.getBlockState(near).is(ModBlocks.DEEP_DARK_PORTAL.get())) return near;
        }
        return null;
    }

    private static BlockPos placePortal(ServerLevel level, BlockPos feet) {
        BlockPos spot = portalSpot(level, feet);
        if (spot == null) throw new IllegalStateException("No portal spot at " + feet);
        if (!level.getBlockState(spot).is(ModBlocks.DEEP_DARK_PORTAL.get())) {
            level.setBlock(spot, ModBlocks.DEEP_DARK_PORTAL.get().defaultBlockState().setValue(DeepDarkPortalBlock.GENERATED, true), Block.UPDATE_ALL);
        }
        return spot;
    }

    /** Digging can only work if the feet and head blocks may be cleared and the floor is usable or replaceable. */
    static boolean canCarve(ServerLevel level, BlockPos feet) {
        if (protectedBlock(level, feet) || protectedBlock(level, feet.above())) return false;
        BlockPos floor = feet.below();
        BlockState floorState = level.getBlockState(floor);
        return !protectedBlock(level, floor) || floorState.isFaceSturdy(level, floor, Direction.UP) && !harmful(floorState);
    }

    /**
     * Clears a 3×3 room three blocks high above {@code feet}'s floor, gives it a stone floor where the
     * floor is missing or harmful, and seals every fluid in the shell around it with stone. Protected
     * blocks are left exactly as they were.
     */
    static void carve(ServerLevel level, BlockPos feet) {
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-2, -2, -2), feet.offset(2, 3, 2))) {
            int dx = pos.getX() - feet.getX(), dy = pos.getY() - feet.getY(), dz = pos.getZ() - feet.getZ();
            boolean room = Math.abs(dx) <= 1 && Math.abs(dz) <= 1 && dy >= 0 && dy <= 2;
            if (room || protectedBlock(level, pos)) continue;
            BlockState state = level.getBlockState(pos);
            boolean floor = Math.abs(dx) <= 1 && Math.abs(dz) <= 1 && dy == -1;
            boolean roof = Math.abs(dx) <= 1 && Math.abs(dz) <= 1 && dy == 3;
            if (floor && (!state.isFaceSturdy(level, pos, Direction.UP) || harmful(state)) || !state.getFluidState().isEmpty()
                    || roof && state.getBlock() instanceof FallingBlock) {
                level.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-1, 0, -1), feet.offset(1, 2, 1))) {
            if (!protectedBlock(level, pos) && !level.getBlockState(pos).isAir()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }
}

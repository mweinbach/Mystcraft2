package ports.gridwright.worker;

import ports.gridwright.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;

/**
 * The columns an ender quarry mines: the inside of a closed loop of boundary blocks
 * ({@code #gridwright:quarry_boundary}, fences and walls by default) that touches the quarry. The loop
 * is followed through blocks sharing a side at the quarry's height; the inside is every column the
 * outside cannot reach without crossing it. Columns are indexed {@code x + z * sizeX} from the
 * north-west corner.
 */
public record QuarryArea(int minX, int minZ, int sizeX, int sizeZ, int y, BitSet interior) {

    /** Most boundary blocks a loop may have. */
    public static final int MAX_BOUNDARY = 1024;
    /** Widest a loop may be along either axis, boundary included. */
    public static final int MAX_SPAN = 128;

    /** A scan result: an area, or the reason there is none. */
    public record Scan(WorkerBlockEntity.Status status, @Nullable QuarryArea area) { }

    /** Finds the loop touching {@code quarry} and its inside. */
    public static Scan scan(Level level, BlockPos quarry) {
        BlockPos start = null;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos next = quarry.relative(side);
            if (level.getBlockState(next).is(ModTags.QUARRY_BOUNDARY)) {
                start = next;
                break;
            }
        }
        if (start == null) return new Scan(WorkerBlockEntity.Status.NO_BOUNDARY, null);

        Set<BlockPos> boundary = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        boundary.add(start);
        queue.add(start);
        int minX = start.getX(), maxX = minX, minZ = start.getZ(), maxZ = minZ;
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos next = pos.relative(side);
                if (boundary.contains(next) || !level.isLoaded(next) || !level.getBlockState(next).is(ModTags.QUARRY_BOUNDARY)) continue;
                if (boundary.size() >= MAX_BOUNDARY) return new Scan(WorkerBlockEntity.Status.TOO_LARGE, null);
                boundary.add(next);
                queue.add(next);
                minX = Math.min(minX, next.getX());
                maxX = Math.max(maxX, next.getX());
                minZ = Math.min(minZ, next.getZ());
                maxZ = Math.max(maxZ, next.getZ());
            }
        }
        if (maxX - minX + 1 > MAX_SPAN || maxZ - minZ + 1 > MAX_SPAN) return new Scan(WorkerBlockEntity.Status.TOO_LARGE, null);

        // Flood the outside through a one-column margin around the loop's bounds.
        int width = maxX - minX + 3;
        int depth = maxZ - minZ + 3;
        boolean[] wall = new boolean[width * depth];
        for (BlockPos pos : boundary) wall[(pos.getX() - minX + 1) + (pos.getZ() - minZ + 1) * width] = true;
        boolean[] outside = new boolean[width * depth];
        ArrayDeque<Integer> flood = new ArrayDeque<>();
        outside[0] = true;
        flood.add(0);
        while (!flood.isEmpty()) {
            int cell = flood.poll();
            int cx = cell % width;
            int cz = cell / width;
            int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] step : steps) {
                int nx = cx + step[0];
                int nz = cz + step[1];
                if (nx < 0 || nz < 0 || nx >= width || nz >= depth) continue;
                int next = nx + nz * width;
                if (outside[next] || wall[next]) continue;
                outside[next] = true;
                flood.add(next);
            }
        }
        int sizeX = width - 2;
        int sizeZ = depth - 2;
        BitSet interior = new BitSet(sizeX * sizeZ);
        for (int z = 0; z < sizeZ; z++) {
            for (int x = 0; x < sizeX; x++) {
                int cell = (x + 1) + (z + 1) * width;
                if (!outside[cell] && !wall[cell]) interior.set(x + z * sizeX);
            }
        }
        if (interior.isEmpty()) return new Scan(WorkerBlockEntity.Status.OPEN_BOUNDARY, null);
        return new Scan(WorkerBlockEntity.Status.RUNNING, new QuarryArea(minX, minZ, sizeX, sizeZ, quarry.getY(), interior));
    }

    /** The world x and z of column {@code index} at height {@code y}. */
    public BlockPos pos(int index, int atY) {
        return new BlockPos(minX + index % sizeX, atY, minZ + index / sizeX);
    }

    public int columns() {
        return interior.cardinality();
    }

    public boolean contains(int x, int z) {
        int dx = x - minX;
        int dz = z - minZ;
        return dx >= 0 && dz >= 0 && dx < sizeX && dz < sizeZ && interior.get(dx + dz * sizeX);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("MinX", minX);
        tag.putInt("MinZ", minZ);
        tag.putInt("SizeX", sizeX);
        tag.putInt("SizeZ", sizeZ);
        tag.putInt("Y", y);
        tag.putLongArray("Interior", interior.toLongArray());
        return tag;
    }

    public static QuarryArea load(CompoundTag tag) {
        return new QuarryArea(tag.getInt("MinX"), tag.getInt("MinZ"), tag.getInt("SizeX"), tag.getInt("SizeZ"), tag.getInt("Y"),
                BitSet.valueOf(tag.getLongArray("Interior")));
    }
}

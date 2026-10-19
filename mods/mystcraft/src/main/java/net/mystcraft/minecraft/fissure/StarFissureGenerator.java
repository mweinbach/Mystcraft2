package net.mystcraft.minecraft.fissure;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.mystcraft.integration.registry.StarFissureRegistry;
import net.mystcraft.minecraft.age.AgeSavedData;

/** Source WorldGenMystStarFissure noise, placed once near an Age's spawn at its modern minimum Y. */
public final class StarFissureGenerator {
    private static final long SALT = 0x5374617246697373L;
    private static final int MAX_COLUMNS = 1024;
    private static final int MAX_VOLUME = 262144;
    private StarFissureGenerator() {}

    public static void ensure(ServerLevel level, AgeSavedData.Entry age, AgeSavedData saved) {
        if (age.state().getBoolean("FissureGenerated")) return;
        List<BlockPos> columns;
        BlockPos origin;
        if (age.state().contains("FissureColumns", Tag.TAG_LIST)) {
            ListTag stored = age.state().getList("FissureColumns", Tag.TAG_COMPOUND);
            List<BlockPos> frozen = new ArrayList<>();
            for (int i = 0; i < stored.size(); i++) {
                CompoundTag pos = stored.getCompound(i);
                frozen.add(new BlockPos(pos.getInt("X"), pos.getInt("Y"), pos.getInt("Z")));
            }
            columns = List.copyOf(frozen);
            origin = new BlockPos(age.state().getInt("FissureX"), age.state().getInt("FissureY"), age.state().getInt("FissureZ"));
        } else {
            Random random = new Random(age.seed() ^ SALT);
            BlockPos spawn = level.getSharedSpawnPos();
            BlockPos first = new BlockPos(spawn.getX() + random.nextInt(16) + 8,
                    level.getMinBuildHeight(), spawn.getZ() + random.nextInt(16) + 8);
            List<BlockPos> original = footprint(first, random);
            Set<Long> loaded = new HashSet<>();
            columns = selectCandidate(original, candidate -> {
                // Bound chunk generation across the entire search, including failed candidates.
                Set<Long> needed = new HashSet<>();
                for (BlockPos pos : candidate) needed.add(net.minecraft.world.level.ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
                needed.addAll(loaded);
                if (needed.size() > 96) return false;
                loaded.addAll(needed);
                return preflight(level, candidate, false);
            });
            if (columns.isEmpty()) throw new IllegalStateException("No safe star fissure site within bounded search; existing block entities preserved");
            origin = columns.get(0); // Original source geometry always starts at its origin.
            ListTag stored = new ListTag();
            for (BlockPos pos : columns) {
                CompoundTag tag = new CompoundTag(); tag.putInt("X", pos.getX()); tag.putInt("Y", pos.getY()); tag.putInt("Z", pos.getZ()); stored.add(tag);
            }
            age.state().put("FissureColumns", stored);
            age.state().putInt("FissureX", origin.getX());
            age.state().putInt("FissureY", origin.getY());
            age.state().putInt("FissureZ", origin.getZ());
            saved.setDirty(); // Freeze the selected site before any partial placement can occur.
        }
        generate(level, columns);
        age.state().putBoolean("FissureGenerated", true);
        saved.setDirty();
    }

    /** First site preserves classic geometry; alternate sites translate it without rerolling noise. */
    public static List<BlockPos> selectCandidate(List<BlockPos> original, Predicate<List<BlockPos>> safe) {
        for (int ring = 0; ring <= 2; ring++) {
            for (int dx = -ring; dx <= ring; dx++) for (int dz = -ring; dz <= ring; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                int offsetX = dx * 48, offsetZ = dz * 48;
                List<BlockPos> candidate = original.stream().map(pos -> pos.offset(offsetX, 0, offsetZ)).toList();
                if (safe.test(candidate)) return candidate;
            }
        }
        return List.of();
    }

    public static List<BlockPos> footprint(BlockPos origin, long seed) { return footprint(origin, new Random(seed)); }
    private static List<BlockPos> footprint(BlockPos origin, Random random) {
        int length = random.nextInt(8) + 10;
        int[][] noise = new int[length][2];
        for (int row = 1; row < length; row++) {
            int scale = length + Math.min(row, length - row) + 1;
            noise[row][1] = (random.nextInt(scale) - (scale >> 1) + noise[row - 1][1]) / 2;
            noise[row][0] = (random.nextInt(scale) - (scale >> 1) + noise[row - 1][0]) / 2;
            if (noise[row][0] > noise[row][1]) {
                int swap = noise[row][0]; noise[row][0] = noise[row][1]; noise[row][1] = swap;
            }
            if (noise[row][0] > noise[row - 1][1]) noise[row][0] = noise[row - 1][1];
            if (noise[row][1] < noise[row - 1][0]) noise[row][1] = noise[row - 1][0];
        }
        List<BlockPos> result = new ArrayList<>();
        for (int row = 0; row < length; row++) for (int x = noise[row][0]; x <= noise[row][1]; x++) {
            result.add(origin.offset(x, 0, row * 2)); result.add(origin.offset(x, 0, row * 2 + 1));
        }
        if (result.size() > MAX_COLUMNS) throw new IllegalStateException("Star fissure exceeds column budget");
        return List.copyOf(result);
    }

    /** Preflight all bounds and block entities before changing any block. */
    public static void generate(ServerLevel level, List<BlockPos> columns) {
        preflight(level, columns, true);
        place(level, columns);
    }

    private static boolean preflight(ServerLevel level, List<BlockPos> columns, boolean failOnCollision) {
        if (columns.isEmpty() || columns.size() > MAX_COLUMNS) throw new IllegalArgumentException("Invalid fissure footprint");
        long volume = 0;
        for (BlockPos column : columns) {
            if (column.getY() < level.getMinBuildHeight() || column.getY() >= level.getMaxBuildHeight()
                    || !level.getWorldBorder().isWithinBounds(column)) throw new IllegalArgumentException("Fissure outside level bounds");
            volume += (long) level.getMaxBuildHeight() - column.getY();
            if (volume > MAX_VOLUME) throw new IllegalArgumentException("Fissure exceeds block work budget");
            level.getChunk(column.getX() >> 4, column.getZ() >> 4);
            for (int y = column.getY(); y < level.getMaxBuildHeight(); y++) {
                var entity = level.getBlockEntity(new BlockPos(column.getX(), y, column.getZ()));
                if (entity != null && !(y == column.getY() && entity instanceof StarFissureBlockEntity))
                    if (failOnCollision) throw new IllegalStateException("Fissure would destroy a block entity");
                    else return false;
            }
        }
        return true;
    }

    private static void place(ServerLevel level, List<BlockPos> columns) {
        for (BlockPos column : columns) {
            if (!level.getBlockState(column).is(StarFissureRegistry.STAR_FISSURE.get())
                    && !level.setBlock(column, StarFissureRegistry.STAR_FISSURE.get().defaultBlockState(), 3))
                throw new IllegalStateException("Fissure block placement failed");
            for (int y = column.getY() + 1; y < level.getMaxBuildHeight(); y++) {
                BlockPos at = new BlockPos(column.getX(), y, column.getZ());
                if (!level.isEmptyBlock(at) && !level.setBlock(at, Blocks.AIR.defaultBlockState(), 2))
                    throw new IllegalStateException("Fissure shaft clearing failed");
            }
        }
    }
}

package ports.buildcraft.energy;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import com.google.common.base.Suppliers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.FlowingFluid;

/**
 * Upstream {@code OilGenerator} and {@code OilGenStructure}. Each chunk rolls, from the world seed, whether
 * it holds a large (0.04%) or medium (0.1%) oil well, thirty times likelier in the oil biomes: an oil lake on the surface with tendrils, a sphere of
 * oil underground, a spout joining the two that gushes above the surface and, for large wells, a tube down
 * to an oil spring on the bottom of the world. A well spans several chunks, so every chunk generates the
 * parts of the wells around it that fall inside it.
 */
public class OilWellFeature extends Feature<NoneFeatureConfiguration> {
    /** Upstream {@code MAGIC_GEN_NUMBER}, which separates oil from other seeded generators. */
    private static final long MAGIC_GEN_NUMBER = 0xD0_46_B4_E4_0C_7D_07_CFL;
    /** Large enough that every well fits in this radius of chunks. */
    private static final int MAX_CHUNK_RADIUS = 5;
    /** Upstream {@code BCEnergyConfig} defaults, given there as percentages. */
    public static final double LARGE_PROBABILITY = 0.04 / 100;
    public static final double MEDIUM_PROBABILITY = 0.1 / 100;
    /** Upstream's bonus for {@code excessiveBiomes}. */
    public static final double EXCESSIVE_BONUS = 30;
    private static final int LARGE_SPOUT_MIN_HEIGHT = 10, LARGE_SPOUT_MAX_HEIGHT = 20;
    private static final int SMALL_SPOUT_MIN_HEIGHT = 6, SMALL_SPOUT_MAX_HEIGHT = 12;

    public enum WellType { LARGE, MEDIUM }

    /** One rolled well; {@link #structures()} lists its parts in generation order. */
    public record Well(WellType type, BlockPos centre, List<Structure> structures) {}

    public OilWellFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        if (context.chunkGenerator() instanceof FlatLevelSource) return false;
        WorldGenLevel level = context.level();
        ChunkPos chunk = new ChunkPos(context.origin());
        BoundingBox box = new BoundingBox(chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ(),
                chunk.getMaxBlockX(), level.getMaxBuildHeight() - 1, chunk.getMaxBlockZ());
        boolean placed = false;
        for (int dx = -MAX_CHUNK_RADIUS; dx <= MAX_CHUNK_RADIUS; dx++) {
            for (int dz = -MAX_CHUNK_RADIUS; dz <= MAX_CHUNK_RADIUS; dz++) {
                Well well = well(level.getSeed(), chunk.x + dx, chunk.z + dz, level.getMinBuildHeight(),
                        pos -> level.getBiome(pos).is(OilBiomes.EXCESSIVE_OIL));
                if (well == null) continue;
                for (Structure structure : well.structures()) placed |= structure.generate(level, box);
            }
        }
        return placed;
    }

    /**
     * Upstream {@code getStructures}: the well rolled for chunk {@code (cx, cz)}, or null. {@code excessive}
     * tells whether a position's biome has the bonus; it is only asked when a roll depends on it.
     */
    @Nullable
    public static Well well(long seed, int cx, int cz, int minY, Predicate<BlockPos> excessive) {
        Random rand = randomForChunk(seed, cx, cz);
        int x = cx * 16 + rand.nextInt(16);
        int z = cz * 16 + rand.nextInt(16);
        // Upstream reads the biome at y 0, the bottom of a 1.12 world whose biomes had no height.
        Supplier<Boolean> bonus = Suppliers.memoize(() -> excessive.test(new BlockPos(x, 62, z)));

        WellType type;
        if (rolls(rand.nextDouble(), LARGE_PROBABILITY, bonus)) type = WellType.LARGE;
        else if (rolls(rand.nextDouble(), MEDIUM_PROBABILITY, bonus)) type = WellType.MEDIUM;
        else return null;

        List<Structure> structures = new ArrayList<>();
        int lakeRadius = type == WellType.LARGE ? 4 : 2;
        int tendrilRadius = type == WellType.LARGE ? 25 + rand.nextInt(20) : 5 + rand.nextInt(10);
        structures.add(tendril(new BlockPos(x, 62, z), lakeRadius, tendrilRadius, rand));

        int wellY = 20 + rand.nextInt(10);
        int radius = type == WellType.LARGE ? 8 + rand.nextInt(9) : 4 + rand.nextInt(4);
        structures.add(sphere(new BlockPos(x, wellY, z), radius));

        int minHeight = type == WellType.LARGE ? LARGE_SPOUT_MIN_HEIGHT : SMALL_SPOUT_MIN_HEIGHT;
        int maxHeight = type == WellType.LARGE ? LARGE_SPOUT_MAX_HEIGHT : SMALL_SPOUT_MAX_HEIGHT;
        int spoutRadius = type == WellType.LARGE ? 1 : 0;
        structures.add(new Spout(new BlockPos(x, wellY, z), spoutRadius, minHeight + rand.nextInt(maxHeight - minHeight)));

        if (type == WellType.LARGE) {
            // Upstream's spring sits on y 0, the bottom of a 1.12 world.
            structures.add(tubeY(new BlockPos(x, minY + 1, z), wellY - minY, spoutRadius));
            structures.add(new Spring(new BlockPos(x, minY, z)));
        }
        return new Well(type, new BlockPos(x, wellY, z), structures);
    }

    private static boolean rolls(double roll, double probability, Supplier<Boolean> bonus) {
        return roll <= probability || roll <= probability * EXCESSIVE_BONUS && bonus.get();
    }

    /** Upstream {@code RandUtil.createRandomForChunk}. */
    private static Random randomForChunk(long seed, int cx, int cz) {
        Random worldRandom = new Random(seed);
        long xSeed = worldRandom.nextLong() >> 2 + 1L;
        long zSeed = worldRandom.nextLong() >> 2 + 1L;
        long chunkSeed = (xSeed * cx + zSeed * cz) ^ seed;
        return new Random(chunkSeed ^ MAGIC_GEN_NUMBER);
    }

    private static GenByPredicate tubeY(BlockPos base, int height, int radius) {
        BoundingBox box = new BoundingBox(base.getX() - radius, base.getY(), base.getZ() - radius,
                base.getX() + radius, base.getY() + height, base.getZ() + radius);
        int radiusSq = radius * radius;
        return new GenByPredicate(box, pos -> {
            int dx = pos.getX() - base.getX(), dz = pos.getZ() - base.getZ();
            return dx * dx + dz * dz <= radiusSq;
        });
    }

    private static Structure sphere(BlockPos centre, int radius) {
        BoundingBox box = new BoundingBox(centre.getX() - radius, centre.getY() - radius, centre.getZ() - radius,
                centre.getX() + radius, centre.getY() + radius, centre.getZ() + radius);
        double radiusSq = radius * radius + 0.01;
        return new GenByPredicate(box, pos -> pos.distSqr(centre) <= radiusSq);
    }

    /** Upstream {@code createTendril}: a round lake with branches that thin out with distance. */
    private static Structure tendril(BlockPos centre, int lakeRadius, int radius, Random rand) {
        int diameter = radius * 2 + 1;
        boolean[][] pattern = new boolean[diameter][diameter];
        int x = radius, z = radius;
        for (int dx = -lakeRadius; dx <= lakeRadius; dx++) {
            for (int dz = -lakeRadius; dz <= lakeRadius; dz++) {
                pattern[x + dx][z + dz] = dx * dx + dz * dz <= lakeRadius * lakeRadius;
            }
        }
        for (int w = 1; w < radius; w++) {
            float proba = (float) (radius - w + 4) / (float) (radius + 4);
            fillIfProba(rand, proba, x, z + w, pattern);
            fillIfProba(rand, proba, x, z - w, pattern);
            fillIfProba(rand, proba, x + w, z, pattern);
            fillIfProba(rand, proba, x - w, z, pattern);
            for (int i = 1; i <= w; i++) {
                fillIfProba(rand, proba, x + i, z + w, pattern);
                fillIfProba(rand, proba, x + i, z - w, pattern);
                fillIfProba(rand, proba, x + w, z + i, pattern);
                fillIfProba(rand, proba, x - w, z + i, pattern);
                fillIfProba(rand, proba, x - i, z + w, pattern);
                fillIfProba(rand, proba, x - i, z - w, pattern);
                fillIfProba(rand, proba, x + w, z - i, pattern);
                fillIfProba(rand, proba, x - w, z - i, pattern);
            }
        }
        int depth = rand.nextDouble() < 0.5 ? 1 : 2;
        return new PatternTerrainHeight(centre.offset(-radius, 0, -radius), pattern, depth);
    }

    private static void fillIfProba(Random rand, float proba, int x, int z, boolean[][] pattern) {
        if (rand.nextFloat() <= proba) {
            pattern[x][z] = isSet(pattern, x, z - 1) | isSet(pattern, x, z + 1) | isSet(pattern, x - 1, z) | isSet(pattern, x + 1, z);
        }
    }

    private static boolean isSet(boolean[][] pattern, int x, int z) {
        return x >= 0 && x < pattern.length && z >= 0 && z < pattern[x].length && pattern[x][z];
    }

    /** Places an oil source; exposed sources get a fluid tick so that they flow once the chunk is live. */
    static void setOil(WorldGenLevel level, BlockPos pos, boolean flow) {
        BCFluid oil = BCEnergy.OIL.heat(0).source();
        if (level.setBlock(pos, oil.defaultFluidState().createLegacyBlock(), 2) && flow) {
            level.scheduleTick(pos, (FlowingFluid) oil, oil.getTickDelay(level));
        }
    }

    /** One part of a well; it only writes blocks inside the area being generated. */
    public abstract static class Structure {
        public final BoundingBox box;

        Structure(BoundingBox box) {
            this.box = box;
        }

        final boolean generate(WorldGenLevel level, BoundingBox within) {
            if (!box.intersects(within)) return false;
            generateWithin(level, new BoundingBox(Math.max(box.minX(), within.minX()), Math.max(box.minY(), within.minY()),
                    Math.max(box.minZ(), within.minZ()), Math.min(box.maxX(), within.maxX()), Math.min(box.maxY(), within.maxY()),
                    Math.min(box.maxZ(), within.maxZ())));
            return true;
        }

        abstract void generateWithin(WorldGenLevel level, BoundingBox area);
    }

    static final class GenByPredicate extends Structure {
        private final Predicate<BlockPos> predicate;
        private boolean flow;

        GenByPredicate(BoundingBox box, Predicate<BlockPos> predicate) {
            super(box);
            this.predicate = predicate;
        }

        /** Gives every placed source a fluid tick, for oil that is exposed above ground. */
        GenByPredicate flowing() {
            flow = true;
            return this;
        }

        /** For parts placed by a structure that already owns the whole well column. */
        void generateWhole(WorldGenLevel level) {
            generateWithin(level, box);
        }

        @Override
        void generateWithin(WorldGenLevel level, BoundingBox area) {
            for (BlockPos pos : BlockPos.betweenClosed(area.minX(), area.minY(), area.minZ(), area.maxX(), area.maxY(), area.maxZ())) {
                if (predicate.test(pos)) setOil(level, pos, flow);
            }
        }
    }

    /** Upstream {@code PatternTerrainHeight}: replaces the top block of each column, clearing five blocks above. */
    static final class PatternTerrainHeight extends Structure {
        private final boolean[][] pattern;
        private final int depth;

        PatternTerrainHeight(BlockPos start, boolean[][] pattern, int depth) {
            super(new BoundingBox(start.getX(), Integer.MIN_VALUE / 2, start.getZ(),
                    start.getX() + pattern.length - 1, Integer.MAX_VALUE / 2, start.getZ() + pattern[0].length - 1));
            this.pattern = pattern;
            this.depth = depth;
        }

        @Override
        void generateWithin(WorldGenLevel level, BoundingBox area) {
            BlockState air = Blocks.AIR.defaultBlockState();
            for (int x = area.minX(); x <= area.maxX(); x++) {
                for (int z = area.minZ(); z <= area.maxZ(); z++) {
                    if (!pattern[x - box.minX()][z - box.minZ()]) continue;
                    BlockPos upper = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1, z);
                    for (int y = 0; y < 5; y++) level.setBlock(upper.above(y), air, 2);
                    for (int y = 0; y < depth; y++) setOil(level, upper.below(y), y == 0);
                }
            }
        }
    }

    /**
     * Upstream {@code Spout}: a tube from the well up to the surface, continued above it in narrowing tiers.
     * Its box is the centre column, so only the chunk holding the centre places it, as a whole.
     */
    static final class Spout extends Structure {
        private final BlockPos start;
        private final int radius;
        private final int height;

        Spout(BlockPos start, int radius, int height) {
            super(new BoundingBox(start.getX(), start.getY(), start.getZ(), start.getX(), Integer.MAX_VALUE / 2, start.getZ()));
            this.start = start;
            this.radius = radius;
            this.height = height;
        }

        @Override
        void generateWithin(WorldGenLevel level, BoundingBox area) {
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, start.getX(), start.getZ()) - 1;
            tubeY(start, top - start.getY(), radius).generateWhole(level);
            BlockPos base = new BlockPos(start.getX(), top, start.getZ());
            for (int r = radius; r >= 0; r--) {
                tubeY(base, height, r).flowing().generateWhole(level);
                base = base.above(height);
            }
        }
    }

    /** Upstream {@code Spring}: the oil spring under a large well. */
    static final class Spring extends Structure {
        private final BlockPos pos;

        Spring(BlockPos pos) {
            super(new BoundingBox(pos));
            this.pos = pos;
        }

        @Override
        void generateWithin(WorldGenLevel level, BoundingBox area) {
            if (level.setBlock(pos, BCEnergy.OIL_SPRING.get().defaultBlockState(), 2)) {
                level.scheduleTick(pos, BCEnergy.OIL_SPRING.get(), OilSpringBlock.TICK_RATE);
            }
        }
    }
}

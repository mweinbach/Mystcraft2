package net.mystcraft.minecraft.exploration;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.mystcraft.integration.registry.PortalRegistry;

/** Classic one-in-fifteen crystal formation population, confined to Mystcraft Ages. */
public final class ClassicCrystalFeature extends Feature<NoneFeatureConfiguration> {
    public ClassicCrystalFeature() { super(NoneFeatureConfiguration.CODEC); }
    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level(); RandomSource random = context.random();
        if (!usesCompatibilityPopulation(level.getLevel().getChunkSource().getGenerator())) return false;
        ResourceLocation dimension = level.getLevel().dimension().location();
        if (!dimension.getNamespace().equals("mystcraft") || !dimension.getPath().startsWith("age_") || random.nextInt(15) != 0) return false;
        int x = (context.origin().getX() & ~15) + random.nextInt(16) + 8;
        int z = (context.origin().getZ() & ~15) + random.nextInt(16) + 8;
        int y = level.getMinBuildHeight() + 1;
        // Source starts at the bottom and searches for the first air pocket, then roots two below it.
        while (y < level.getMaxBuildHeight() - 15 && !level.isEmptyBlock(new BlockPos(x, y, z))) y++;
        BlockPos root = new BlockPos(x, y - 2, z);
        if (y >= level.getMaxBuildHeight() - 15 || level.isEmptyBlock(root)) return false;
        return generateAt(level, root, random);
    }
    /** New frozen plans run their ordered, material-aware cryform population exactly once. */
    public static boolean usesCompatibilityPopulation(net.minecraft.world.level.chunk.ChunkGenerator generator) {
        return !(generator instanceof net.mystcraft.minecraft.age.generation.ClassicChunkGenerator classic) || !classic.usesAuthoredPopulation();
    }
    /** Same source line placement used by Age population and the controlled runtime fixture. */
    public boolean generateAt(WorldGenLevel level, BlockPos root, RandomSource random) {
        for (int count = 1 + random.nextInt(3); count > 0; count--) {
            double angle = Math.toRadians(random.nextFloat() * 140 + 15);
            int length = random.nextInt(7) + 6;
            BlockPos end = root.offset((int) (length * Math.cos(angle)), (int) (length * Math.sin(angle)), random.nextInt(7) - 3);
            line(level, root, end);
        }
        return true;
    }
    private static void line(WorldGenLevel level, BlockPos start, BlockPos end) {
        int dx = end.getX() - start.getX(), dy = end.getY() - start.getY(), dz = end.getZ() - start.getZ();
        int steps = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        for (int i = 0; i <= steps; i++) {
            double t = steps == 0 ? 0 : (double) i / steps;
            BlockPos center = new BlockPos(Mth.floor(start.getX() + dx * t + 0.5),
                    Mth.floor(start.getY() + dy * t + 0.5), Mth.floor(start.getZ() + dz * t + 0.5));
            place(level, center);
            for (Direction direction : Direction.values()) place(level, center.relative(direction));
        }
    }
    private static void place(WorldGenLevel level, BlockPos pos) {
        net.mystcraft.minecraft.age.generation.DeferredCrystalWrites.place(level, pos,
                PortalRegistry.CRYSTAL.get().defaultBlockState(), 2);
    }
}

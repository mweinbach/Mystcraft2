package ports.jurassicraft.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import ports.jurassicraft.egg.FossilNestRegistry;

/** Upstream's independent underground trackways, separate from fossil nests. */
public final class FossilTrackwayFeature extends Feature<NoneFeatureConfiguration> {
    public FossilTrackwayFeature() { super(NoneFeatureConfiguration.CODEC); }

    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        if (random.nextInt(level.getBiome(origin).is(BiomeTags.IS_RIVER) ? 10 : 20) != 0) return false;
        int variant = random.nextInt(3);
        boolean placed = false;
        for (int count = 1 + random.nextInt(2); count > 0; count--) {
            Direction direction = Direction.Plane.HORIZONTAL.getRandomDirection(random);
            BlockState track = FossilNestRegistry.trackway(variant).get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, direction);
            BlockPos start = origin.offset(random.nextInt(9) - 4, 0, random.nextInt(9) - 4);
            int length = 3 + random.nextInt(2);
            placed |= placeTrack(level, start, direction, track, length);
        }
        return placed;
    }

    static boolean placeTrack(WorldGenLevel level, BlockPos start, Direction direction, BlockState track, int length) {
        boolean placed = false;
        for (int i = 0; i < length; i++) {
            BlockPos pos = start.relative(direction, i);
            if (level.ensureCanWrite(pos) && level.getBlockState(pos).is(Blocks.STONE))
                placed |= level.setBlock(pos, track, 2);
        }
        return placed;
    }
}

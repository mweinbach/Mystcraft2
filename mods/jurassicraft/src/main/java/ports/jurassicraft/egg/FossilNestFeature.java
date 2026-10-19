package ports.jurassicraft.egg;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** The small underground nest/trackway deposit from the 1.12 world generator. */
public final class FossilNestFeature extends Feature<NoneFeatureConfiguration> {
    private static final TagKey<Biome> SANDY = TagKey.create(Registry.BIOME_REGISTRY, new ResourceLocation("forge", "is_sandy"));
    private static final TagKey<Block> REPLACEABLE = BlockTags.create(new ResourceLocation("jurassicraft", "fossil_nest_replaceable"));

    public FossilNestFeature() { super(NoneFeatureConfiguration.CODEC); }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        var biome = level.getBiome(origin);
        boolean favored = biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_HILL)
                || biome.is(BiomeTags.IS_BADLANDS) || biome.is(SANDY);
        if (random.nextInt(favored ? 30 : 100) != 0) return false;

        int variant = random.nextInt(3);
        BlockState trackway = FossilNestRegistry.trackway(random.nextInt(3)).get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(random));
        BlockState nest = FossilNestRegistry.nest(variant, false).get().defaultBlockState();
        int size = random.nextInt(3) + 6;
        boolean placed = false;
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                BlockPos pos = origin.offset(x, 0, z);
                if (!replaceable(level, pos) || random.nextFloat() >= 0.8F) continue;
                BlockState next;
                if (random.nextFloat() < 0.1F) next = trackway;
                else if (random.nextFloat() < 0.6F) next = Blocks.GRAVEL.defaultBlockState();
                else next = (random.nextBoolean() ? Blocks.WHITE_TERRACOTTA : Blocks.LIGHT_GRAY_TERRACOTTA).defaultBlockState();
                placed |= level.setBlock(pos, next, 2);
            }
        }
        int count = random.nextInt(2) + 1;
        for (int i = 0; i < count; i++) {
            BlockPos pos = origin.offset(random.nextInt(size), 0, random.nextInt(size));
            if (replaceable(level, pos)) placed |= level.setBlock(pos, nest, 2);
        }
        return placed;
    }

    private static boolean replaceable(WorldGenLevel level, BlockPos pos) {
        if (!level.ensureCanWrite(pos)) return false;
        BlockState state = level.getBlockState(pos);
        return state.is(REPLACEABLE) && state.getFluidState().isEmpty() && !state.hasBlockEntity()
                && state.getDestroySpeed(level, pos) >= 0;
    }
}

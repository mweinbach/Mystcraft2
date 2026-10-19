package ports.jurassicraft.plants;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import ports.jurassicraft.content.JurassicContent;

/** After coal veins generate, substitute fossils for one in ten tagged coal ores. */
final class CoalPlantFossilsFeature extends Feature<NoneFeatureConfiguration> {
    private static final TagKey<Block> COAL = TagKey.create(Registry.BLOCK_REGISTRY, new ResourceLocation("jurassicraft", "coal_ores"));
    CoalPlantFossilsFeature() { super(NoneFeatureConfiguration.CODEC); }

    static boolean isCoal(BlockState state) {
        return state.is(COAL);
    }

    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        ChunkAccess chunk = level.getChunk(origin);
        int minX = chunk.getPos().getMinBlockX(), minZ = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean placed = false;
        for (LevelChunkSection section : chunk.getSections()) {
            // Most sections contain no coal; inspect palettes before scanning 4096 blocks.
            if (!section.maybeHas(CoalPlantFossilsFeature::isCoal)) continue;
            int minY = section.bottomBlockY();
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                if (!isCoal(section.getBlockState(x, y, z))) continue;
                pos.set(minX + x, minY + y, minZ + z);
                placed |= tryReplace(level, pos, context.random());
            }
        }
        return placed;
    }

    static boolean tryReplace(WorldGenLevel level, BlockPos pos, RandomSource random) {
        if (!level.ensureCanWrite(pos) || !isCoal(level.getBlockState(pos)) || random.nextInt(10) != 0) return false;
        return level.setBlock(pos, JurassicContent.PLANT_FOSSIL_BLOCK.get().defaultBlockState(), Block.UPDATE_CLIENTS);
    }
}

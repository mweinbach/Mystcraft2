package ports.jurassicraft.plants;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

final class PeatFeature extends Feature<NoneFeatureConfiguration> {
    PeatFeature() { super(NoneFeatureConfiguration.CODEC); }
    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos ground = context.origin().below();
        if (level.getBlockState(ground).is(Blocks.GRASS_BLOCK)) ground = ground.below();
        if (!level.getBlockState(ground).is(Blocks.DIRT)) return false;
        return level.setBlock(ground, PlantRegistry.PEAT.get().defaultBlockState(), Block.UPDATE_CLIENTS);
    }
}

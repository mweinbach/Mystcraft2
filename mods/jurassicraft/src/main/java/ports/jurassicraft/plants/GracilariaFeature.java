package ports.jurassicraft.plants;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

final class GracilariaFeature extends Feature<NoneFeatureConfiguration> {
    GracilariaFeature() { super(NoneFeatureConfiguration.CODEC); }
    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos pos = context.origin();
        BlockState state = PlantRegistry.GRACILARIA_SEAWEED.get().defaultBlockState();
        if (level.getBlockState(pos).is(Blocks.WATER) && state.canSurvive(level, pos)) {
            return level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
        return false;
    }
}

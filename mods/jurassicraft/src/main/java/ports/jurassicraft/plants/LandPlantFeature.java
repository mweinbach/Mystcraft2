package ports.jurassicraft.plants;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.BlockStateConfiguration;

/** Surface vegetation never replaces blocks already present in the world. */
final class LandPlantFeature extends Feature<BlockStateConfiguration> {
    LandPlantFeature() { super(BlockStateConfiguration.CODEC); }
    @Override
    public boolean place(FeaturePlaceContext<BlockStateConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos pos = context.origin();
        BlockState state = context.config().state;
        if (!level.isEmptyBlock(pos) || !state.canSurvive(level, pos)) return false;
        if (state.getBlock() instanceof DoublePlantBlock) {
            if (!level.isEmptyBlock(pos.above())) return false;
            DoublePlantBlock.placeAt(level, state, pos, Block.UPDATE_CLIENTS);
            return true;
        }
        if (!Block.canSupportRigidBlock(level, pos.below())) return false;
        return level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }
}

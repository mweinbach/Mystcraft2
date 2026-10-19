package ports.jurassicraft.plants;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SeagrassBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/** Underwater plants require water above and spread onto soft sediment. */
public class AncientAquaticPlantBlock extends SeagrassBlock {
    public AncientAquaticPlantBlock(BlockBehaviour.Properties properties) { super(properties); }

    @Override
    protected boolean mayPlaceOn(BlockState ground, BlockGetter level, BlockPos pos) {
        return ground.is(Blocks.SAND) || ground.is(Blocks.CLAY) || ground.is(Blocks.GRAVEL)
                || ground.is(Blocks.DIRT);
    }
    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return mayPlaceOn(level.getBlockState(pos.below()), level, pos.below()) && level.isWaterAt(pos.above());
    }
    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                  BlockPos pos, BlockPos neighborPos) {
        // Returning air lets the normal neighbor-update destruction path drop
        // the plant; the block's fluid state restores the water afterwards.
        if (!state.canSurvive(level, pos)) return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }
    @Override
    public boolean isValidBonemealTarget(BlockGetter level, BlockPos pos, BlockState state, boolean clientSide) {
        return false;
    }
    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!PlantGameRules.canSpread(level) || !PlantChunks.isAreaLoaded(level, pos, 5)) return;
        int light = level.getRawBrightness(pos, 0);
        if (random.nextInt(100) >= (light >= 5 && light <= 11 ? 11 : 2)) return;
        int count = 0;
        for (BlockPos near : BlockPos.betweenClosed(pos.offset(-4, -3, -4), pos.offset(4, 3, 4))) {
            if (level.getBlockState(near).is(this) && ++count >= 5) return;
        }
        for (int attempt = 0; attempt < 3; attempt++) {
            int x = random.nextInt(9) - 4, z = random.nextInt(9) - 4;
            for (int y = -3; y <= 3; y++) {
                BlockPos target = pos.offset(x, y, z);
                if (level.getBlockState(target).is(Blocks.WATER)
                        && level.getFluidState(target).getType() == Fluids.WATER && state.canSurvive(level, target)) {
                    level.setBlock(target, state, Block.UPDATE_ALL);
                    return;
                }
            }
        }
    }
}

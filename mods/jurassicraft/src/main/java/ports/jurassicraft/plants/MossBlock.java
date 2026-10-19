package ports.jurassicraft.plants;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Moss is a thin surface layer and renews itself over nearby peat. */
final class MossBlock extends CarpetBlock {
    MossBlock() { super(BlockBehaviour.Properties.copy(Blocks.MOSS_CARPET).strength(0.2F).randomTicks()); }
    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // The original moss spreads only on peat, independently of plantSpreading.
        if (!level.getBlockState(pos.below()).is(PlantRegistry.PEAT.get()) || random.nextInt(2) != 0 || !level.isAreaLoaded(pos, 4)) return;
        for (int attempt = 0; attempt < 3; attempt++) {
            int x = random.nextInt(7) - 3, z = random.nextInt(7) - 3;
            for (int y = -3; y <= 3; y++) {
                BlockPos target = pos.offset(x, y, z);
                if (!level.isEmptyBlock(target) || !level.getBlockState(target.below()).is(PlantRegistry.PEAT.get())) continue;
                int density = 0;
                for (BlockPos nearby : BlockPos.betweenClosed(target.offset(-2, -3, -2), target.offset(2, 3, 2))) {
                    if (level.getBlockState(nearby).is(this) && ++density >= 8) return;
                }
                level.setBlock(target, state, Block.UPDATE_ALL);
                return;
            }
        }
    }
}

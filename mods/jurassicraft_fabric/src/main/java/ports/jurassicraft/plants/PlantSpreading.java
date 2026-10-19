package ports.jurassicraft.plants;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Limited, water-adjacent spreading, as in the original ancient plants. */
final class PlantSpreading {
    private PlantSpreading() {}

    static void trySpread(ServerLevel level, BlockPos pos, BlockState state, RandomSource random, boolean tall) {
        if (!PlantGameRules.canSpread(level) || random.nextInt(16) != 0 || !PlantChunks.isAreaLoaded(level, pos, 9) || level.getRawBrightness(pos, 0) < 5)
            return;
        boolean water = false;
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-8, -3, -8), pos.offset(8, 3, 8))) {
            if (level.isWaterAt(p) && p.distSqr(pos) < 81) { water = true; break; }
        }
        if (!water) return;
        for (int attempt = 0; attempt < 3; attempt++) {
            int x = random.nextInt(13) - 6, z = random.nextInt(13) - 6;
            for (int y = -3; y <= 3; y++) {
                BlockPos target = pos.offset(x, y, z);
                if (!level.isEmptyBlock(target) || (tall && !level.isEmptyBlock(target.above())))
                    continue;
                if (!state.canSurvive(level, target)) continue;
                int neighbors = 0;
                for (BlockPos p : BlockPos.betweenClosed(target.offset(-2, -3, -2), target.offset(2, 3, 2))) {
                    if (level.getBlockState(p).getBlock() instanceof net.minecraft.world.level.block.BushBlock && ++neighbors >= 4)
                        return;
                }
                BlockState seedling = state.getBlock().defaultBlockState();
                if (tall) DoublePlantBlock.placeAt(level, seedling, target, Block.UPDATE_ALL);
                else level.setBlock(target, seedling, Block.UPDATE_ALL);
                return;
            }
        }
    }
}

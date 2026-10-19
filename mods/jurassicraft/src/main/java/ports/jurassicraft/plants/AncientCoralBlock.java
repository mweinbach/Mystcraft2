package ports.jurassicraft.plants;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Prehistoric corals share a density limit and require light to spread. */
final class AncientCoralBlock extends AncientAquaticPlantBlock {
    private static final int RADIUS = 6;
    private static final int MAX_COLONY = 5;

    AncientCoralBlock(BlockBehaviour.Properties properties) { super(properties); }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!PlantGameRules.canSpread(level) || !PlantChunks.isAreaLoaded(level, pos, RADIUS + 1)
                || !passesSpreadRoll(level.getRawBrightness(pos, 0), random) || !hasRoom(level, pos)) return;
        tryPlaceNearby(level, pos, state, random);
    }

    static boolean tryPlaceNearby(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        for (int attempt = 0; attempt < 3; attempt++) {
            int x = random.nextInt(RADIUS * 2) - RADIUS;
            int z = random.nextInt(RADIUS * 2) - RADIUS;
            for (int y = -RADIUS; y < 2; y++) {
                BlockPos target = pos.offset(x, y, z);
                if (level.isOutsideBuildHeight(target) || level.isOutsideBuildHeight(target.above())) continue;
                if (!level.getBlockState(target).is(Blocks.WATER) || !level.getFluidState(target).isSource()
                        || !level.getBlockState(target.above()).is(Blocks.WATER)
                        || !level.getFluidState(target.above()).isSource()
                        || !state.canSurvive(level, target)) continue;
                return level.setBlock(target, state, Block.UPDATE_ALL);
            }
        }
        return false;
    }

    static boolean passesSpreadRoll(int light, RandomSource random) {
        return light >= 5 && random.nextInt((15 - light) / 2 + 10) == 0;
    }

    static boolean hasRoom(BlockGetter level, BlockPos center) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-RADIUS, -3, -RADIUS), center.offset(RADIUS, 3, RADIUS))) {
            if (level.getBlockState(pos).getBlock() instanceof AncientCoralBlock && ++count >= MAX_COLONY) return false;
        }
        return true;
    }
}

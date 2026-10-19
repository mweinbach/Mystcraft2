package ports.jurassicraft.plants;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraftforge.common.Tags;
import ports.jurassicraft.config.JurassicConfig;

/** Damp peat dries into dirt away from swamp biomes, water and rain. */
final class PeatBlock extends Block {
    private static final IntegerProperty MOISTURE = BlockStateProperties.MOISTURE;
    PeatBlock() {
        super(BlockBehaviour.Properties.copy(Blocks.DIRT).randomTicks());
        registerDefaultState(stateDefinition.any().setValue(MOISTURE, 0));
    }
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(MOISTURE); }
    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.isAreaLoaded(pos, 4)) return;
        boolean swamp = level.getBiome(pos).is(Tags.Biomes.IS_SWAMP);
        boolean wet = swamp || level.isRainingAt(pos.above());
        if (!wet) {
            for (BlockPos p : BlockPos.betweenClosed(pos.offset(-4, 0, -4), pos.offset(4, 1, 4))) {
                if (level.getFluidState(p).is(FluidTags.WATER)) { wet = true; break; }
            }
        }
        int moisture = state.getValue(MOISTURE);
        if (wet && moisture != 7) level.setBlock(pos, state.setValue(MOISTURE, 7), Block.UPDATE_CLIENTS);
        else if (!wet && moisture > 0) level.setBlock(pos, state.setValue(MOISTURE, moisture - 1), Block.UPDATE_CLIENTS);
        else if (!wet) level.setBlock(pos, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
        if (swamp) {
            int distance = JurassicConfig.PEAT_SPREAD_DISTANCE.get();
            if (distance == 0) return;
            for (int i = 0; i < 4; i++) {
                // The old generator samples a 0..distance cube in each axis;
                // a larger value makes a near-surface match less likely.
                BlockPos target = pos.offset(random.nextInt(distance + 1), random.nextInt(distance + 1), random.nextInt(distance + 1));
                spreadTo(level, target);
            }
        }
    }

    static boolean spreadTo(ServerLevel level, BlockPos target) {
        // Never load a new chunk just to attempt peat spreading.
        if (level.isOutsideBuildHeight(target) || !level.hasChunkAt(target)) return false;
        if (!level.getBlockState(target).is(Blocks.DIRT)) return false;
        if (!level.getBlockState(target.above()).is(Blocks.GRASS_BLOCK)
                && !level.getBlockState(target.above(2)).is(Blocks.GRASS_BLOCK)
                && !level.getBlockState(target.above(3)).is(Blocks.GRASS_BLOCK)) return false;
        return level.setBlock(target, PlantRegistry.PEAT.get().defaultBlockState(), Block.UPDATE_ALL);
    }
}

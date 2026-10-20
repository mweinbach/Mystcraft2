package ports.buildcraft.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Upstream {@code BlockSpring} of type {@code OIL}: an unbreakable bedrock-like block under large oil wells
 * that, every 6000 ticks and on random ticks, has a one in eight chance to put an oil source above itself
 * when that space is empty.
 */
public class OilSpringBlock extends Block {
    /** Upstream {@code EnumSpring.OIL}. */
    public static final int TICK_RATE = 6000;
    public static final int CHANCE = 8;

    public OilSpringBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        level.scheduleTick(pos, this, TICK_RATE);
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        generate(level, pos, random);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.scheduleTick(pos, this, TICK_RATE);
        generate(level, pos, random);
    }

    private static void generate(ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos above = pos.above();
        if (level.isEmptyBlock(above) && random.nextInt(CHANCE) == 0) {
            level.setBlockAndUpdate(above, BCEnergy.OIL.heat(0).block().defaultBlockState());
        }
    }
}

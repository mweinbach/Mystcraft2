package ports.buildcraft.core;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Upstream {@code BlockSpring} with one {@code EnumSpring} type per block: an unbreakable bedrock-like
 * block that, every {@code tickRate} ticks and on random ticks, puts its liquid above itself when that
 * space is air, always or with a one in {@code chance} chance.
 */
public class SpringBlock extends Block {
    public static final int ALWAYS = -1;
    private final int tickRate;
    private final int chance;
    private final Supplier<BlockState> liquid;

    public SpringBlock(Properties properties, int tickRate, int chance, Supplier<BlockState> liquid) {
        super(properties);
        this.tickRate = tickRate;
        this.chance = chance;
        this.liquid = liquid;
    }

    public int tickRate() {
        return tickRate;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        level.scheduleTick(pos, this, tickRate);
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
        level.scheduleTick(pos, this, tickRate);
        generate(level, pos, random);
    }

    private void generate(ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos above = pos.above();
        if (level.isEmptyBlock(above) && (chance == ALWAYS || random.nextInt(chance) == 0)) {
            level.setBlockAndUpdate(above, liquid.get());
        }
    }
}

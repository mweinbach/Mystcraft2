package ports.buildcraft.transport;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import ports.buildcraft.transport.behaviour.LimiterBehaviour;

/**
 * A pipe block for upstream's {@code PipeBehaviourLimiter} pipes, whose texture follows their power limit
 * ({@code getTextureIndex}). The limit's shift is block state so that a vanilla multipart model can pick it.
 */
public class LimiterPipeBlock extends PipeBlock {
    public static final IntegerProperty LIMIT_SHIFT = IntegerProperty.create("limit_shift", 0, LimiterBehaviour.MAX_SHIFT);

    public LimiterPipeBlock(Properties properties, PipeDefinition definition) {
        super(properties, definition);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIMIT_SHIFT);
    }
}

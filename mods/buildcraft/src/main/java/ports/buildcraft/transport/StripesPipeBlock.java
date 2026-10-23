package ports.buildcraft.transport;

import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * The stripes pipe's block: upstream {@code PipeBehaviourStripes} drew a gold rod on the open end it acts on. The
 * end is block state so that a vanilla multipart model can draw the rod.
 */
public class StripesPipeBlock extends PipeBlock {
    public enum Facing implements StringRepresentable {
        NONE, DOWN, UP, NORTH, SOUTH, WEST, EAST;

        public static Facing of(@Nullable Direction direction) {
            return direction == null ? NONE : values()[direction.ordinal() + 1];
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final EnumProperty<Facing> DIRECTION = EnumProperty.create("direction", Facing.class);

    public StripesPipeBlock(Properties properties, PipeDefinition definition) {
        super(properties, definition);
        registerDefaultState(defaultBlockState().setValue(DIRECTION, Facing.NONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(DIRECTION);
    }
}

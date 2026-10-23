package ports.buildcraft.transport;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * A pipe whose model follows its behaviour's colour (upstream {@code getTextureIndex} of the lapis and daizuli
 * pipes). The colour is block state so that a vanilla multipart model can pick the texture.
 */
public class ColouredPipeBlock extends PipeBlock {
    public static final EnumProperty<DyeColor> COLOUR = EnumProperty.create("colour", DyeColor.class);

    public ColouredPipeBlock(Properties properties, PipeDefinition definition) {
        super(properties, definition);
        registerDefaultState(defaultBlockState().setValue(COLOUR, DyeColor.WHITE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(COLOUR);
    }
}

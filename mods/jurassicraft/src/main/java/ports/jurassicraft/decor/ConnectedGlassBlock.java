package ports.jurassicraft.decor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.GlassBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** The original glass joins across all six sides and removes the corresponding frame. */
public final class ConnectedGlassBlock extends GlassBlock {
    private static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    private static final BooleanProperty EAST = BlockStateProperties.EAST;
    private static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    private static final BooleanProperty WEST = BlockStateProperties.WEST;
    private static final BooleanProperty UP = BlockStateProperties.UP;
    private static final BooleanProperty DOWN = BlockStateProperties.DOWN;

    public ConnectedGlassBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(NORTH, false).setValue(EAST, false)
                .setValue(SOUTH, false).setValue(WEST, false).setValue(UP, false).setValue(DOWN, false));
    }

    private static BooleanProperty property(Direction direction) {
        return switch (direction) {
            case NORTH -> NORTH; case EAST -> EAST; case SOUTH -> SOUTH;
            case WEST -> WEST; case UP -> UP; case DOWN -> DOWN;
        };
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState result = defaultBlockState();
        for (Direction side : Direction.values()) {
            result = result.setValue(property(side), context.getLevel().getBlockState(context.getClickedPos().relative(side)).is(this));
        }
        return result;
    }

    @Override public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state.setValue(property(direction), neighbor.is(this));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    @Override public BlockState rotate(BlockState state, Rotation rotation) {
        BlockState result = state;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            result = result.setValue(property(rotation.rotate(side)), state.getValue(property(side)));
        }
        return result;
    }

    @Override public BlockState mirror(BlockState state, Mirror mirror) {
        BlockState result = state;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            result = result.setValue(property(mirror.mirror(side)), state.getValue(property(side)));
        }
        return result;
    }
}

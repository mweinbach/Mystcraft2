package ports.buildcraft.core;

import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import ports.buildcraft.lib.marker.MarkerBlockEntity;

/**
 * Upstream {@code BlockMarkerBase}: a torch-like marker standing out from the solid face it was placed
 * on, with no collision. The wrench turns it to another face it can stand on.
 */
public abstract class MarkerBlock extends BaseEntityBlock implements CustomRotationHandler {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        double halfWidth = 0.1;
        double h = 0.65;
        double nw = 0.5 - halfWidth;
        double pw = 0.5 + halfWidth;
        double ih = 1 - h;
        SHAPES.put(Direction.DOWN, Shapes.box(nw, ih, nw, pw, 1, pw));
        SHAPES.put(Direction.UP, Shapes.box(nw, 0, nw, pw, h, pw));
        SHAPES.put(Direction.SOUTH, Shapes.box(nw, nw, 0, pw, pw, h));
        SHAPES.put(Direction.NORTH, Shapes.box(nw, nw, ih, pw, pw, 1));
        SHAPES.put(Direction.EAST, Shapes.box(0, nw, nw, h, pw, pw));
        SHAPES.put(Direction.WEST, Shapes.box(ih, nw, nw, 1, pw, pw));
    }

    protected MarkerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getClickedFace());
        return canSurvive(state, context.getLevel(), context.getClickedPos()) ? state : null;
    }

    /** Upstream {@code canPlaceBlockOnSide}: the block behind the marker must have a solid face towards it. */
    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction side = state.getValue(FACING);
        BlockPos support = pos.relative(side.getOpposite());
        return level.getBlockState(support).isFaceSturdy(level, support, side);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        if (!canSurvive(state, level, pos)) level.destroyBlock(pos, true);
    }

    @Override
    public InteractionResult attemptRotation(Level level, BlockPos pos, BlockState state, Direction sideWrenched) {
        Direction current = state.getValue(FACING);
        for (int i = Wrenches.ROTATE_FACING.length; i > 1; i--) {
            current = Wrenches.nextFacing(current);
            BlockState rotated = state.setValue(FACING, current);
            if (canSurvive(rotated, level, pos)) {
                level.setBlockAndUpdate(pos, rotated);
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.FAIL;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof MarkerBlockEntity<?> marker) marker.onBroken();
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}

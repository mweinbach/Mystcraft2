package ports.jurassicraft.fence;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Pole lights follow the redstone at their base, and arms meet adjacent wires. */
public final class FencePoleBlock extends Block {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    private static final VoxelShape SHAPE = box(5.48, 0, 5.48, 10.52, 16, 10.52);
    public FencePoleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false)
                .setValue(BlockStateProperties.NORTH, false).setValue(BlockStateProperties.SOUTH, false)
                .setValue(BlockStateProperties.EAST, false).setValue(BlockStateProperties.WEST, false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE, BlockStateProperties.NORTH, BlockStateProperties.SOUTH, BlockStateProperties.EAST, BlockStateProperties.WEST);
    }
    static BooleanProperty directionProperty(Direction direction) {
        return switch (direction) {
            case NORTH -> BlockStateProperties.NORTH;
            case SOUTH -> BlockStateProperties.SOUTH;
            case EAST -> BlockStateProperties.EAST;
            case WEST -> BlockStateProperties.WEST;
            default -> throw new IllegalArgumentException("horizontal direction required");
        };
    }
    private static BlockState updated(LevelAccessor level, BlockPos pos, BlockState state) {
        BlockState below = level.getBlockState(pos.below());
        boolean active = below.is(FenceRegistry.POLE.get()) && below.getValue(ACTIVE)
                || below.is(FenceRegistry.BASE.get()) && level instanceof Level world && world.hasNeighborSignal(pos.below());
        state = state.setValue(ACTIVE, active);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = pos.relative(direction);
            BlockState other = level.getBlockState(neighbor);
            BlockState lower = level.getBlockState(neighbor.below());
            boolean connected = FenceWireBlock.connects(other) || lower.is(FenceRegistry.WIRE.get())
                    && lower.getValue(FenceWireBlock.UP) == direction.getOpposite();
            state = state.setValue(directionProperty(direction), connected);
        }
        return state;
    }
    static void refresh(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof FencePoleBlock)) return;
        BlockState updated = updated(level, pos, state);
        if (updated != state) level.setBlock(pos, updated, 3);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return updated(context.getLevel(), context.getClickedPos(), defaultBlockState());
    }
    @Override public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return updated(level, pos, state);
    }
    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (!level.isClientSide && !oldState.is(this)) { refresh(level, pos); FenceWireBlock.scheduleNearby(level, pos); }
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        super.onRemove(state, level, pos, next, moving);
        if (!level.isClientSide && !next.is(this)) FenceWireBlock.scheduleNearby(level, pos);
    }
    @Override public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        if (!level.isClientSide) refresh(level, pos);
    }
    @Override public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) { refresh(level, pos); }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
}

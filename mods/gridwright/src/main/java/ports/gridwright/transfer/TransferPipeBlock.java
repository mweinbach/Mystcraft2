package ports.gridwright.transfer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/**
 * A transfer pipe. Pipes join each other, the back and sides of nodes, and any inventory or tank
 * beside them, which is where items and fluids leave the network. Its six sides are shown as block
 * state; the network itself is found by walking pipes, not by reading these properties.
 */
public class TransferPipeBlock extends Block {

    public static final Map<Direction, BooleanProperty> SIDES = PipeBlock.PROPERTY_BY_DIRECTION;
    private static final VoxelShape[] SHAPES = shapes();

    public TransferPipeBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any();
        for (BooleanProperty side : SIDES.values()) state = state.setValue(side, false);
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SIDES.values().toArray(BooleanProperty[]::new));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return connections(context.getLevel(), context.getClickedPos(), defaultBlockState());
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (!(level instanceof Level real)) return state;
        boolean connected = connects(real, pos, direction);
        if (connected != state.getValue(SIDES.get(direction))) PipeNetwork.changed();
        return state.setValue(SIDES.get(direction), connected);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, block, from, moving);
        PipeNetwork.changed();
        if (level.isClientSide) return;
        BlockState updated = connections(level, pos, state);
        if (updated != state) level.setBlock(pos, updated, Block.UPDATE_CLIENTS);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        PipeNetwork.changed();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock())) PipeNetwork.changed();
        super.onRemove(state, level, pos, replacement, moving);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int mask = 0;
        for (Direction direction : Direction.values()) {
            if (state.getValue(SIDES.get(direction))) mask |= 1 << direction.ordinal();
        }
        return SHAPES[mask];
    }

    private static BlockState connections(Level level, BlockPos pos, BlockState state) {
        for (Direction direction : Direction.values()) state = state.setValue(SIDES.get(direction), connects(level, pos, direction));
        return state;
    }

    /**
     * Whether the pipe at {@code pos} joins what is on its {@code direction} side. Inventories and tanks
     * are only looked up on the server; the client shows what the server sends.
     */
    public static boolean connects(Level level, BlockPos pos, Direction direction) {
        BlockPos neighbour = pos.relative(direction);
        BlockState state = level.getBlockState(neighbour);
        if (state.getBlock() instanceof TransferPipeBlock) return true;
        if (state.getBlock() instanceof NodeBlock) return state.getValue(NodeBlock.FACING) != direction.getOpposite();
        if (level.isClientSide) return false;
        Direction face = direction.getOpposite();
        return TransferPlatform.hasItems(level, neighbour, face) || TransferPlatform.hasFluids(level, neighbour, face);
    }

    private static VoxelShape[] shapes() {
        VoxelShape core = Block.box(6, 6, 6, 10, 10, 10);
        VoxelShape[] arms = new VoxelShape[6];
        for (Direction direction : Direction.values()) {
            arms[direction.ordinal()] = switch (direction) {
                case DOWN -> Block.box(6, 0, 6, 10, 6, 10);
                case UP -> Block.box(6, 10, 6, 10, 16, 10);
                case NORTH -> Block.box(6, 6, 0, 10, 10, 6);
                case SOUTH -> Block.box(6, 6, 10, 10, 10, 16);
                case WEST -> Block.box(0, 6, 6, 6, 10, 10);
                case EAST -> Block.box(10, 6, 6, 16, 10, 10);
            };
        }
        VoxelShape[] shapes = new VoxelShape[64];
        for (int mask = 0; mask < 64; mask++) {
            VoxelShape shape = core;
            for (int side = 0; side < 6; side++) {
                if ((mask & (1 << side)) != 0) shape = Shapes.or(shape, arms[side]);
            }
            shapes[mask] = shape.optimize();
        }
        return shapes;
    }
}

package ports.buildcraft.builders;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Upstream {@code BlockFrame}: the quarry's gantry, which joins to neighbouring frames and the quarry. */
public class FrameBlock extends Block {
    public static final Map<Direction, BooleanProperty> CONNECTED = QuarryBlock.CONNECTED;
    private static final VoxelShape BASE = Block.box(4, 4, 4, 12, 12, 12);
    private static final Map<Direction, VoxelShape> ARMS = new EnumMap<>(Direction.class);
    private final VoxelShape[] shapes = new VoxelShape[64];

    static {
        ARMS.put(Direction.DOWN, Block.box(4, 0, 4, 12, 4, 12));
        ARMS.put(Direction.UP, Block.box(4, 12, 4, 12, 16, 12));
        ARMS.put(Direction.NORTH, Block.box(4, 4, 0, 12, 12, 4));
        ARMS.put(Direction.SOUTH, Block.box(4, 4, 12, 12, 12, 16));
        ARMS.put(Direction.WEST, Block.box(0, 4, 4, 4, 12, 12));
        ARMS.put(Direction.EAST, Block.box(12, 4, 4, 16, 12, 12));
    }

    public FrameBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any();
        for (BooleanProperty property : CONNECTED.values()) state = state.setValue(property, false);
        registerDefaultState(state);
        for (int mask = 0; mask < 64; mask++) {
            VoxelShape shape = BASE;
            for (Direction side : Direction.values()) {
                if ((mask & (1 << side.ordinal())) != 0) shape = Shapes.or(shape, ARMS.get(side));
            }
            shapes[mask] = shape;
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        CONNECTED.values().forEach(builder::add);
    }

    private static boolean connects(BlockState neighbour) {
        return neighbour.getBlock() instanceof FrameBlock || neighbour.getBlock() instanceof QuarryBlock;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Direction side : Direction.values()) {
            state = state.setValue(CONNECTED.get(side), connects(context.getLevel().getBlockState(context.getClickedPos().relative(side))));
        }
        return state;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        return state.setValue(CONNECTED.get(direction), connects(neighbour));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int mask = 0;
        for (Direction side : Direction.values()) if (state.getValue(CONNECTED.get(side))) mask |= 1 << side.ordinal();
        return shapes[mask];
    }
}

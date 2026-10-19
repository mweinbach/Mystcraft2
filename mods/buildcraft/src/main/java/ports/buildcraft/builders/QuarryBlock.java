package ports.buildcraft.builders;

import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import ports.buildcraft.lib.Ports;

/**
 * Upstream {@code BlockQuarry}. The {@code connected_*} properties are model-local faces, lit when the
 * matching world face has an item inventory or pipe next to it.
 */
public class QuarryBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final Map<Direction, BooleanProperty> CONNECTED = new EnumMap<>(Direction.class);

    static {
        for (Direction direction : Direction.values()) CONNECTED.put(direction, BooleanProperty.create("connected_" + direction.getSerializedName()));
    }

    public QuarryBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any().setValue(FACING, Direction.NORTH);
        for (BooleanProperty property : CONNECTED.values()) state = state.setValue(property, false);
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
        CONNECTED.values().forEach(builder::add);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** Upstream's local-to-world face mapping for a model rotated to {@code facing}. */
    static Direction worldFace(Direction local, Direction facing) {
        if (local.getAxis() == Direction.Axis.Y) return local;
        return Direction.from2DDataValue(local.get2DDataValue() + 2 + facing.get2DDataValue());
    }

    private static BlockState withConnections(Level level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FACING);
        for (Map.Entry<Direction, BooleanProperty> entry : CONNECTED.entrySet()) {
            Direction world = worldFace(entry.getKey(), facing);
            boolean connected = level.getBlockEntity(pos.relative(world)) != null && Ports.items(level, pos.relative(world), world.getOpposite()) != null;
            state = state.setValue(entry.getValue(), connected);
        }
        return state;
    }

    private static void refresh(Level level, BlockPos pos, BlockState state) {
        BlockState updated = withConnections(level, pos, state);
        if (updated != state) level.setBlock(pos, updated, 2);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        if (!level.isClientSide) refresh(level, pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (level.isClientSide) return;
        if (level.getBlockEntity(pos) instanceof QuarryBlockEntity quarry) quarry.onPlaced();
        refresh(level, pos, level.getBlockState(pos));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new QuarryBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, BCBuilders.QUARRY_TYPE.get(),
                level.isClientSide ? (l, p, s, quarry) -> quarry.clientTick() : (l, p, s, quarry) -> quarry.serverTick());
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof QuarryBlockEntity quarry) quarry.onRemoved();
        super.onRemove(state, level, pos, newState, moving);
    }
}

package ports.buildcraft.factory;

import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import ports.buildcraft.core.Wrenches;
import ports.buildcraft.platform.Platform;

/**
 * Upstream {@code BlockHeatExchange}. Three to five exchangers in a row, facing the same way, form one
 * exchanger: the start end takes the fluid to heat from below, the end takes the coolant from its side.
 * Upstream derived {@code part} and the connections from the tile; here they are stored block state.
 */
public class HeatExchangerBlock extends BaseEntityBlock {
    public enum Part implements StringRepresentable {
        START, MIDDLE, END;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    /** Connected toward the start of the row, upstream {@code facing.rotateY()}. */
    public static final BooleanProperty CONNECTED_LEFT = BooleanProperty.create("connected_left");
    public static final BooleanProperty CONNECTED_RIGHT = BooleanProperty.create("connected_right");

    public HeatExchangerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.WEST).setValue(PART, Part.MIDDLE)
                .setValue(CONNECTED_LEFT, false).setValue(CONNECTED_RIGHT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, CONNECTED_LEFT, CONNECTED_RIGHT);
    }

    /** The direction from this exchanger toward the start of its row. */
    public static Direction toStart(Direction facing) {
        return facing.getClockWise();
    }

    public static Direction toEnd(Direction facing) {
        return facing.getCounterClockWise();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        return withConnections(state, context.getLevel(), context.getClickedPos());
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        return direction.getAxis().isHorizontal() ? withConnections(state, level, pos) : state;
    }

    static BlockState withConnections(BlockState state, BlockGetter level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        return state.setValue(CONNECTED_LEFT, connects(level, pos.relative(toStart(facing)), facing))
                .setValue(CONNECTED_RIGHT, connects(level, pos.relative(toEnd(facing)), facing));
    }

    private static boolean connects(BlockGetter level, BlockPos pos, Direction facing) {
        BlockState neighbour = level.getBlockState(pos);
        return neighbour.getBlock() instanceof HeatExchangerBlock && neighbour.getValue(FACING) == facing;
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        if (from.getY() == pos.getY() && level.getBlockEntity(pos) instanceof HeatExchangerBlockEntity exchanger) exchanger.checkNeighbours();
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HeatExchangerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, BCFactory.HEAT_EXCHANGER_TYPE.get(),
                level.isClientSide ? (l, p, s, tile) -> tile.clientTick() : (l, p, s, tile) -> tile.serverTick());
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof HeatExchangerBlockEntity exchanger)) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        if (Wrenches.isWrench(held)) {
            if (!level.isClientSide) exchanger.rotate();
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (held.isEmpty() || state.getValue(PART) == Part.MIDDLE) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        return Platform.get().interactWithFluidContainer(player, hand, level, pos, null) ? InteractionResult.CONSUME : InteractionResult.PASS;
    }
}

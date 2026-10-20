package ports.buildcraft.factory;

import java.util.function.BiFunction;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import ports.buildcraft.core.FragileFluidShardItem;

/** Upstream {@code BlockPump} and {@code BlockMiningWell}; the mining well also faces its placer. */
public class MinerBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private final Supplier<? extends BlockEntityType<? extends MinerBlockEntity>> type;
    private final BiFunction<BlockPos, BlockState, ? extends MinerBlockEntity> factory;
    private final boolean facing;

    public MinerBlock(Properties properties, Supplier<? extends BlockEntityType<? extends MinerBlockEntity>> type,
            BiFunction<BlockPos, BlockState, ? extends MinerBlockEntity> factory, boolean facing) {
        super(properties);
        this.type = type;
        this.factory = factory;
        this.facing = facing;
        if (facing) registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public static MinerBlock pump(Properties properties) {
        return new MinerBlock(properties, BCFactory.PUMP_TYPE, PumpBlockEntity::new, false);
    }

    public static MinerBlock miningWell(Properties properties) {
        return new FacingMinerBlock(properties);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {}

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (!facing) return defaultBlockState();
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return factory.apply(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (blockEntityType != type.get()) return null;
        return level.isClientSide
                ? (l, p, s, tile) -> ((MinerBlockEntity) tile).clientTick()
                : (l, p, s, tile) -> ((MinerBlockEntity) tile).serverTick();
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof MinerBlockEntity miner) {
            miner.onRemoved();
            if (miner instanceof PumpBlockEntity pump) FragileFluidShardItem.dropTanks(level, pos, pump.tank());
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    private static final class FacingMinerBlock extends MinerBlock {
        FacingMinerBlock(Properties properties) {
            super(properties, BCFactory.MINING_WELL_TYPE, MiningWellBlockEntity::new, true);
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING);
        }
    }
}

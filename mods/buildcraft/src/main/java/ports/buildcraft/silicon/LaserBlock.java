package ports.buildcraft.silicon;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import ports.buildcraft.core.CustomRotationHandler;

/**
 * Upstream {@code BlockLaser}: faces any of the six directions, chosen as {@code BlockBCBase_Neptune} does, and
 * turns through north, east, south, west, up and down under a wrench.
 */
public class LaserBlock extends BaseEntityBlock implements CustomRotationHandler {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public LaserBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /**
     * Upstream: away from the placer's horizontal facing, or down when placed from more than two blocks above
     * and up when placed from below, if the placer stands within two blocks of it horizontally.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction orientation = context.getHorizontalDirection();
        Player player = context.getPlayer();
        BlockPos pos = context.getClickedPos();
        if (player != null && Math.abs(player.getX() - pos.getX()) < 2 && Math.abs(player.getZ() - pos.getZ()) < 2) {
            double eyeY = player.getEyeY();
            if (eyeY - pos.getY() > 2) orientation = Direction.DOWN;
            if (pos.getY() - eyeY > 0) orientation = Direction.UP;
        }
        return defaultBlockState().setValue(FACING, orientation.getOpposite());
    }

    /** Upstream {@code RotationUtil.rotateAll}. */
    static Direction rotateAll(Direction facing) {
        return switch (facing) {
            case NORTH -> Direction.EAST;
            case EAST -> Direction.SOUTH;
            case SOUTH -> Direction.WEST;
            case WEST -> Direction.UP;
            case UP -> Direction.DOWN;
            case DOWN -> Direction.NORTH;
        };
    }

    @Override
    public InteractionResult attemptRotation(Level level, BlockPos pos, BlockState state, Direction sideWrenched) {
        level.setBlockAndUpdate(pos, state.setValue(FACING, rotateAll(state.getValue(FACING))));
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LaserBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, BCSilicon.LASER_TYPE.get(), (l, p, s, laser) -> laser.clientTick())
                : createTickerHelper(type, BCSilicon.LASER_TYPE.get(), (l, p, s, laser) -> laser.serverTick());
    }
}

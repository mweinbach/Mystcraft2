package net.mystcraft.minecraft.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.mystcraft.integration.network.LinkingNetwork;
import net.mystcraft.minecraft.blockentity.BookstandBlockEntity;
import net.mystcraft.minecraft.item.LinkingBookItem;

public final class BookstandBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty ROTATION = IntegerProperty.create("rotation", 0, 7);
    private static final VoxelShape COLLISION = Block.box(2, 0, 2, 14, 12, 14);
    private static final VoxelShape OUTLINE = Shapes.or(
            Block.box(5.6, 0, 5.6, 10.4, 3.2, 10.4),
            Block.box(7.2, 1.6, 7.2, 8.8, 8, 8.8),
            Block.box(2.4, 6.4, 2.4, 13.6, 11.2, 13.6));
    public BookstandBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ROTATION, 0));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ROTATION);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(ROTATION, Mth.floor(context.getRotation() * 8 / 360 + 0.5) & 7);
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return OUTLINE;
    }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                                 CollisionContext context) { return COLLISION; }

    /** Four-direction prototype saves omit rotation; retain their facing when rotation defaults to zero. */
    public static float rotationDegrees(BlockState state) {
        int rotation = state.getValue(ROTATION);
        if (rotation == 0 && state.getValue(FACING) != Direction.NORTH) {
            return state.getValue(FACING).getOpposite().toYRot();
        }
        return 45 * rotation;
    }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BookstandBlockEntity(pos, state);
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                           InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof BookstandBlockEntity stand)) return InteractionResult.PASS;
        if (player.isSpectator()) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        if (stand.getBook().isEmpty() && !(held.getItem() instanceof LinkingBookItem)) return InteractionResult.PASS;
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) > 64) return InteractionResult.FAIL;
            if (stand.getBook().isEmpty()) {
                stand.setBook(held.split(1));
            } else if (player.isShiftKeyDown() && held.isEmpty()) {
                player.setItemInHand(hand, stand.takeBook());
            } else {
                LinkingNetwork.openStand(serverPlayer, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof BookstandBlockEntity stand) {
            Block.popResource(level, pos, stand.takeBook());
        }
        super.onRemove(state, level, pos, replacement, moving);
    }
}

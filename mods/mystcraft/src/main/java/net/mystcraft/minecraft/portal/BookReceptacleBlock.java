package net.mystcraft.minecraft.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.mystcraft.integration.registry.PortalRegistry;
import net.mystcraft.minecraft.item.LinkingBookItem;

public final class BookReceptacleBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = DirectionProperty.create("facing");
    public BookReceptacleBlock() {
        super(Properties.of(Material.GLASS).strength(1).sound(SoundType.GLASS).noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (context.getClickedFace() == Direction.DOWN) return null;
        BlockState state = defaultBlockState().setValue(FACING, context.getClickedFace());
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }
    @Override public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.relative(state.getValue(FACING).getOpposite())).is(PortalRegistry.CRYSTAL.get());
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case UP -> Block.box(0, 0, 0, 16, 6, 16);
            case DOWN -> Block.box(0, 10, 0, 16, 16, 16);
            case NORTH -> Block.box(0, 0, 10, 16, 16, 16);
            case SOUTH -> Block.box(0, 0, 0, 16, 16, 6);
            case WEST -> Block.box(10, 0, 0, 16, 16, 16);
            case EAST -> Block.box(0, 0, 0, 6, 16, 16);
        };
    }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new BookReceptacleBlockEntity(pos, state); }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isSpectator() || !(level.getBlockEntity(pos) instanceof BookReceptacleBlockEntity receptacle)) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        if (receptacle.getBook().isEmpty()) {
            if (!(held.getItem() instanceof LinkingBookItem)) return InteractionResult.PASS;
            if (!level.isClientSide) receptacle.setBook(held.split(1));
        } else if (held.isEmpty() && !level.isClientSide) {
            player.setItemInHand(hand, receptacle.takeBook());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void neighborChanged(BlockState state, Level level, BlockPos pos, Block changed, BlockPos from, boolean moving) {
        if (!level.isClientSide && !state.canSurvive(level, pos)) level.destroyBlock(pos, true);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof BookReceptacleBlockEntity receptacle) {
            ItemStack book = receptacle.takeBook();
            Block.popResource(level, pos, book);
        }
        super.onRemove(state, level, pos, next, moving);
    }
    @Override public boolean hasAnalogOutputSignal(BlockState state) { return true; }
    @Override public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof BookReceptacleBlockEntity receptacle && !receptacle.getBook().isEmpty() ? 15 : 0;
    }
}

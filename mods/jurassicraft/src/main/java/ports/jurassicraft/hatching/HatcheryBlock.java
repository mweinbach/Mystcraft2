package ports.jurassicraft.hatching;

import ports.jurassicraft.platform.Platform;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.BlockHitResult;

public final class HatcheryBlock extends BaseEntityBlock {
    private final HatcheryRegistry.Machine machine;
    public HatcheryBlock(HatcheryRegistry.Machine machine) {
        super(BlockBehaviour.Properties.of(Material.METAL).strength(2F).sound(SoundType.METAL).noOcclusion());
        this.machine = machine;
        registerDefaultState(stateDefinition.any().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) { builder.add(HorizontalDirectionalBlock.FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, context.getHorizontalDirection().getOpposite()); }
    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(HorizontalDirectionalBlock.FACING, rotation.rotate(state.getValue(HorizontalDirectionalBlock.FACING))); }
    @Override public BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(HorizontalDirectionalBlock.FACING))); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new HatcheryBlockEntity(pos, state, machine); }
    @Override @Nullable public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, HatcheryRegistry.blockEntity(machine), HatcheryBlockEntity::tick);
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!level.isClientSide && player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof HatcheryBlockEntity entity) Platform.openMenu(server, entity, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level,pos,state,placer,stack);
        if (stack.hasCustomHoverName() && level.getBlockEntity(pos) instanceof HatcheryBlockEntity entity) entity.setCustomName(stack.getHoverName());
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!level.isClientSide && !state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof HatcheryBlockEntity entity) {
            Containers.dropContents(level,pos,entity);
            level.updateNeighbourForOutputSignal(pos,this);
        }
        super.onRemove(state,level,pos,newState,moving);
    }
}

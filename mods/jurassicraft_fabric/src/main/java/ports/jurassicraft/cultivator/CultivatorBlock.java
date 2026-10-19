package ports.jurassicraft.cultivator;

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
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.BlockHitResult;

/** Two-block-tall machine; its lower half owns the only inventory. */
public final class CultivatorBlock extends BaseEntityBlock {
    public static final EnumProperty<DoubleBlockHalf> HALF=BlockStateProperties.DOUBLE_BLOCK_HALF;
    public CultivatorBlock() {
        super(BlockBehaviour.Properties.of(Material.METAL).strength(2,5).sound(SoundType.METAL).noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(HALF,DoubleBlockHalf.LOWER));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block,BlockState> builder) { builder.add(HALF); }
    @Override @Nullable public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos above=context.getClickedPos().above();
        return above.getY()<context.getLevel().getMaxBuildHeight() && context.getLevel().getBlockState(above).canBeReplaced(context) ? defaultBlockState() : null;
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,@Nullable LivingEntity placer,ItemStack stack) {
        super.setPlacedBy(level,pos,state,placer,stack);
        level.setBlock(pos.above(),state.setValue(HALF,DoubleBlockHalf.UPPER),3);
        if (stack.hasCustomHoverName() && level.getBlockEntity(pos) instanceof CultivatorBlockEntity entity) entity.setCustomName(stack.getHoverName());
    }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override @Nullable public BlockEntity newBlockEntity(BlockPos pos,BlockState state) {
        return state.getValue(HALF)==DoubleBlockHalf.LOWER ? new CultivatorBlockEntity(pos,state) : null;
    }
    @Override @Nullable public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type) {
        return level.isClientSide || state.getValue(HALF)==DoubleBlockHalf.UPPER ? null : createTickerHelper(type,CultivatorRegistry.ENTITY.get(),CultivatorBlockEntity::tick);
    }
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if (player.isShiftKeyDown()) return InteractionResult.PASS;
        BlockPos bottom=state.getValue(HALF)==DoubleBlockHalf.UPPER ? pos.below() : pos;
        if (!level.isClientSide && player instanceof ServerPlayer server && level.getBlockEntity(bottom) instanceof CultivatorBlockEntity entity) server.openMenu(entity);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void playerWillDestroy(Level level,BlockPos pos,BlockState state,Player player) {
        if (!level.isClientSide && player.isCreative() && state.getValue(HALF)==DoubleBlockHalf.UPPER) {
            BlockPos bottom=pos.below();
            if (level.getBlockState(bottom).is(this)) level.destroyBlock(bottom,false);
        }
        super.playerWillDestroy(level,pos,state,player);
    }
    @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving) {
        if (!level.isClientSide && !state.is(next.getBlock())) {
            if (state.getValue(HALF)==DoubleBlockHalf.LOWER) {
                if (level.getBlockEntity(pos) instanceof CultivatorBlockEntity entity) Containers.dropContents(level,pos,entity);
                BlockPos upper=pos.above();
                if (level.getBlockState(upper).is(this)) level.setBlock(upper,Blocks.AIR.defaultBlockState(),3);
            } else {
                BlockPos bottom=pos.below();
                if (level.getBlockState(bottom).is(this)) level.destroyBlock(bottom,true);
            }
        }
        super.onRemove(state,level,pos,next,moving);
    }
}

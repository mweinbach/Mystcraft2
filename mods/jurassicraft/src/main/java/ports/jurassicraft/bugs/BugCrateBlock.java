package ports.jurassicraft.bugs;

import ports.jurassicraft.platform.Platform;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.BlockHitResult;

public final class BugCrateBlock extends BaseEntityBlock {
    public BugCrateBlock() { super(BlockBehaviour.Properties.of(Material.WOOD).strength(2.5F).sound(SoundType.WOOD)); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new BugCrateBlockEntity(pos,state); }
    @Override @Nullable public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type,BugRegistry.ENTITY.get(),BugCrateBlockEntity::tick);
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,@Nullable LivingEntity placer,ItemStack stack) {
        super.setPlacedBy(level,pos,state,placer,stack);
        if (stack.hasCustomHoverName() && level.getBlockEntity(pos) instanceof BugCrateBlockEntity crate) crate.setCustomName(stack.getHoverName());
    }
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if (player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!level.isClientSide && player instanceof ServerPlayer server && level.getBlockEntity(pos) instanceof BugCrateBlockEntity crate) Platform.openMenu(server,crate,pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving) {
        if (!level.isClientSide && !state.is(next.getBlock()) && level.getBlockEntity(pos) instanceof BugCrateBlockEntity crate) {
            Containers.dropContents(level,pos,crate); level.updateNeighbourForOutputSignal(pos,this);
        }
        super.onRemove(state,level,pos,next,moving);
    }
}

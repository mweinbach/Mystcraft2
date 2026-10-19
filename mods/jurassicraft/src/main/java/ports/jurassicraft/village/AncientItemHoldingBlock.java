package ports.jurassicraft.village;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Sand hiding a displayed tool, as in the old digsites. */
final class AncientItemHoldingBlock extends BaseEntityBlock {
    AncientItemHoldingBlock() { super(BlockBehaviour.Properties.copy(Blocks.SAND).noOcclusion()); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override @Nullable public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new AncientItemHoldingBlockEntity(pos, state); }
    @Override public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) { return new ItemStack(Blocks.SAND); }
    @Override public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof AncientItemHoldingBlockEntity holder && !holder.item().isEmpty())
            Block.popResource(level, pos, holder.item().copy());
        super.playerWillDestroy(level, pos, state, player);
    }
}

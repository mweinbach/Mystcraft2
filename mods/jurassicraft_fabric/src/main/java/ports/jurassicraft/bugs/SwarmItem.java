package ports.jurassicraft.bugs;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class SwarmItem extends Item {
    private final Supplier<Block> block;
    public SwarmItem(Properties properties,Supplier<Block> block) { super(properties); this.block=block; }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        ItemStack stack=player.getItemInHand(hand);
        BlockHitResult hit=getPlayerPOVHitResult(level,player,ClipContext.Fluid.ANY);
        BlockPos water=hit.getBlockPos(), pos=water.above();
        if (hit.getType()!=HitResult.Type.BLOCK || !level.getFluidState(water).is(FluidTags.WATER)
                || !level.getBlockState(pos).getMaterial().isReplaceable() || !player.mayUseItemAt(pos,Direction.UP,stack)) return InteractionResultHolder.pass(stack);
        if (!level.isClientSide && level.setBlock(pos,block.get().defaultBlockState(),3) && !player.getAbilities().instabuild) stack.shrink(1);
        return InteractionResultHolder.sidedSuccess(stack,level.isClientSide);
    }
}

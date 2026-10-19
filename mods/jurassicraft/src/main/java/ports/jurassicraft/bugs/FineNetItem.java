package ports.jurassicraft.bugs;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class FineNetItem extends Item {
    public FineNetItem(Properties properties) { super(properties); }
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand) {
        ItemStack net=player.getItemInHand(hand);
        BlockHitResult hit=getPlayerPOVHitResult(level,player,ClipContext.Fluid.ANY);
        BlockPos pos=hit.getBlockPos();
        if (hit.getType()!=HitResult.Type.BLOCK || !level.getFluidState(pos).is(FluidTags.WATER)) return InteractionResultHolder.pass(net);
        if (!level.isClientSide) {
            if (level.random.nextInt(20)==0) {
                ItemStack catchStack=new ItemStack(level.random.nextBoolean() ? BugRegistry.KRILL.get() : BugRegistry.PLANKTON.get());
                if (!player.getInventory().add(catchStack)) player.drop(catchStack,false);
            }
            net.hurtAndBreak(1,player,p -> p.broadcastBreakEvent(hand));
        }
        return InteractionResultHolder.sidedSuccess(net,level.isClientSide);
    }
}

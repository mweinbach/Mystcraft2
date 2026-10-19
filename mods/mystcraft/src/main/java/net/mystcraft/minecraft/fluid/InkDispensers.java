package net.mystcraft.minecraft.fluid;
import net.minecraft.core.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.DispenserBlock;
import net.mystcraft.integration.registry.InkRegistry;
/** Classic UniversalBucket explicitly registered DispenseFluidContainer; keep actual container placement. */
public final class InkDispensers {
    public static void register(){
        DispenserBlock.registerBehavior(InkRegistry.BLACK_INK_BUCKET.get(),new DefaultDispenseItemBehavior(){
            @Override protected ItemStack execute(BlockSource source,ItemStack stack){
                var target=source.getPos().relative(source.getBlockState().getValue(DispenserBlock.FACING));
                var block=source.getLevel().getBlockEntity(target);
                if(block instanceof InkTank tank&&tank.fillInk(1000,false)==1000){tank.fillInk(1000,true);return new ItemStack(Items.BUCKET);}
                var bucket=(BucketItem)stack.getItem();
                if(bucket.emptyContents(null,source.getLevel(),target,null)){bucket.checkExtraContent(null,source.getLevel(),stack,target);return new ItemStack(Items.BUCKET);}
                return super.execute(source,stack);
            }
        });
    }
    private InkDispensers(){}
}

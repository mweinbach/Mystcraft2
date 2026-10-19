package net.mystcraft.minecraft.exploration;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
/** Atomic source currency accounting, including nine-emerald blocks and exact change. */
public final class ArchivistPurchases {
    private ArchivistPurchases(){}
    public static int emeralds(Inventory inventory){int total=0;for(int i=0;i<36;i++){ItemStack stack=inventory.getItem(i);if(stack.is(Items.EMERALD))total+=stack.getCount();else if(stack.is(Items.EMERALD_BLOCK))total+=9*stack.getCount();}return total;}
    public static boolean buy(Inventory inventory,ItemStack output,int price){
        if(output.isEmpty()||price<0||emeralds(inventory)<price)return false;
        SimpleContainer copy=new SimpleContainer(36);for(int i=0;i<36;i++)copy.setItem(i,inventory.getItem(i).copy());
        int owing=price;
        for(int i=0;i<36&&owing>0;i++)if(copy.getItem(i).is(Items.EMERALD)){int use=Math.min(owing,copy.getItem(i).getCount());copy.getItem(i).shrink(use);owing-=use;}
        int change=0;
        for(int i=0;i<36&&owing>0;i++)if(copy.getItem(i).is(Items.EMERALD_BLOCK)){int use=Math.min((owing+8)/9,copy.getItem(i).getCount());copy.getItem(i).shrink(use);owing-=use*9;}
        if(owing<0)change=-owing;
        if(change>0&&!copy.addItem(new ItemStack(Items.EMERALD,change)).isEmpty())return false;
        if(!copy.addItem(output.copy()).isEmpty())return false;
        for(int i=0;i<36;i++)inventory.setItem(i,copy.getItem(i));inventory.setChanged();return true;
    }
}

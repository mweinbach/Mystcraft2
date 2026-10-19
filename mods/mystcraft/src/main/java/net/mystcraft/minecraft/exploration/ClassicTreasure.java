package net.mystcraft.minecraft.exploration;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.mystcraft.integration.MystcraftItems;
import net.mystcraft.integration.registry.InkMixerRegistry;

/** Pinned mystcraft_treasure pool: four fixed entries followed by individually weighted pages. */
public final class ClassicTreasure {
    private ClassicTreasure() {}
    public static ItemStack roll(RegistryAccess registries, RandomSource random) {
        var pages=ExplorationPages.available(registries);
        int[] weights=ExplorationPages.treasureWeights(pages);
        int total=1150;
        for(var page:pages) total=Math.addExact(total,weights[page.rank()]);
        int choice=random.nextInt(total);
        if((choice-=50)<0)return new ItemStack(InkMixerRegistry.INK_VIAL.get());
        if((choice-=1000)<0)return new ItemStack(MystcraftItems.BOOSTER.get());
        if((choice-=50)<0)return new ItemStack(Items.LEATHER,1+random.nextInt(3));
        if((choice-=50)<0)return new ItemStack(Items.PAPER,1+random.nextInt(6));
        for(var page:pages)if((choice-=weights[page.rank()])<0){
            ItemStack stack=ExplorationPages.page(page);
            int maximum=page.rank()<4?16>>page.rank():1;
            stack.setCount(1+random.nextInt(Math.max(1,maximum-1)));
            return stack;
        }
        throw new IllegalStateException("Treasure weight selection escaped pool");
    }
    public static List<ItemStack> generate(RegistryAccess registries,RandomSource random,float luck){
        int count=4+random.nextInt(5)+(int)Math.floor((1+random.nextFloat())*luck);
        var result=new ArrayList<ItemStack>();
        for(int i=0;i<count;i++)result.add(roll(registries,random));
        return List.copyOf(result);
    }
}

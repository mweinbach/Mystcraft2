package net.mystcraft.minecraft.fluid;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.mystcraft.integration.registry.InkRegistry;
import net.mystcraft.integration.registry.InkMixerRegistry;
/** Native API adapters work on a single copy; callers commit input/output changes only after fitting the remainder. */
public final class InkContainerAccess {
    public record Drain(int amount,ItemStack remainder){public Drain{remainder=remainder.copy();}@Override public ItemStack remainder(){return remainder.copy();}}
    @FunctionalInterface public interface Adapter{Optional<Drain> drain(ItemStack one,int maximum,boolean wholeBucketOnly);}
    private static Adapter adapter=InkContainerAccess::ownContainers;
    public static void install(Adapter nativeAdapter){adapter=nativeAdapter;}
    public static Optional<Drain> drainOne(ItemStack stack,int maximum,boolean wholeBucketOnly){
        if(stack.isEmpty()||maximum<=0)return Optional.empty();ItemStack one=stack.copy();one.setCount(1);return adapter.drain(one,maximum,wholeBucketOnly);
    }
    public static boolean isInkContainer(ItemStack stack){return drainOne(stack,1000,true).isPresent();}
    public static boolean containsInk(ItemStack stack){return drainOne(stack,1000,false).isPresent();}
    public static boolean fits(ItemStack existing,ItemStack remainder){return remainder.isEmpty()||existing.isEmpty()&&remainder.getCount()<=remainder.getMaxStackSize()||ItemStack.isSameItemSameTags(existing,remainder)&&existing.getCount()+remainder.getCount()<=existing.getMaxStackSize();}
    public static ItemStack merge(ItemStack existing,ItemStack remainder){if(remainder.isEmpty())return existing.copy();if(existing.isEmpty())return remainder.copy();ItemStack merged=existing.copy();merged.grow(remainder.getCount());return merged;}
    private static Optional<Drain> ownContainers(ItemStack one,int maximum,boolean wholeBucketOnly){
        if(maximum<1000)return Optional.empty();
        if(one.is(InkMixerRegistry.INK_VIAL.get()))return Optional.of(new Drain(1000,new ItemStack(Items.GLASS_BOTTLE)));
        if(one.is(InkRegistry.BLACK_INK_BUCKET.get()))return Optional.of(new Drain(1000,new ItemStack(Items.BUCKET)));
        return Optional.empty();
    }
    private InkContainerAccess(){}
}

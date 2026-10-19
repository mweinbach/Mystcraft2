package net.mystcraft.forge.fluid;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.mystcraft.integration.registry.InkMixerRegistry;
import net.mystcraft.integration.registry.InkRegistry;
/** Original indivisible bucket-volume vial; unsafe direct operations on stacked containers reject. */
public final class ForgeInkVialHandler implements IFluidHandlerItem,ICapabilityProvider {
    private ItemStack container;
    private final LazyOptional<IFluidHandlerItem> capability=LazyOptional.of(()->this);
    public ForgeInkVialHandler(ItemStack container){this.container=container;}
    @Override public ItemStack getContainer(){return container;}
    private boolean full(){return container.is(InkMixerRegistry.INK_VIAL.get());}
    @Override public int getTanks(){return 1;}
    @Override public FluidStack getFluidInTank(int tank){return tank==0&&full()?new FluidStack(InkRegistry.BLACK_INK.get(),1000):FluidStack.EMPTY;}
    @Override public int getTankCapacity(int tank){return tank==0?1000:0;}
    @Override public boolean isFluidValid(int tank,FluidStack fluid){return tank==0&&fluid.getFluid()==InkRegistry.BLACK_INK.get();}
    @Override public int fill(FluidStack fluid,FluidAction action){
        if(fluid.hasTag()||container.getCount()!=1||!container.is(Items.GLASS_BOTTLE)||fluid.getFluid()!=InkRegistry.BLACK_INK.get()||fluid.getAmount()<1000)return 0;
        if(action.execute())container=new ItemStack(InkMixerRegistry.INK_VIAL.get());return 1000;
    }
    @Override public FluidStack drain(FluidStack fluid,FluidAction action){return fluid.getFluid()==InkRegistry.BLACK_INK.get()?drain(fluid.getAmount(),action):FluidStack.EMPTY;}
    @Override public FluidStack drain(int maximum,FluidAction action){
        if(container.getCount()!=1||!full()||maximum<1000)return FluidStack.EMPTY;
        var drained=new FluidStack(InkRegistry.BLACK_INK.get(),1000);if(action.execute())container=new ItemStack(Items.GLASS_BOTTLE);return drained;
    }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> key,Direction face){return key==ForgeCapabilities.FLUID_HANDLER_ITEM?capability.cast():LazyOptional.empty();}
    public void invalidate(){capability.invalidate();}
}

package ports.buildcraft.forge;

import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.core.FragileFluidShardItem;
import ports.buildcraft.lib.FluidAmount;

/** Upstream {@code FragileFluidHandler}: the shard's drain-only {@code FLUID_HANDLER_ITEM}, emptied into nothing. */
final class ForgeShardHandler implements IFluidHandlerItem, ICapabilityProvider {
    private static final ResourceLocation KEY = BuildCraft.id("fragile_fluid_shard");
    private final LazyOptional<IFluidHandlerItem> self = LazyOptional.of(() -> this);
    private ItemStack container;

    private ForgeShardHandler(ItemStack container) {
        this.container = container;
    }

    static void attach(AttachCapabilitiesEvent<ItemStack> event) {
        if (event.getObject().getItem() instanceof FragileFluidShardItem) event.addCapability(KEY, new ForgeShardHandler(event.getObject()));
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
        return capability == ForgeCapabilities.FLUID_HANDLER_ITEM ? self.cast() : LazyOptional.empty();
    }

    @Override public ItemStack getContainer() { return container; }
    @Override public int getTanks() { return 1; }
    @Override public int getTankCapacity(int tank) { return FragileFluidShardItem.MAX_FLUID_HELD; }
    @Override public boolean isFluidValid(int tank, FluidStack stack) { return false; }
    @Override public int fill(FluidStack resource, FluidAction action) { return 0; }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return toStack(FragileFluidShardItem.getFluid(container));
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty() || resource.hasTag()) return FluidStack.EMPTY;
        return drain(resource, resource.getAmount(), action);
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return drain(null, maxDrain, action);
    }

    private FluidStack drain(@Nullable FluidStack filter, int max, FluidAction action) {
        FluidAmount drained = FragileFluidShardItem.drain(container, filter == null ? null : filter.getFluid(), max, action.simulate());
        if (action.execute() && FragileFluidShardItem.getFluid(container).isEmpty()) container = ItemStack.EMPTY;
        return toStack(drained);
    }

    private static FluidStack toStack(FluidAmount amount) {
        return amount.isEmpty() ? FluidStack.EMPTY : new FluidStack(amount.fluid(), (int) amount.amount());
    }
}

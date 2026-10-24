/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.fluids.capability.wrappers;

import net.minecraft.core.Direction;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ports.projecte.lib.common.capabilities.Capability;
import ports.projecte.lib.common.capabilities.ForgeCapabilities;
import ports.projecte.lib.common.capabilities.ICapabilityProvider;
import ports.projecte.lib.common.util.LazyOptional;
import ports.projecte.lib.fluids.FluidStack;
import ports.projecte.lib.fluids.FluidType;
import ports.projecte.lib.fluids.capability.IFluidHandlerItem;

/**
 * A full or empty bucket seen as a one-bucket tank that can only be emptied or filled completely.
 */
public class FluidBucketWrapper implements IFluidHandlerItem, ICapabilityProvider {

    private final LazyOptional<IFluidHandlerItem> holder = LazyOptional.of(() -> this);
    @NotNull
    protected ItemStack container;

    public FluidBucketWrapper(@NotNull ItemStack container) {
        this.container = container;
    }

    @NotNull
    @Override
    public ItemStack getContainer() {
        return container;
    }

    public boolean canFillFluidType(FluidStack fluid) {
        return !fluid.getFluid().getBucket().getDefaultInstance().isEmpty() && fluid.getFluid().isSource(fluid.getFluid().defaultFluidState());
    }

    @NotNull
    public FluidStack getFluid() {
        if (container.getItem() instanceof BucketItem bucket) {
            Fluid content = bucket.content;
            return content == Fluids.EMPTY ? FluidStack.EMPTY : new FluidStack(content, FluidType.BUCKET_VOLUME);
        }
        return FluidStack.EMPTY;
    }

    protected void setFluid(@NotNull FluidStack fluidStack) {
        container = fluidStack.isEmpty() ? new ItemStack(Items.BUCKET) : new ItemStack(fluidStack.getFluid().getBucket());
    }

    @Override
    public int getTanks() {
        return 1;
    }

    @NotNull
    @Override
    public FluidStack getFluidInTank(int tank) {
        return getFluid();
    }

    @Override
    public int getTankCapacity(int tank) {
        return FluidType.BUCKET_VOLUME;
    }

    @Override
    public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
        return true;
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (container.getCount() != 1 || resource.getAmount() < FluidType.BUCKET_VOLUME || !getFluid().isEmpty() || !canFillFluidType(resource)) {
            return 0;
        }
        if (action.execute()) {
            setFluid(resource);
        }
        return FluidType.BUCKET_VOLUME;
    }

    @NotNull
    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (container.getCount() != 1 || resource.getAmount() < FluidType.BUCKET_VOLUME) {
            return FluidStack.EMPTY;
        }
        FluidStack fluidStack = getFluid();
        if (!fluidStack.isEmpty() && fluidStack.isFluidEqual(resource)) {
            if (action.execute()) {
                setFluid(FluidStack.EMPTY);
            }
            return fluidStack;
        }
        return FluidStack.EMPTY;
    }

    @NotNull
    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        if (container.getCount() != 1 || maxDrain < FluidType.BUCKET_VOLUME) {
            return FluidStack.EMPTY;
        }
        FluidStack fluidStack = getFluid();
        if (!fluidStack.isEmpty()) {
            if (action.execute()) {
                setFluid(FluidStack.EMPTY);
            }
            return fluidStack;
        }
        return FluidStack.EMPTY;
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction facing) {
        return ForgeCapabilities.FLUID_HANDLER_ITEM.orEmpty(capability, holder);
    }
}

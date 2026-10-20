/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.item.inventory.container.data;

import net.minecraft.network.FriendlyByteBuf;
import ports.extremereactors.corelib.fluid.FluidStack;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class FluidStackData
        extends AbstractData<FluidStack> {

    public FluidStackData(final Supplier<FluidStack> source, final Consumer<FluidStack> consumer) {
        super(source, consumer);
    }

    @Override
    protected void write(final FriendlyByteBuf buffer, final FluidStack value) {
        value.writeToPacket(buffer);
    }

    @Override
    protected FluidStack read(final FriendlyByteBuf buffer) {
        return FluidStack.readFromPacket(buffer);
    }

    @Override
    protected boolean isSame(final FluidStack previous, final FluidStack current) {
        return previous.isFluidStackIdentical(current);
    }

    @Override
    protected FluidStack copy(final FluidStack value) {
        return value.copy();
    }
}

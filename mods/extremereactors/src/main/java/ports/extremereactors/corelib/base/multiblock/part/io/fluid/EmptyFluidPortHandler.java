/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.base.multiblock.part.io.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import ports.extremereactors.corelib.capability.Capability;
import ports.extremereactors.corelib.capability.LazyOptional;
import ports.extremereactors.corelib.data.IoDirection;
import ports.extremereactors.corelib.fluid.FluidStack;
import ports.extremereactors.corelib.fluid.handler.IFluidHandler;

import javax.annotation.Nullable;
import java.util.function.Function;

/**
 * A fluid port handler that never connects to anything, for ports whose fluid system is not available.
 */
public class EmptyFluidPortHandler implements IFluidPortHandler {

    @Override
    public int outputFluid(final FluidStack stack) {
        return 0;
    }

    @Override
    public int inputFluid(final IFluidHandler destination, final int maxAmount) {
        return 0;
    }

    @Override
    public void update(final Function<IoDirection, IFluidHandler> handlerProvider) {
    }

    @Override
    public boolean isActive() {
        return false;
    }

    @Override
    public boolean isConnected() {
        return false;
    }

    @Override
    public void checkConnections(@Nullable final Level world, final BlockPos position) {
    }

    @Override
    public void invalidate() {
    }

    @Nullable
    @Override
    public <T> LazyOptional<T> getCapability(final Capability<T> capability, @Nullable final Direction direction) {
        return null;
    }
}

/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.base.multiblock.part.io.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import ports.extremereactors.corelib.base.multiblock.part.AbstractMultiblockEntity;
import ports.extremereactors.corelib.capability.Capability;
import ports.extremereactors.corelib.capability.CapabilityHelper;
import ports.extremereactors.corelib.capability.LazyOptional;
import ports.extremereactors.corelib.data.IoDirection;
import ports.extremereactors.corelib.data.IoMode;
import ports.extremereactors.corelib.fluid.FluidStack;
import ports.extremereactors.corelib.fluid.FluidUtil;
import ports.extremereactors.corelib.fluid.handler.EmptyFluidHandler;
import ports.extremereactors.corelib.fluid.handler.FluidHandlerForwarder;
import ports.extremereactors.corelib.fluid.handler.IFluidHandler;
import ports.extremereactors.multiblock.cuboid.AbstractCuboidMultiblockController;

import javax.annotation.Nullable;
import java.util.function.Function;

/**
 * A fluid port working with the loader's standard fluid handlers (the Forge fluid capability or the Fabric
 * Transfer API). Active ports push to, or pull from, the neighbour in front of them. Passive ports expose the
 * machine handler matching their direction on their outward side.
 */
public class FluidPortHandlerForge<Controller extends AbstractCuboidMultiblockController<Controller>,
                                   T extends AbstractMultiblockEntity<Controller> & IFluidPort>
        extends AbstractFluidPortHandler<Controller, T> {

    public FluidPortHandlerForge(final T part, final IoMode mode) {

        super(part, mode);
        this._neighbor = LazyOptional.empty();
        this._forwarder = new FluidHandlerForwarder(EmptyFluidHandler.INSTANCE);
        this._exposed = null;
    }

    //region IFluidPortHandler

    @Override
    public int outputFluid(final FluidStack stack) {

        if (this.isPassive() || stack.isEmpty()) {
            return 0;
        }

        final IFluidHandler destination = this._neighbor.orElse(null);

        return null == destination ? 0 : destination.fill(stack, IFluidHandler.FluidAction.EXECUTE);
    }

    @Override
    public int inputFluid(final IFluidHandler destination, final int maxAmount) {

        if (this.isPassive() || maxAmount <= 0) {
            return 0;
        }

        final IFluidHandler source = this._neighbor.orElse(null);

        return null == source ? 0 : FluidUtil.tryFluidTransfer(destination, source, maxAmount, true).getAmount();
    }

    @Override
    public void update(final Function<IoDirection, IFluidHandler> handlerProvider) {

        final T part = this.getPart();

        this._forwarder.setHandler(handlerProvider.apply(part.getIoDirection()));
        this.checkConnections(part.getLevel(), part.getWorldPosition());
    }

    //endregion
    //region IIOPortHandler

    @Override
    public boolean isConnected() {
        return this._neighbor.isPresent();
    }

    @Override
    public void checkConnections(@Nullable final Level world, final BlockPos position) {
        this._neighbor = this.lookupNeighbor(world, position, CapabilityHelper.FLUID_HANDLER);
    }

    @Override
    public void invalidate() {

        this._neighbor = LazyOptional.empty();

        if (null != this._exposed) {

            this._exposed.invalidate();
            this._exposed = null;
        }
    }

    @Nullable
    @Override
    public <C> LazyOptional<C> getCapability(final Capability<C> capability, @Nullable final Direction direction) {

        if (CapabilityHelper.FLUID_HANDLER != capability) {
            return null;
        }

        if (this.isActive() || !this.isExposedOn(direction)) {
            return LazyOptional.empty();
        }

        if (null == this._exposed) {
            this._exposed = LazyOptional.of(() -> this._forwarder);
        }

        return this._exposed.cast();
    }

    //endregion
    //region internals

    private LazyOptional<IFluidHandler> _neighbor;
    private final FluidHandlerForwarder _forwarder;
    @Nullable
    private LazyOptional<IFluidHandler> _exposed;

    //endregion
}

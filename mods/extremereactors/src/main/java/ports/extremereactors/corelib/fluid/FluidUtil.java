/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.fluid;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import ports.extremereactors.corelib.capability.CapabilityHelper;
import ports.extremereactors.corelib.capability.LazyOptional;
import ports.extremereactors.corelib.fluid.handler.IFluidHandler;
import ports.extremereactors.platform.Platform;

public final class FluidUtil {

    public static LazyOptional<IFluidHandler> getFluidHandler(final Level level, final BlockPos position,
                                                              @Nullable final Direction side) {
        return CapabilityHelper.getCapability(level, position, CapabilityHelper.FLUID_HANDLER, side);
    }

    /**
     * Fills or empties the fluid container the player is holding (a bucket, a tank item...) into or from
     * {@code handler}, using the loader's item fluid API.
     *
     * @return true if any fluid moved
     */
    public static boolean interactWithFluidHandler(final Player player, final InteractionHand hand, final IFluidHandler handler) {
        return Platform.backend().interactWithFluidHandler(player, hand, handler);
    }

    /**
     * Moves up to {@code maxAmount} of whatever {@code source} drains first into {@code destination}.
     *
     * @return the fluid moved, or that would be moved if {@code doTransfer} is false
     */
    public static FluidStack tryFluidTransfer(final IFluidHandler destination, final IFluidHandler source,
                                              final int maxAmount, final boolean doTransfer) {

        final FluidStack available = source.drain(maxAmount, IFluidHandler.FluidAction.SIMULATE);

        if (available.isEmpty()) {
            return FluidStack.EMPTY;
        }

        final int accepted = destination.fill(available, IFluidHandler.FluidAction.SIMULATE);

        if (accepted <= 0) {
            return FluidStack.EMPTY;
        }

        final FluidStack toMove = source.drain(new FluidStack(available, accepted), IFluidHandler.FluidAction.SIMULATE);

        if (toMove.isEmpty()) {
            return FluidStack.EMPTY;
        }

        if (!doTransfer) {
            return toMove;
        }

        final FluidStack drained = source.drain(toMove, IFluidHandler.FluidAction.EXECUTE);
        final int filled = destination.fill(drained, IFluidHandler.FluidAction.EXECUTE);

        return new FluidStack(drained, filled);
    }

    private FluidUtil() {
    }
}

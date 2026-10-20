/*
 * Extreme Reactors 1.19.2 port - game tests
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

/**
 * {@link LoaderApis} through Forge capabilities.
 */
final class ForgeLoaderApis implements LoaderApis {

    static final LoaderApis INSTANCE = new ForgeLoaderApis();

    @Override
    public long insertItems(final ServerLevel level, final BlockPos position, final Direction side, final ItemStack stack) {

        final IItemHandler handler = capability(level, position, ForgeCapabilities.ITEM_HANDLER, side);

        return stack.getCount() - ItemHandlerHelper.insertItem(handler, stack.copy(), false).getCount();
    }

    @Override
    public long insertFluid(final ServerLevel level, final BlockPos position, final Direction side, final Fluid fluid,
                            final int millibuckets) {

        final IFluidHandler handler = capability(level, position, ForgeCapabilities.FLUID_HANDLER, side);

        return handler.fill(new FluidStack(fluid, millibuckets), IFluidHandler.FluidAction.EXECUTE);
    }

    @Override
    public long extractEnergy(final ServerLevel level, final BlockPos position, final Direction side, final long maxAmount) {

        final IEnergyStorage handler = capability(level, position, ForgeCapabilities.ENERGY, side);

        return handler.extractEnergy((int) Math.min(Integer.MAX_VALUE, maxAmount), false);
    }

    private static <T> T capability(final ServerLevel level, final BlockPos position, final Capability<T> capability,
                                    final Direction side) {

        final BlockEntity blockEntity = level.getBlockEntity(position);

        if (null == blockEntity) {
            throw new GameTestAssertException("no block entity at " + position);
        }

        return blockEntity.getCapability(capability, side).resolve()
                .orElseThrow(() -> new GameTestAssertException("no " + capability.getName() + " at " + position));
    }

    private ForgeLoaderApis() {
    }
}

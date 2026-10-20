/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.capability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import ports.extremereactors.corelib.energy.IEnergyStorage;
import ports.extremereactors.corelib.fluid.handler.IFluidHandler;
import ports.extremereactors.corelib.item.inventory.handler.IItemHandler;
import ports.extremereactors.platform.Platform;

import javax.annotation.Nullable;

/**
 * Looks up handlers on any block entity. Ours are asked directly; anything else goes through the loader's transfer
 * API (Forge capabilities, or the Fabric Transfer API and Team Reborn Energy) via an adapter.
 */
public final class CapabilityHelper {

    public static final Capability<IEnergyStorage> ENERGY = CapabilityManager.get(IEnergyStorage.class);
    public static final Capability<IFluidHandler> FLUID_HANDLER = CapabilityManager.get(IFluidHandler.class);
    public static final Capability<IItemHandler> ITEM_HANDLER = CapabilityManager.get(IItemHandler.class);

    public static <T> LazyOptional<T> getCapability(final BlockEntity blockEntity, final Capability<T> capability,
                                                    @Nullable final Direction side) {

        if (blockEntity instanceof ICapabilityProvider) {
            return ((ICapabilityProvider) blockEntity).getCapability(capability, side);
        }

        final Level level = blockEntity.getLevel();

        if (null == level || blockEntity.isRemoved()) {
            return LazyOptional.empty();
        }

        return Platform.backend().foreignCapability(level, blockEntity.getBlockPos(), blockEntity.getBlockState(),
                blockEntity, capability, side);
    }

    public static <T> LazyOptional<T> getCapability(final Level level, final BlockPos position,
                                                    final Capability<T> capability, @Nullable final Direction side) {

        if (!level.isLoaded(position)) {
            return LazyOptional.empty();
        }

        final BlockEntity blockEntity = level.getBlockEntity(position);

        if (blockEntity instanceof ICapabilityProvider) {
            return ((ICapabilityProvider) blockEntity).getCapability(capability, side);
        }

        return Platform.backend().foreignCapability(level, position, level.getBlockState(position), blockEntity,
                capability, side);
    }

    private CapabilityHelper() {
    }
}

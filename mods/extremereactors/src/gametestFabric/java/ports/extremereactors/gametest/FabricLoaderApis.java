/*
 * Extreme Reactors 1.19.2 port - game tests
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.gametest;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import team.reborn.energy.api.EnergyStorage;

import java.util.Optional;

/**
 * {@link LoaderApis} through the Fabric Transfer API and Team Reborn Energy.
 */
final class FabricLoaderApis implements LoaderApis {

    static final LoaderApis INSTANCE = new FabricLoaderApis();

    @Override
    public long insertItems(final ServerLevel level, final BlockPos position, final Direction side, final ItemStack stack) {

        final Storage<ItemVariant> storage = found(ItemStorage.SIDED.find(level, position, side), "item storage", position);

        try (Transaction transaction = Transaction.openOuter()) {

            final long inserted = storage.insert(ItemVariant.of(stack), stack.getCount(), transaction);

            transaction.commit();
            return inserted;
        }
    }

    @Override
    public long insertFluid(final ServerLevel level, final BlockPos position, final Direction side, final Fluid fluid,
                            final int millibuckets) {

        final Storage<FluidVariant> storage = found(FluidStorage.SIDED.find(level, position, side), "fluid storage", position);
        final long dropletsPerMb = FluidConstants.BUCKET / 1000;

        try (Transaction transaction = Transaction.openOuter()) {

            final long inserted = storage.insert(FluidVariant.of(fluid), millibuckets * dropletsPerMb, transaction);

            transaction.commit();
            return inserted / dropletsPerMb;
        }
    }

    @Override
    public long extractEnergy(final ServerLevel level, final BlockPos position, final Direction side, final long maxAmount) {

        final EnergyStorage storage = found(EnergyStorage.SIDED.find(level, position, side), "energy storage", position);

        try (Transaction transaction = Transaction.openOuter()) {

            final long extracted = storage.extract(maxAmount, transaction);

            transaction.commit();
            return extracted;
        }
    }

    @Override
    public String energyUnit() {
        return "E";
    }

    /** Mekanism has no 1.19.2 Fabric release. */
    @Override
    public Optional<GasTank> mekanismGas(final ServerLevel level, final BlockPos position, final Direction side) {
        return Optional.empty();
    }

    @Override
    public long extractMekanismGas(final ServerLevel level, final BlockPos position, final Direction side,
                                   final long maxAmount) {
        return 0;
    }

    private static <T> T found(final T lookupResult, final String what, final BlockPos position) {

        if (null == lookupResult) {
            throw new GameTestAssertException("no " + what + " at " + position);
        }

        return lookupResult;
    }

    private FabricLoaderApis() {
    }
}

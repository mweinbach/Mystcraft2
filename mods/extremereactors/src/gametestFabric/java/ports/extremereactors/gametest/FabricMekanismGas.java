/*
 * Extreme Reactors 1.19.2 port - game tests
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.gametest;

import mekanism.api.Action;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.gas.IGasHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import ports.mekanism.fabric.api.MekanismChemicalLookups;

import java.util.Optional;

/**
 * Mekanism's view of a block, kept apart from {@link FabricLoaderApis} so Mekanism's classes only load with Mekanism.
 */
final class FabricMekanismGas {

    static Optional<LoaderApis.GasTank> find(final ServerLevel level, final BlockPos position, final Direction side) {
        return handler(level, position, side).map(gas -> {

            final GasStack stored = gas.getChemicalInTank(0);

            return new LoaderApis.GasTank(stored.isEmpty() ? null : stored.getTypeRegistryName(), stored.getAmount(),
                    gas.getTankCapacity(0));
        });
    }

    static long extract(final ServerLevel level, final BlockPos position, final Direction side, final long maxAmount) {
        return handler(level, position, side)
                .map(gas -> gas.extractChemical(0, maxAmount, Action.EXECUTE).getAmount())
                .orElse(0L);
    }

    private static Optional<IGasHandler> handler(final ServerLevel level, final BlockPos position, final Direction side) {

        final IGasHandler gas = MekanismChemicalLookups.GAS.find(level, position, side);

        return null == gas ? Optional.empty() : Optional.of(gas);
    }

    private FabricMekanismGas() {
    }
}

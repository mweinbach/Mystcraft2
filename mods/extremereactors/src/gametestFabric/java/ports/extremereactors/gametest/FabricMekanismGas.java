/*
 * Extreme Reactors 1.19.2 port - game tests
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.gametest;

import mekanism.api.Action;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.gas.IGasHandler;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.util.CapabilityUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import ports.mekanism.lib.common.capabilities.ICapabilityProvider;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Mekanism's view of a block, kept apart from {@link FabricLoaderApis} so Mekanism's classes only load with Mekanism.
 * <p>
 * The handler is asked for the way the Mekanism port's own blocks ask their neighbours: through Mekanism's gas
 * capability on the block entity. The port injects that capability into every block entity; for one it did not
 * attach a capability to, such as ours, it falls back to the {@code mekanism:gas_handler} block lookup. So this
 * covers the bridge Mekanism's tubes and machines use, not only the lookup registration.
 */
final class FabricMekanismGas {

    static Optional<LoaderApis.GasTank> find(final ServerLevel level, final BlockPos position, @Nullable final Direction side) {
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

    private static Optional<IGasHandler> handler(final ServerLevel level, final BlockPos position,
                                                 @Nullable final Direction side) {

        final BlockEntity blockEntity = level.getBlockEntity(position);

        if (null == blockEntity) {
            return Optional.empty();
        }

        // The Mekanism port injects its capability provider into BlockEntity at runtime; this build compiles against
        // Minecraft without that interface injection.
        final ICapabilityProvider provider = (ICapabilityProvider) (Object) blockEntity;

        return CapabilityUtils.getCapability(provider, Capabilities.GAS_HANDLER, side).resolve();
    }

    private FabricMekanismGas() {
    }
}

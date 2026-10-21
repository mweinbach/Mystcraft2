/*
 * Extreme Reactors 1.19.2 port - Fabric adapter
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.fabric.compat;

import it.zerono.mods.extremereactors.gamecontent.Content;
import mekanism.api.chemical.gas.IGasHandler;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import ports.extremereactors.corelib.capability.ICapabilityProvider;
import ports.extremereactors.corelib.compat.mekanism.MekanismGas;
import ports.extremereactors.corelib.compat.mekanism.MekanismRotary;
import ports.extremereactors.corelib.fluid.handler.IFluidHandler;
import ports.extremereactors.fabric.FabricTransfer;
import ports.mekanism.fabric.api.MekanismChemicalLookups;

/**
 * Registers the reactor Mekanism port on Mekanism's gas lookup, and reads a neighbour's gas handler back through
 * that same lookup. The fluid and gas conversion is {@link MekanismRotary}, shared with the Forge adapter. Only
 * loaded when Mekanism is.
 */
public final class FabricMekanism {

    public static void initialize() {

        final BlockEntityType<?> port = Content.TileEntityTypes.REACTOR_FLUIDPORT_MEKANISM_PASSIVE.get();

        MekanismChemicalLookups.GAS.registerForBlockEntities(FabricMekanism::gasOf, port);
        FabricTransfer.registerForeign(MekanismGas.HANDLER, (level, position, state, blockEntity, side) -> {

            final IGasHandler gas = MekanismChemicalLookups.GAS.find(level, position, state, blockEntity, side);

            return null == gas ? null : MekanismRotary.asFluid(gas);
        });
    }

    private static IGasHandler gasOf(final BlockEntity blockEntity, final Direction side) {

        if (!(blockEntity instanceof ICapabilityProvider provider)) {
            return null;
        }

        final IFluidHandler tank = provider.getCapability(MekanismGas.HANDLER, side).orElse(null);

        return null == tank ? null : MekanismRotary.asGas(tank);
    }

    private FabricMekanism() {
    }
}

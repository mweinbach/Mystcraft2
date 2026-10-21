/*
 * Extreme Reactors 1.19.2 port - Forge adapter
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.forge.compat;

import mekanism.api.chemical.gas.IGasHandler;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import ports.extremereactors.corelib.compat.mekanism.MekanismGas;
import ports.extremereactors.corelib.compat.mekanism.MekanismRotary;
import ports.extremereactors.forge.ForgeTransfer;

/**
 * Bridges {@link MekanismGas#HANDLER} to Mekanism's gas handler capability. The fluid and gas conversion is
 * {@link MekanismRotary}, shared with the Fabric adapter. Only loaded when Mekanism is.
 */
public final class ForgeMekanism {

    public static final Capability<IGasHandler> GAS_HANDLER = CapabilityManager.get(new CapabilityToken<>(){});

    public static void initialize() {
        ForgeTransfer.register(new ForgeTransfer.ForeignCapability<>(GAS_HANDLER, MekanismGas.HANDLER,
                MekanismRotary::asGas, MekanismRotary::asFluid));
    }

    private ForgeMekanism() {
    }
}

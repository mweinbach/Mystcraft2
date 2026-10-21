/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.compat.mekanism;

import ports.extremereactors.corelib.capability.Capability;
import ports.extremereactors.corelib.capability.CapabilityManager;
import ports.extremereactors.corelib.fluid.handler.IFluidHandler;

/**
 * Mekanism's gas handler, seen from the shared code as the fluid tank whose content it offers as gas. A loader with
 * Mekanism bridges it to Mekanism's own handler and does the fluid and gas conversion; elsewhere nothing asks for it.
 */
public final class MekanismGas {

    public static final String MOD_ID = "mekanism";

    public static final Capability<IFluidHandler> HANDLER = CapabilityManager.get("mekanism:gas_handler", IFluidHandler.class);

    private MekanismGas() {
    }
}

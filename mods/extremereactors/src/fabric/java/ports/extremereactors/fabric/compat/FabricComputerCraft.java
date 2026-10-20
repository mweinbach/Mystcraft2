/*
 * Extreme Reactors 1.19.2 port - Fabric adapter
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.fabric.compat;

import dan200.computercraft.api.ComputerCraftAPI;
import ports.extremereactors.corelib.compat.computer.ComputerCraftPeripheral;

/**
 * Offers the reactor and turbine computer ports to CC: Restitched. Only loaded when CC: Restitched is installed.
 */
public final class FabricComputerCraft {

    public static void initialize() {
        ComputerCraftAPI.registerPeripheralProvider(ComputerCraftPeripheral::find);
    }

    private FabricComputerCraft() {
    }
}

/*
 * Extreme Reactors 1.19.2 port - Forge adapter
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.forge.compat;

import dan200.computercraft.api.ForgeComputerCraftAPI;
import net.minecraftforge.common.util.LazyOptional;
import ports.extremereactors.corelib.compat.computer.ComputerCraftPeripheral;

/**
 * Offers the reactor and turbine computer ports to CC: Tweaked. Only loaded when CC: Tweaked is installed.
 */
public final class ForgeComputerCraft {

    public static void initialize() {
        ForgeComputerCraftAPI.registerPeripheralProvider((level, position, side) -> {

            final ComputerCraftPeripheral peripheral = ComputerCraftPeripheral.find(level, position, side);

            return null == peripheral ? LazyOptional.empty() : LazyOptional.of(() -> peripheral);
        });
    }

    private ForgeComputerCraft() {
    }
}

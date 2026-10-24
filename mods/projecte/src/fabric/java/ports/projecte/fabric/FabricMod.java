/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric loader adapter.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric;

import moze_intel.projecte.PECore;
import moze_intel.projecte.gameObjs.EnumFuelType;
import moze_intel.projecte.gameObjs.registries.PEBlocks;
import moze_intel.projecte.gameObjs.registries.PEItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.registry.FuelRegistry;
import ports.projecte.platform.Platform;

/**
 * The Fabric main entrypoint: constructs ProjectE and runs it through Forge's common loading stages.
 */
public final class FabricMod implements ModInitializer {

    @Override
    public void onInitialize() {
        Platform.install(FabricPlatform.INSTANCE);
        FabricGameEvents.register();
        FabricLifecycle.addMod(PECore.MODID, PECore::new);
        FabricLifecycle.tryStart();
        registerFuels();
    }

    /**
     * Forge asks each item's {@code getBurnTime}; Fabric furnaces, and every other Fabric mod's, ask Fabric's fuel
     * registry. Registering the alchemical fuels there, with the burn times their {@code getBurnTime} returns, needs no
     * furnace patch that could disagree with another mod's. The registry resolves the items lazily, after registration.
     * Burn times above 32,767 ticks are Forge's values too; Fabric only logs a warning for them.
     */
    private static void registerFuels() {
        FuelRegistry fuels = FuelRegistry.INSTANCE;
        fuels.add(PEItems.ALCHEMICAL_COAL, EnumFuelType.ALCHEMICAL_COAL.getBurnTime());
        fuels.add(PEItems.MOBIUS_FUEL, EnumFuelType.MOBIUS_FUEL.getBurnTime());
        fuels.add(PEItems.AETERNALIS_FUEL, EnumFuelType.AETERNALIS_FUEL.getBurnTime());
        fuels.add(PEBlocks.ALCHEMICAL_COAL, EnumFuelType.ALCHEMICAL_COAL.getBurnTime() * 9);
        fuels.add(PEBlocks.MOBIUS_FUEL, EnumFuelType.MOBIUS_FUEL.getBurnTime() * 9);
        fuels.add(PEBlocks.AETERNALIS_FUEL, EnumFuelType.AETERNALIS_FUEL.getBurnTime() * 9);
    }
}

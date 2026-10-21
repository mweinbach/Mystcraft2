/*
 * Mekanism 1.19.2 port - Fabric loader adapter.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.generators.fabric;

import mekanism.generators.common.MekanismGenerators;
import net.fabricmc.api.ModInitializer;
import ports.mekanism.fabric.FabricLifecycle;

/**
 * Registers Generators with the port's Forge-shaped loading stages. Construction waits until every Mekanism module
 * jar that is installed has registered, which the core module starts.
 */
public final class GeneratorsFabricMod implements ModInitializer {

    @Override
    public void onInitialize() {
        FabricLifecycle.addMod(MekanismGenerators.MODID, MekanismGenerators::new);
        FabricLifecycle.tryStart();
    }
}

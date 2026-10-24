/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric loader adapter.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.fabric;

import moze_intel.projecte.PECore;
import net.fabricmc.api.ModInitializer;
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
    }
}

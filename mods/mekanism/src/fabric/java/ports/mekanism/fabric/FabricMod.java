/*
 * Mekanism 1.19.2 port - Fabric loader adapter.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.fabric;

import mekanism.common.Mekanism;
import net.fabricmc.api.ModInitializer;
import ports.mekanism.platform.Platform;

/**
 * The Fabric main entrypoint: constructs Mekanism and runs it through Forge's common loading stages.
 */
public final class FabricMod implements ModInitializer {

    @Override
    public void onInitialize() {
        Platform.install(FabricPlatform.INSTANCE);
        FabricGameEvents.register();
        FabricLifecycle.addMod(Mekanism.MODID, Mekanism::new);
        FabricLifecycle.startCommon();
        FabricWorldgen.register();
    }
}

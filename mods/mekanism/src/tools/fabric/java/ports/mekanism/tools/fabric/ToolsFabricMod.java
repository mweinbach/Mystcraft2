/*
 * Mekanism 1.19.2 port - Fabric loader adapter.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.tools.fabric;

import mekanism.tools.common.MekanismTools;
import net.fabricmc.api.ModInitializer;
import ports.mekanism.fabric.FabricLifecycle;

public final class ToolsFabricMod implements ModInitializer {

    @Override
    public void onInitialize() {
        FabricLifecycle.addMod(MekanismTools.MODID, MekanismTools::new);
        FabricLifecycle.tryStart();
    }
}

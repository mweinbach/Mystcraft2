/*
 * Mekanism 1.19.2 port - Fabric loader adapter.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.additions.fabric;

import mekanism.additions.common.MekanismAdditions;
import net.fabricmc.api.ModInitializer;
import ports.mekanism.fabric.FabricLifecycle;

public final class AdditionsFabricMod implements ModInitializer {

    @Override
    public void onInitialize() {
        FabricLifecycle.addMod(MekanismAdditions.MODID, MekanismAdditions::new);
        FabricLifecycle.tryStart();
        FabricAdditionsSpawns.register();
    }
}

/*
 * Extreme Reactors 1.19.2 port - game tests
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.gametest;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.ResourceLocation;

/**
 * Stands in for a Fabric steel mod on the game test server: the built-in {@code steel} data pack puts netherite
 * ingots in {@code c:steel_ingots}, so the shared game tests cover the steel recipes on Fabric. Forge's game test
 * server has no steel unless Mekanism is added, which covers the steel-free recipes.
 *
 * <p>The build turns it off under -PwithProjectE ({@code bigreactors.gametest.steel=false}): ProjectE derives EMC from
 * the loaded recipes, so it would price every reinforced part with netherite, and the EMC audit compares against the
 * steel-free values. The shared tests then cover the steel-free recipes, as on Forge, and the -PwithMekanism run
 * covers the steel ones with real steel.
 */
public final class FabricGameTestSteel implements ModInitializer {

    @Override
    public void onInitialize() {

        if (System.getProperty("fabric-api.gametest") != null && Boolean.getBoolean("bigreactors.gametest.steel")) {
            ResourceManagerHelper.registerBuiltinResourcePack(new ResourceLocation("bigreactors_gametest", "steel"),
                    FabricLoader.getInstance().getModContainer("bigreactors_gametest").orElseThrow(),
                    ResourcePackActivationType.ALWAYS_ENABLED);
        }
    }
}

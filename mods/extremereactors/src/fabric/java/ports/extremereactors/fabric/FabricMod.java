/*
 * Extreme Reactors 1.19.2 port - Fabric adapter
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.fabric;

import it.zerono.mods.extremereactors.ExtremeReactors;
import it.zerono.mods.extremereactors.config.Config;
import it.zerono.mods.extremereactors.config.Worldgen;
import it.zerono.mods.extremereactors.config.conditions.ConfigCondition;
import it.zerono.mods.extremereactors.gamecontent.Content;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import ports.extremereactors.corelib.compat.mekanism.MekanismGas;
import ports.extremereactors.fabric.compat.FabricComputerCraft;
import ports.extremereactors.fabric.compat.FabricMekanism;
import ports.extremereactors.platform.Platform;

import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

/**
 * The Fabric entrypoint.
 */
public final class FabricMod implements ModInitializer {

    @Override
    public void onInitialize() {

        Platform.install(new FabricPlatform());
        ExtremeReactors.initialize();
        FabricPlatform.get().registerAll();
        ResourceConditions.register(ConfigCondition.ID,
                json -> ConfigCondition.test(GsonHelper.getAsString(json, ConfigCondition.JSON_CONFIG_NAME)));

        Content.onCommonInit();
        ExtremeReactors.getInstance().onCommonInit();

        if (Platform.isModLoaded("computercraft")) {
            FabricComputerCraft.initialize();
        }

        if (Platform.isModLoaded(MekanismGas.MOD_ID)) {
            FabricMekanism.initialize();
        }

        final Worldgen worldgen = Config.COMMON.worldgen;

        addOre(BiomeTags.IS_OVERWORLD, "yellorite_ore", worldgen::generatesYellorite);
        addOre(BiomeTags.IS_OVERWORLD, "yellorite_deepslate_ore", worldgen::generatesYellorite);
        addOre(BiomeTags.IS_END, "anglesite_ore", worldgen::generatesAnglesite);
        addOre(BiomeTags.IS_NETHER, "benitoite_ore", worldgen::generatesBenitoite);

        ServerLifecycleEvents.SERVER_STARTING.register(server -> processApiMessages());
    }

    /**
     * Process the modpack API file once every mod has registered its content: on the first client start or server
     * start. Fabric has no inter-mod messages; other mods call the Extreme Reactors API directly.
     */
    public static void processApiMessages() {

        if (!s_apiProcessed) {

            s_apiProcessed = true;
            ExtremeReactors.getInstance().onInterModProcess(method -> Stream.empty());
        }
    }

    /**
     * The selector, and so the config switch, is evaluated when a server starts, as Forge applies its biome modifiers.
     */
    private static void addOre(final TagKey<Biome> biomes, final String placedFeature, final BooleanSupplier enabled) {
        BiomeModifications.addFeature(BiomeSelectors.tag(biomes).and(context -> enabled.getAsBoolean()),
                GenerationStep.Decoration.UNDERGROUND_ORES,
                ResourceKey.create(Registry.PLACED_FEATURE_REGISTRY, ExtremeReactors.newID(placedFeature)));
    }

    private static boolean s_apiProcessed;
}

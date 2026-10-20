/*
 * Extreme Reactors 1.19.2 port - Fabric adapter
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.fabric;

import it.zerono.mods.extremereactors.ExtremeReactors;
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
import ports.extremereactors.platform.Platform;

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

        addOre(BiomeTags.IS_OVERWORLD, "yellorite_ore");
        addOre(BiomeTags.IS_OVERWORLD, "yellorite_deepslate_ore");
        addOre(BiomeTags.IS_END, "anglesite_ore");
        addOre(BiomeTags.IS_NETHER, "benitoite_ore");

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

    private static void addOre(final TagKey<Biome> biomes, final String placedFeature) {
        BiomeModifications.addFeature(BiomeSelectors.tag(biomes), GenerationStep.Decoration.UNDERGROUND_ORES,
                ResourceKey.create(Registry.PLACED_FEATURE_REGISTRY, ExtremeReactors.newID(placedFeature)));
    }

    private static boolean s_apiProcessed;
}

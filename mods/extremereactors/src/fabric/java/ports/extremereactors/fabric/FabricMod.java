/*
 * Extreme Reactors 1.19.2 port - Fabric adapter
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.fabric;

import com.mojang.logging.LogUtils;
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
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import org.slf4j.Logger;
import ports.extremereactors.corelib.compat.mekanism.MekanismGas;
import ports.extremereactors.fabric.api.ExtremeReactorsApiEntrypoint;
import ports.extremereactors.fabric.compat.FabricComputerCraft;
import ports.extremereactors.fabric.compat.FabricMekanism;
import ports.extremereactors.platform.Platform;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;

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
            initializeMekanism();
        }

        final Worldgen worldgen = Config.COMMON.worldgen;

        addOre(BiomeTags.IS_OVERWORLD, "yellorite_ore", worldgen::generatesYellorite);
        addOre(BiomeTags.IS_OVERWORLD, "yellorite_deepslate_ore", worldgen::generatesYellorite);
        addOre(BiomeTags.IS_END, "anglesite_ore", worldgen::generatesAnglesite);
        addOre(BiomeTags.IS_NETHER, "benitoite_ore", worldgen::generatesBenitoite);

        ServerLifecycleEvents.SERVER_STARTING.register(server -> processApiMessages());
    }

    /**
     * Process the API messages of the {@value ExtremeReactorsApiEntrypoint#KEY} entrypoints, the Fabric stand-in for
     * Forge's inter-mod messages, and the modpack API file, once every mod has registered its content: on the first
     * client start or server start.
     */
    public static void processApiMessages() {

        if (!s_apiProcessed) {

            s_apiProcessed = true;

            final Map<String, List<Runnable>> messages = collectApiMessages(FabricLoader.getInstance()
                    .getEntrypointContainers(ExtremeReactorsApiEntrypoint.KEY, ExtremeReactorsApiEntrypoint.class));

            ExtremeReactors.getInstance().onInterModProcess(method -> messages.getOrDefault(method, List.of()).stream());
        }
    }

    /**
     * @return the actions each entrypoint sent, by IMC method, in entrypoint order
     */
    static Map<String, List<Runnable>> collectApiMessages(
            final List<EntrypointContainer<ExtremeReactorsApiEntrypoint>> entrypoints) {

        final Set<String> methods = Set.of(ExtremeReactors.IMC_METHODS);
        final Map<String, List<Runnable>> messages = new HashMap<>();

        for (final EntrypointContainer<ExtremeReactorsApiEntrypoint> entrypoint : entrypoints) {

            final String modId = entrypoint.getProvider().getMetadata().getId();

            entrypoint.getEntrypoint().sendApiMessages((method, action) -> {

                Objects.requireNonNull(action, "action");

                if (methods.contains(method)) {
                    messages.computeIfAbsent(method, key -> new ArrayList<>()).add(action);
                } else {
                    LOGGER.warn("{} sent an Extreme Reactors API message for unknown method {}; it is ignored", modId, method);
                }
            });
        }

        return messages;
    }

    /**
     * The adapter is written against this repository's Mekanism Fabric port. Another Mekanism build with the same mod
     * id but without the port's API classes is left alone instead of crashing the game.
     */
    private static void initializeMekanism() {

        final ClassLoader loader = FabricMod.class.getClassLoader();

        for (final String name : MEKANISM_API_CLASSES) {
            try {
                Class.forName(name, false, loader);
            } catch (final ClassNotFoundException | LinkageError e) {

                LOGGER.warn("Mekanism is installed but {} is missing: the Mekanism gas port needs this repository's " +
                        "Mekanism Fabric port and stays inactive", name);
                return;
            }
        }

        try {
            FabricMekanism.initialize();
        } catch (final LinkageError e) {
            LOGGER.error("The installed Mekanism does not match the Mekanism gas port; the port stays inactive", e);
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

    /** The classes of this repository's Mekanism Fabric port that {@link FabricMekanism} links against. */
    private static final String[] MEKANISM_API_CLASSES = {
            "ports.mekanism.fabric.api.MekanismChemicalLookups",
            "mekanism.api.chemical.gas.IGasHandler",
            "mekanism.api.recipes.RotaryRecipe",
    };
    private static final Logger LOGGER = LogUtils.getLogger();

    private static boolean s_apiProcessed;
}

/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import static mekanism.gametest.CoreMachineGameTests.TEMPLATE;
import static mekanism.gametest.CoreMachineGameTests.check;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import mekanism.common.Mekanism;
import mekanism.common.config.MekanismConfig;
import mekanism.common.config.WorldConfig.OreVeinConfig;
import mekanism.common.registration.impl.SetupFeatureDeferredRegister.MekFeature;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.registries.MekanismFeatures;
import mekanism.common.resource.ore.OreBlockType;
import mekanism.common.resource.ore.OreType;
import mekanism.common.resource.ore.OreType.OreVeinType;
import mekanism.common.util.EnumUtils;
import mekanism.common.world.DisableableFeaturePlacement;
import mekanism.common.world.ResizableDiskConfig;
import mekanism.common.world.ResizableOreFeatureConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.HeightmapPlacement;
import net.minecraft.world.level.levelgen.placement.InSquarePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;

/**
 * Mekanism's ore and salt features are registered and attached to overworld biomes on the loader under test.
 * Forge attaches them with its biome modifiers; Fabric attaches the same placed features through its own API.
 */
public class OreGenerationGameTests {

    private static final Set<String> VEINS = Set.of(
          "ore_tin_small", "ore_tin_large",
          "ore_osmium_upper", "ore_osmium_middle", "ore_osmium_small",
          "ore_uranium_small", "ore_uranium_buried",
          "ore_fluorite_normal", "ore_fluorite_buried",
          "ore_lead_normal",
          "salt"
    );

    @GameTest(template = TEMPLATE)
    public static void oresAndSaltAreAttachedToOverworldBiomes(GameTestHelper helper) {
        Registry<PlacedFeature> placed = helper.getLevel().registryAccess().registryOrThrow(Registry.PLACED_FEATURE_REGISTRY);
        Registry<ConfiguredFeature<?, ?>> configured = helper.getLevel().registryAccess().registryOrThrow(Registry.CONFIGURED_FEATURE_REGISTRY);
        Registry<Biome> biomes = helper.getLevel().registryAccess().registryOrThrow(Registry.BIOME_REGISTRY);
        List<FeatureRef> features = features();
        Set<String> registered = new HashSet<>();
        for (FeatureRef feature : features) {
            registered.add(feature.path());
        }
        check(registered.equals(VEINS), "Ore and salt features were " + registered);

        PlacementContext placementContext = new PlacementContext(helper.getLevel(), helper.getLevel().getChunkSource().getGenerator(), Optional.empty());
        RandomSource random = RandomSource.create(1);
        for (FeatureRef feature : features) {
            checkFeature(placed, configured, feature, placementContext, random);
            checkAttached(biomes, feature.placedKey(), feature.path());
            FeatureRef retrogen = feature.asRetrogen();
            checkFeature(placed, configured, retrogen, placementContext, random);
            checkNotAttached(biomes, retrogen.placedKey(), retrogen.path());
        }
        checkConfigIsLive(placed, placementContext, random);
        helper.succeed();
    }

    private static void checkFeature(Registry<PlacedFeature> placed, Registry<ConfiguredFeature<?, ?>> configured, FeatureRef feature,
          PlacementContext placementContext, RandomSource random) {
        PlacedFeature placedFeature = placed.get(feature.placedKey());
        check(placedFeature != null, "Missing placed feature " + feature.path());
        ConfiguredFeature<?, ?> configuredFeature = configured.get(feature.configuredKey());
        check(configuredFeature != null, "Missing configured feature " + feature.path());
        check(placedFeature.feature().unwrapKey().orElseThrow().equals(feature.configuredKey()),
              feature.path() + " placed feature points at " + placedFeature.feature().unwrapKey());
        if (feature.salt()) {
            check(configuredFeature.config() instanceof ResizableDiskConfig disk && disk.state.is(MekanismBlocks.SALT_BLOCK.getBlock()),
                  feature.path() + " is not a salt disk");
        } else {
            check(configuredFeature.config() instanceof ResizableOreFeatureConfig ore
                        && ore.targetStates().stream().anyMatch(target -> target.state.is(feature.blocks().stoneBlock()))
                        && ore.targetStates().stream().anyMatch(target -> target.state.is(feature.blocks().deepslateBlock())),
                  feature.path() + " does not replace stone and deepslate with its ore");
        }
        List<PlacementModifier> modifiers = placedFeature.placement();
        check(!modifiers.isEmpty() && modifiers.get(0) instanceof DisableableFeaturePlacement,
              feature.path() + " is not gated by the world-generation config");
        check(modifiers.stream().anyMatch(CountPlacement.class::isInstance), feature.path() + " has no count");
        check(modifiers.stream().anyMatch(InSquarePlacement.class::isInstance), feature.path() + " has no horizontal spread");
        check(modifiers.stream().anyMatch(BiomeFilter.class::isInstance), feature.path() + " has no biome filter");
        if (feature.salt()) {
            check(modifiers.stream().anyMatch(HeightmapPlacement.class::isInstance), feature.path() + " has no surface heightmap");
        } else {
            check(modifiers.stream().anyMatch(HeightRangePlacement.class::isInstance), feature.path() + " has no height range");
        }
        DisableableFeaturePlacement gate = (DisableableFeaturePlacement) modifiers.get(0);
        boolean places = gate.getPositions(placementContext, random, BlockPos.ZERO).findAny().isPresent();
        boolean enabled = feature.enabled() && (!feature.retrogen() || MekanismConfig.world.enableRegeneration.get());
        check(places == enabled, feature.path() + (places ? " places" : " does not place") + " while its config says " + enabled);
        CountPlacement count = modifiers.stream().filter(CountPlacement.class::isInstance).map(CountPlacement.class::cast).findFirst().orElseThrow();
        int sampled = (int) count.getPositions(placementContext, random, BlockPos.ZERO).count();
        check(sampled == feature.perChunk(), feature.path() + " places " + sampled + " veins per chunk; the config says " + feature.perChunk());
    }

    private static void checkAttached(Registry<Biome> biomes, ResourceKey<PlacedFeature> feature, String path) {
        int overworld = 0;
        for (ResourceKey<Biome> biomeKey : biomes.registryKeySet()) {
            Biome biome = biomes.getOrThrow(biomeKey);
            boolean tagged = biomes.getHolderOrThrow(biomeKey).is(BiomeTags.IS_OVERWORLD);
            int attached = count(biome, feature);
            check(attached == (tagged ? 1 : 0), path + " is attached " + attached + " times in " + biomeKey.location()
                  + (tagged ? ", an overworld biome" : ", outside the overworld"));
            if (tagged) {
                overworld++;
            }
        }
        check(overworld > 0, path + " had no overworld biome to generate in");
    }

    private static void checkNotAttached(Registry<Biome> biomes, ResourceKey<PlacedFeature> feature, String path) {
        for (ResourceKey<Biome> biomeKey : biomes.registryKeySet()) {
            int attached = count(biomes.getOrThrow(biomeKey), feature);
            check(attached == 0, path + " is attached to " + biomeKey.location());
        }
    }

    private static int count(Biome biome, ResourceKey<PlacedFeature> feature) {
        int found = 0;
        List<HolderSet<PlacedFeature>> steps = biome.getGenerationSettings().features();
        for (int step = 0; step < steps.size(); step++) {
            int inStep = (int) steps.get(step).stream().filter(holder -> holder.is(feature)).count();
            if (inStep > 0) {
                check(step == GenerationStep.Decoration.UNDERGROUND_ORES.ordinal(),
                      feature.location() + " generates at step " + step + " instead of underground ores");
            }
            found += inStep;
        }
        return found;
    }

    /**
     * The placement modifiers read the world config when a chunk generates, so changing a toggle changes generation
     * without re-registering the feature.
     */
    private static void checkConfigIsLive(Registry<PlacedFeature> placed, PlacementContext placementContext, RandomSource random) {
        FeatureRef salt = features().stream().filter(FeatureRef::salt).findFirst().orElseThrow();
        PlacedFeature saltFeature = placed.getOrThrow(salt.placedKey());
        DisableableFeaturePlacement gate = (DisableableFeaturePlacement) saltFeature.placement().get(0);
        boolean original = MekanismConfig.world.salt.shouldGenerate.get();
        try {
            MekanismConfig.world.salt.shouldGenerate.set(false);
            check(gate.getPositions(placementContext, random, BlockPos.ZERO).findAny().isEmpty(),
                  "Salt still generates after its config toggle is turned off");
            MekanismConfig.world.salt.shouldGenerate.set(true);
            check(gate.getPositions(placementContext, random, BlockPos.ZERO).findAny().isPresent(),
                  "Salt does not generate after its config toggle is turned on");
        } finally {
            MekanismConfig.world.salt.shouldGenerate.set(original);
        }

        FeatureRef vein = features().stream().filter(feature -> feature.path().equals("ore_osmium_upper")).findFirst().orElseThrow();
        CountPlacement count = placed.getOrThrow(vein.placedKey()).placement().stream()
              .filter(CountPlacement.class::isInstance).map(CountPlacement.class::cast).findFirst().orElseThrow();
        OreVeinConfig veinConfig = MekanismConfig.world.getVeinConfig(new OreVeinType(OreType.OSMIUM, 0));
        int originalCount = veinConfig.perChunk().get();
        int changed = originalCount == 256 ? originalCount - 1 : originalCount + 1;
        try {
            veinConfig.perChunk().set(changed);
            int sampled = (int) count.getPositions(placementContext, random, BlockPos.ZERO).count();
            check(sampled == changed, "Osmium upper veins per chunk stayed " + sampled + " after the config changed to " + changed);
        } finally {
            veinConfig.perChunk().set(originalCount);
        }
    }

    private static List<FeatureRef> features() {
        List<FeatureRef> features = new ArrayList<>();
        for (OreType type : EnumUtils.ORE_TYPES) {
            MekFeature<?, ?>[] veins = MekanismFeatures.ORES.get(type);
            for (int index = 0; index < veins.length; index++) {
                features.add(FeatureRef.ore(veins[index], type, index));
            }
        }
        features.add(FeatureRef.salt(MekanismFeatures.SALT));
        return features;
    }

    private record FeatureRef(ResourceKey<PlacedFeature> placedKey, ResourceKey<ConfiguredFeature<?, ?>> configuredKey, boolean salt,
                              boolean retrogen, OreType oreType, int veinIndex) {

        private static FeatureRef ore(MekFeature<?, ?> feature, OreType type, int index) {
            return of(feature.feature().getPlacedFeatureKey(), false, false, type, index);
        }

        private static FeatureRef salt(MekFeature<?, ?> feature) {
            return of(feature.feature().getPlacedFeatureKey(), true, false, null, 0);
        }

        private static FeatureRef of(ResourceKey<PlacedFeature> placedKey, boolean salt, boolean retrogen, OreType oreType, int veinIndex) {
            ResourceKey<ConfiguredFeature<?, ?>> configuredKey = ResourceKey.create(Registry.CONFIGURED_FEATURE_REGISTRY, placedKey.location());
            return new FeatureRef(placedKey, configuredKey, salt, retrogen, oreType, veinIndex);
        }

        private FeatureRef asRetrogen() {
            ResourceLocation id = new ResourceLocation(Mekanism.MODID, path() + "_retrogen");
            return of(ResourceKey.create(Registry.PLACED_FEATURE_REGISTRY, id), salt, true, oreType, veinIndex);
        }

        private String path() {
            return placedKey.location().getPath();
        }

        private boolean enabled() {
            return salt ? MekanismConfig.world.salt.shouldGenerate.get() : veinConfig().shouldGenerate().getAsBoolean();
        }

        private int perChunk() {
            return salt ? MekanismConfig.world.salt.perChunk.get() : veinConfig().perChunk().get();
        }

        private OreVeinConfig veinConfig() {
            return MekanismConfig.world.getVeinConfig(new OreVeinType(oreType, veinIndex));
        }

        private OreBlockType blocks() {
            return MekanismBlocks.ORES.get(oreType);
        }
    }
}

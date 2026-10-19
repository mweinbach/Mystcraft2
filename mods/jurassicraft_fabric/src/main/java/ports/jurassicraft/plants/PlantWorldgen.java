package ports.jurassicraft.plants;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import ports.jurassicraft.PortMod;

/** Adds the same placed features as the Forge port using Fabric's biome API. */
public final class PlantWorldgen {
    private static final Map<String, BooleanSupplier> ENABLED = new HashMap<>();
    private PlantWorldgen() {}

    /** Set prior to world loading; keys are the placed feature IDs below. */
    public static void setEnabled(String feature, BooleanSupplier enabled) {
        ENABLED.put(feature, enabled);
    }

    private static boolean enabled(String feature) {
        BooleanSupplier value = ENABLED.get(feature);
        return value == null || value.getAsBoolean();
    }
    private static TagKey<Biome> tag(String id) {
        return TagKey.create(Registry.BIOME_REGISTRY, new ResourceLocation(PortMod.MOD_ID, id));
    }
    private static ResourceKey<PlacedFeature> feature(String id) {
        return ResourceKey.create(Registry.PLACED_FEATURE_REGISTRY, new ResourceLocation(PortMod.MOD_ID, id));
    }
    private static void add(String id, TagKey<Biome> biomes, GenerationStep.Decoration step) {
        BiomeModifications.addFeature(BiomeSelectors.tag(biomes).and(context -> enabled(id)), step, feature(id));
    }
    static void init() {
        add("petrified_tree", BiomeTags.IS_OVERWORLD, GenerationStep.Decoration.UNDERGROUND_ORES);
        add("peat", PeatBlock.SWAMPS, GenerationStep.Decoration.UNDERGROUND_ORES);
        add("coal_plant_fossils", BiomeTags.IS_OVERWORLD, GenerationStep.Decoration.UNDERGROUND_DECORATION);
        add("gracilaria", BiomeTags.IS_OCEAN, GenerationStep.Decoration.VEGETAL_DECORATION);
        add("moss", tag("moss_biomes"), GenerationStep.Decoration.VEGETAL_DECORATION);
        add("heliconia", BiomeTags.IS_JUNGLE, GenerationStep.Decoration.VEGETAL_DECORATION);
        add("west_indian_lilac", tag("lilac_biomes"), GenerationStep.Decoration.VEGETAL_DECORATION);
    }
}

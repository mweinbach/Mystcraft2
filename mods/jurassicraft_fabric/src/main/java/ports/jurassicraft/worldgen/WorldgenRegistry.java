package ports.jurassicraft.worldgen;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.config.JurassicConfig;
import ports.jurassicraft.content.Species;

public final class WorldgenRegistry {
    private WorldgenRegistry() {}
    private static ResourceLocation id(String name) { return new ResourceLocation(PortMod.MOD_ID, name); }
    private static void add(String name, String setting) {
        ResourceKey<PlacedFeature> feature = ResourceKey.create(Registry.PLACED_FEATURE_REGISTRY, id(name));
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld().and(context -> JurassicConfig.generation(setting)),
                GenerationStep.Decoration.UNDERGROUND_ORES, feature);
    }
    public static void init() {
        Registry.register(Registry.INT_PROVIDER_TYPES, id("binomial_count"), BinomialCount.TYPE);
        Registry.register(Registry.FEATURE, id("fossil_trackway"), new FossilTrackwayFeature());
        add("fossil_trackway", "trackwayGeneration");
        add("amber_ore", "amberGeneration");
        add("ice_shard", "iceShardGeneration");
        add("gypsum_stone", "gypsumGeneration");
        for (Species species : Species.values()) add(species.id() + "_fossil_block", "fossilGeneration");
    }
}

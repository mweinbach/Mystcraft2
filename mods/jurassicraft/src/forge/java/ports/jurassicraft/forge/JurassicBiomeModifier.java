package ports.jurassicraft.forge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.RegistryOps;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.common.world.ModifiableBiomeInfo;
import ports.jurassicraft.worldgen.NaturalSpawns;
import ports.jurassicraft.worldgen.WorldgenPlacements;

/** Applies the shared placement and natural-spawn tables to each biome. */
public record JurassicBiomeModifier(Registry<PlacedFeature> features) implements BiomeModifier {
    static final Codec<JurassicBiomeModifier> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            RegistryOps.retrieveRegistry(Registry.PLACED_FEATURE_REGISTRY).forGetter(JurassicBiomeModifier::features)
    ).apply(instance, JurassicBiomeModifier::new));

    @Override public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase != Phase.ADD) return;
        for (var placement : WorldgenPlacements.all()) {
            if (placement.enabled() && biome.is(placement.biomes()))
                builder.getGenerationSettings().addFeature(placement.step(), features.getHolderOrThrow(placement.feature()));
        }
        var spawns = builder.getMobSpawnSettings();
        NaturalSpawns.BiomeView view = new NaturalSpawns.BiomeView() {
            @Override public boolean is(TagKey<Biome> tag) { return biome.is(tag); }
            @Override public boolean spawns(MobCategory category, EntityType<?> type) {
                return spawns.getSpawner(category).stream().anyMatch(spawn -> spawn.type == type);
            }
        };
        for (var entry : NaturalSpawns.entries()) {
            if (!entry.rule().accepts(view) || view.spawns(entry.category(), entry.type())) continue;
            spawns.getSpawner(entry.category()).add(new MobSpawnSettings.SpawnerData(entry.type(), entry.weight(), entry.minCount(), entry.maxCount()));
        }
    }

    @Override public Codec<? extends BiomeModifier> codec() { return CODEC; }
}

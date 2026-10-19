package ports.jurassicraft.entity;

import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.common.world.ModifiableBiomeInfo;
import ports.jurassicraft.config.JurassicConfig;
import ports.jurassicraft.content.Species;

/** Original natural spawn weights and biome-type unions, opt-in via the common config. */
public final class DinosaurBiomeModifier implements BiomeModifier {
    @Override public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase == Phase.ADD) apply(biome, builder, JurassicConfig.NATURAL_DINOSAUR_SPAWNING.get());
    }

    static void apply(Holder<Biome> biome, ModifiableBiomeInfo.BiomeInfo.Builder builder, boolean enabled) {
        if (!enabled) return;
        for (Species species : Species.values()) {
            if (!matches(species, biome)) continue;
            MobCategory category = species == Species.COELACANTH ? MobCategory.WATER_CREATURE : MobCategory.CREATURE;
            var spawns = builder.getMobSpawnSettings().getSpawner(category);
            if (spawns.stream().anyMatch(spawn -> spawn.type == DinosaurRegistry.type(species))) continue;
            int maxGroup = Math.max(1, Math.min(6, DinosaurHerd.maxSize(species) / 2));
            spawns.add(new MobSpawnSettings.SpawnerData(DinosaurRegistry.type(species), weight(species), 1, maxGroup));
        }
    }

    static boolean matches(Species species, Holder<Biome> biome) {
        boolean forest = biome.is(BiomeTags.IS_FOREST);
        boolean plains = biome.is(Tags.Biomes.IS_PLAINS);
        return switch (species) {
            case VELOCIRAPTOR, MICRORAPTOR -> biome.is(BiomeTags.IS_JUNGLE) || forest || biome.is(Tags.Biomes.IS_DENSE);
            case COELACANTH -> biome.is(BiomeTags.IS_OCEAN) || biome.is(Tags.Biomes.IS_WATER) || biome.is(BiomeTags.IS_RIVER);
            case BRACHIOSAURUS, DILOPHOSAURUS -> forest;
            case MUSSAURUS, PARASAUROLOPHUS, TYRANNOSAURUS -> plains || forest;
            case GALLIMIMUS -> plains || biome.is(Tags.Biomes.IS_DRY);
            case TRICERATOPS -> plains || biome.is(Tags.Biomes.IS_SPARSE) || forest;
        };
    }

    static int weight(Species species) {
        return switch (species) {
            case COELACANTH, BRACHIOSAURUS, TYRANNOSAURUS -> 5;
            case MUSSAURUS, PARASAUROLOPHUS -> 15;
            case GALLIMIMUS -> 25;
            default -> 10;
        };
    }

    @Override public Codec<? extends BiomeModifier> codec() { return DinosaurRegistry.SPAWN_MODIFIER.get(); }
}

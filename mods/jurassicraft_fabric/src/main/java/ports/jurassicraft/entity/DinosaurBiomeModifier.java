package ports.jurassicraft.entity;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.fabricmc.fabric.api.tag.convention.v1.ConventionalBiomeTags;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.MobCategory;
import ports.jurassicraft.config.JurassicConfig;
import ports.jurassicraft.content.Species;

/** Preserve the Forge port's opt-in biome unions, weights and herd-based group sizes. */
public final class DinosaurBiomeModifier {
    private DinosaurBiomeModifier() {}
    static void init() {
        if (!JurassicConfig.NATURAL_DINOSAUR_SPAWNING.get()) return;
        for (Species species : Species.values()) {
            MobCategory category = species == Species.COELACANTH ? MobCategory.WATER_CREATURE : MobCategory.CREATURE;
            int maxGroup = Math.max(1, Math.min(6, DinosaurHerd.maxSize(species) / 2));
            BiomeModifications.addSpawn(context -> matches(species, context), category,
                    DinosaurRegistry.type(species), weight(species), 1, maxGroup);
        }
    }
    static boolean matches(Species species, BiomeSelectionContext biome) {
        boolean forest = biome.hasTag(BiomeTags.IS_FOREST) || biome.hasTag(ConventionalBiomeTags.FOREST);
        boolean plains = biome.hasTag(ConventionalBiomeTags.PLAINS);
        return switch (species) {
            case VELOCIRAPTOR, MICRORAPTOR -> biome.hasTag(BiomeTags.IS_JUNGLE) || forest
                    || biome.hasTag(ConventionalBiomeTags.VEGETATION_DENSE);
            case COELACANTH -> biome.hasTag(BiomeTags.IS_OCEAN) || biome.hasTag(BiomeTags.IS_RIVER)
                    || biome.hasTag(ConventionalBiomeTags.AQUATIC);
            case BRACHIOSAURUS, DILOPHOSAURUS -> forest;
            case MUSSAURUS, PARASAUROLOPHUS, TYRANNOSAURUS -> plains || forest;
            case GALLIMIMUS -> plains || biome.hasTag(ConventionalBiomeTags.CLIMATE_DRY);
            case TRICERATOPS -> plains || biome.hasTag(ConventionalBiomeTags.VEGETATION_SPARSE) || forest;
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
}

package net.mystcraft.integration.exploration;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.common.world.ModifiableBiomeInfo;

/**
 * Adds the dimension-filtered exploration features to {@code biomes}, normally {@code #mystcraft:has_exploration}.
 * Fabric selects the same tag, so a datapack retargets or disables the features on both loaders.
 */
public record ExplorationBiomeModifier(HolderSet<Biome> biomes, Holder<PlacedFeature> library, Holder<PlacedFeature> crystal) implements BiomeModifier {
    public static final Codec<ExplorationBiomeModifier> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Biome.LIST_CODEC.fieldOf("biomes").forGetter(ExplorationBiomeModifier::biomes),
            PlacedFeature.CODEC.fieldOf("library").forGetter(ExplorationBiomeModifier::library),
            PlacedFeature.CODEC.fieldOf("crystal").forGetter(ExplorationBiomeModifier::crystal)
    ).apply(instance, ExplorationBiomeModifier::new));
    @Override public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase == Phase.ADD && biomes.contains(biome)) {
            builder.getGenerationSettings().addFeature(GenerationStep.Decoration.SURFACE_STRUCTURES, library);
            builder.getGenerationSettings().addFeature(GenerationStep.Decoration.UNDERGROUND_DECORATION, crystal);
        }
    }
    @Override public Codec<? extends BiomeModifier> codec() { return CODEC; }
}

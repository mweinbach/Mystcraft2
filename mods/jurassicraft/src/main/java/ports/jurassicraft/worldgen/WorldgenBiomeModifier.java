package ports.jurassicraft.worldgen;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.common.world.ModifiableBiomeInfo;
import ports.jurassicraft.config.JurassicConfig;

/** Remove only disabled, explicitly named JurassiCraft features after all additions. */
public final class WorldgenBiomeModifier implements BiomeModifier {
    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase != Phase.REMOVE) return;
        for (GenerationStep.Decoration step : GenerationStep.Decoration.values()) {
            removeDisabled(builder.getGenerationSettings().getFeatures(step), JurassicConfig::worldgenEnabled);
        }
    }

    static void removeDisabled(List<Holder<PlacedFeature>> features, Predicate<WorldgenCategory> enabled) {
        features.removeIf(holder -> holder.unwrapKey().map(key -> WorldgenCategory.forFeature(key.location()))
                .map(category -> !enabled.test(category)).orElse(false));
    }

    @Override public Codec<? extends BiomeModifier> codec() { return WorldgenRegistry.FILTER.get(); }
}

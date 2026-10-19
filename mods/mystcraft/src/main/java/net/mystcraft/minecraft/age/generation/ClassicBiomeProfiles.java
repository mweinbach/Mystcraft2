package net.mystcraft.minecraft.age.generation;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

/** Frozen v2 mapping: modern biome IDs no longer carry legacy depth/variation properties. */
final class ClassicBiomeProfiles {
    private ClassicBiomeProfiles() {}
    static ClassicNormalDensity.Profile profile(Holder<Biome> biome) {
        var id = biome.unwrapKey().map(key -> key.location()).orElse(null);
        return profile(id);
    }
    static ClassicNormalDensity.Profile profile(net.minecraft.resources.ResourceLocation id) {
        if (id == null || !id.getNamespace().equals("minecraft")) return new ClassicNormalDensity.Profile(0.125F, 0.05F);
        String name = id.getPath();
        if (name.contains("deep") && name.contains("ocean")) return new ClassicNormalDensity.Profile(-1.8F, 0.1F);
        if (name.contains("ocean")) return new ClassicNormalDensity.Profile(-1, 0.1F);
        return switch (name) {
            case "plains", "sunflower_plains", "desert", "snowy_plains", "savanna" -> new ClassicNormalDensity.Profile(0.125F, 0.05F);
            case "river", "frozen_river" -> new ClassicNormalDensity.Profile(-0.5F, 0);
            case "swamp", "mangrove_swamp" -> new ClassicNormalDensity.Profile(-0.2F, 0.1F);
            case "taiga", "snowy_taiga", "old_growth_pine_taiga", "old_growth_spruce_taiga" -> new ClassicNormalDensity.Profile(0.2F, 0.2F);
            case "windswept_hills", "windswept_forest", "windswept_gravelly_hills", "stony_peaks", "jagged_peaks", "frozen_peaks" -> new ClassicNormalDensity.Profile(1, 0.5F);
            case "savanna_plateau", "wooded_badlands" -> new ClassicNormalDensity.Profile(1.5F, 0.025F);
            case "windswept_savanna" -> new ClassicNormalDensity.Profile(0.3625F, 1.225F);
            case "beach", "snowy_beach" -> new ClassicNormalDensity.Profile(0, 0.025F);
            case "stony_shore" -> new ClassicNormalDensity.Profile(0.1F, 0.8F);
            case "mushroom_fields" -> new ClassicNormalDensity.Profile(0.2F, 0.3F);
            case "flower_forest" -> new ClassicNormalDensity.Profile(0.1F, 0.4F);
            default -> new ClassicNormalDensity.Profile(0.1F, 0.2F);
        };
    }
}

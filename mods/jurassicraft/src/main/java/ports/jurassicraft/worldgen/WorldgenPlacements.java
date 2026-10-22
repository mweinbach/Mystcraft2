package ports.jurassicraft.worldgen;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import ports.jurassicraft.config.JurassicConfig;
import ports.jurassicraft.platform.Reg;

/** Every placed feature JurassiCraft adds to biomes; both loaders apply exactly this table. */
public final class WorldgenPlacements {
    public record Placement(ResourceKey<PlacedFeature> feature, TagKey<Biome> biomes, GenerationStep.Decoration step,
                            @Nullable WorldgenCategory category) {
        /** Reads the common config, so call this only once configuration has loaded. */
        public boolean enabled() { return category == null || JurassicConfig.worldgenEnabled(category); }

        /** Coal scanned after ordinary feature additions, so ores added in later steps are included. */
        public boolean afterOtherFeatures() { return category == WorldgenCategory.PLANT_FOSSILS; }
    }
    private static final List<Placement> ALL = create();
    private WorldgenPlacements() {}

    public static List<Placement> all() { return ALL; }

    private static List<Placement> create() {
        List<Placement> list = new ArrayList<>();
        var ores = GenerationStep.Decoration.UNDERGROUND_ORES;
        var plants = GenerationStep.Decoration.VEGETAL_DECORATION;
        add(list, "amber_ore", BiomeTags.IS_OVERWORLD, ores);
        add(list, "ice_shard", BiomeTags.IS_OVERWORLD, ores);
        add(list, "gypsum_stone", BiomeTags.IS_OVERWORLD, ores);
        add(list, "fossil_deposits", BiomeTags.IS_OVERWORLD, ores);
        add(list, "fossil_nest", BiomeTags.IS_OVERWORLD, ores);
        add(list, "fossil_trackway", BiomeTags.IS_OVERWORLD, ores);
        add(list, "petrified_tree", BiomeTags.IS_OVERWORLD, ores);
        add(list, "peat", tag("swamps"), ores);
        add(list, "coal_plant_fossils", BiomeTags.IS_OVERWORLD, GenerationStep.Decoration.TOP_LAYER_MODIFICATION);
        add(list, "gracilaria", BiomeTags.IS_OCEAN, plants);
        add(list, "moss", tag("moss_biomes"), plants);
        add(list, "heliconia", BiomeTags.IS_JUNGLE, plants);
        add(list, "west_indian_lilac", tag("lilac_biomes"), plants);
        return List.copyOf(list);
    }
    private static void add(List<Placement> list, String name, TagKey<Biome> biomes, GenerationStep.Decoration step) {
        var id = Reg.id(name);
        list.add(new Placement(ResourceKey.create(Registry.PLACED_FEATURE_REGISTRY, id), biomes, step, WorldgenCategory.forFeature(id)));
    }
    public static TagKey<Biome> tag(String name) { return TagKey.create(Registry.BIOME_REGISTRY, Reg.id(name)); }
}

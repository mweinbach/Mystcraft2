package ports.jurassicraft.worldgen;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.content.Species;

/** Exact placed-feature IDs belonging to each original generation toggle. */
public enum WorldgenCategory {
    FOSSILS("mineralGeneration", "fossilGeneration", "Generate prehistoric animal fossils."),
    NEST_FOSSILS("mineralGeneration", "nestFossilGeneration", "Generate fossil nest deposits.", "fossil_nest"),
    TRACKWAYS("mineralGeneration", "trackwayGeneration", "Generate independent fossil trackways.", "fossil_trackway"),
    PLANT_FOSSILS("mineralGeneration", "plantFossilGeneration", "Generate plant fossils in coal deposits.", "coal_plant_fossils"),
    AMBER("mineralGeneration", "amberGeneration", "Generate amber ore.", "amber_ore"),
    ICE_SHARDS("mineralGeneration", "iceShardGeneration", "Generate ice shards.", "ice_shard"),
    GYPSUM("mineralGeneration", "gypsumGeneration", "Generate gypsum.", "gypsum_stone"),
    PETRIFIED_TREES("mineralGeneration", "petrifiedTreeGeneration", "Generate petrified trees.", "petrified_tree"),
    MOSS("plantGeneration", "mossGeneration", "Generate moss.", "moss"),
    PEAT("plantGeneration", "peatGeneration", "Generate peat.", "peat"),
    FLOWERS("plantGeneration", "flowerGeneration", "Generate West Indian lilac.", "west_indian_lilac"),
    GRACILARIA("plantGeneration", "gracilariaGeneration", "Generate gracilaria.", "gracilaria");

    private final String section;
    private final String key;
    private final String description;
    private final String[] features;
    private static final Map<ResourceLocation, WorldgenCategory> BY_FEATURE = new HashMap<>();
    static {
        for (WorldgenCategory category : values()) {
            for (String feature : category.features) add(feature, category);
        }
        for (Species species : Species.values()) add(species.id() + "_fossil_block", FOSSILS);
    }

    WorldgenCategory(String section, String key, String description, String... features) {
        this.section = section;
        this.key = key;
        this.description = description;
        this.features = features;
    }
    private static void add(String name, WorldgenCategory category) {
        if (BY_FEATURE.put(new ResourceLocation(PortMod.MOD_ID, name), category) != null)
            throw new IllegalStateException("Duplicate worldgen feature " + name);
    }
    public String section() { return section; }
    public String key() { return key; }
    public String description() { return description; }
    public static WorldgenCategory forFeature(ResourceLocation name) { return BY_FEATURE.get(name); }
}

package ports.jurassicraft.config;

import java.util.EnumMap;
import ports.jurassicraft.vehicle.VehicleConfig;
import ports.jurassicraft.worldgen.WorldgenCategory;

/** Common configuration shared by entity spawning and natural world generation. */
public final class JurassicConfig {
    private static final ConfigOption.File FILE = ConfigOption.File.COMMON;
    public static final ConfigOption<Boolean> NATURAL_GOAT_SPAWNING = ConfigOption.bool(FILE, null, "naturalGoatSpawning", false, true,
            "Spawn goats in biomes that spawn pigs; restart required.");
    public static final ConfigOption<Boolean> NATURAL_DINOSAUR_SPAWNING = ConfigOption.bool(FILE, null, "naturalDinosaurSpawning", false, true,
            "Enable natural dinosaur spawning; restart required.");
    public static final ConfigOption<Boolean> ALLOW_CARCASS = ConfigOption.bool(FILE, null, "allowCarcass", true, false,
            "Leave dinosaur carcasses that can be harvested for meat and bones.");
    public static final ConfigOption<Boolean> HUNT_WHEN_HUNGRY = ConfigOption.bool(FILE, null, "huntOnlyWhenHungry", false, false,
            "Only allow dinosaurs to hunt when they are hungry.");
    public static final ConfigOption<Boolean> DISABLE_GROWTH_SERUM = ConfigOption.bool(FILE, "items", "disableGrowthSerum", false, false,
            "Disable the growth serum's effect on dinosaurs.");
    public static final ConfigOption<Integer> LOOT_SPAWNRATE = ConfigOption.range(FILE, "items", "lootSpawnrate", 100, 0, 10000, false,
            "Percentage of JurassiCraft items added to vanilla chest loot; reload loot tables after changing.");
    public static final ConfigOption<Boolean> GENERATE_VISITOR_CENTRE = ConfigOption.bool(FILE, "structureGeneration", "visitorCentreGeneration", true, true,
            "Generate the visitor centre; restart required.");
    public static final ConfigOption<Boolean> GENERATE_RAPTOR_PADDOCK = ConfigOption.bool(FILE, "structureGeneration", "raptorPaddockGeneration", true, true,
            "Generate raptor paddocks; restart required.");
    public static final ConfigOption<Integer> RAPTOR_PADDOCK_RARITY = ConfigOption.range(FILE, "structureGeneration", "paddockRarity", 4000, 1, 16777216, true,
            "Approximate chance of a paddock per eligible chunk, as one in this number; restart required.");
    private static final EnumMap<WorldgenCategory, ConfigOption<Boolean>> WORLDGEN = new EnumMap<>(WorldgenCategory.class);
    static {
        for (WorldgenCategory category : WorldgenCategory.values()) {
            if (category.section().equals("mineralGeneration"))
                WORLDGEN.put(category, ConfigOption.bool(FILE, category.section(), category.key(), true, true, category.description() + " Restart required."));
        }
        for (WorldgenCategory category : WorldgenCategory.values()) {
            if (category.section().equals("plantGeneration"))
                WORLDGEN.put(category, ConfigOption.bool(FILE, category.section(), category.key(), true, true, category.description() + " Restart required."));
        }
    }
    public static final ConfigOption<Integer> PEAT_SPREAD_DISTANCE = ConfigOption.range(FILE, "plantGeneration", "peatGenerationSpeed", 20, 0, 1024, false,
            "Peat spread sampling distance; lower values spread faster. Zero disables spreading.");

    private JurassicConfig() {}

    /** Ensures every option class has defined its settings before a backend enumerates them. */
    static void load() { VehicleConfig.load(); }

    public static boolean worldgenEnabled(WorldgenCategory category) {
        return WORLDGEN.get(category).get();
    }

    /** Data generation decodes placement codecs before configs load; the default applies until then. */
    public static int paddockRarity() {
        return RAPTOR_PADDOCK_RARITY.get();
    }
}

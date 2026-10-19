package ports.jurassicraft.config;

import java.util.EnumMap;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import ports.jurassicraft.worldgen.WorldgenCategory;

/** Common configuration shared by entity spawning and natural world generation. */
public final class JurassicConfig {
    private static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue NATURAL_GOAT_SPAWNING;
    public static final ForgeConfigSpec.BooleanValue NATURAL_DINOSAUR_SPAWNING;
    public static final ForgeConfigSpec.IntValue PEAT_SPREAD_DISTANCE;
    public static final ForgeConfigSpec.BooleanValue ALLOW_CARCASS;
    public static final ForgeConfigSpec.BooleanValue HUNT_WHEN_HUNGRY;
    public static final ForgeConfigSpec.BooleanValue DISABLE_GROWTH_SERUM;
    public static final ForgeConfigSpec.IntValue LOOT_SPAWNRATE;
    public static final ForgeConfigSpec.BooleanValue GENERATE_VISITOR_CENTRE;
    public static final ForgeConfigSpec.BooleanValue GENERATE_RAPTOR_PADDOCK;
    public static final ForgeConfigSpec.IntValue RAPTOR_PADDOCK_RARITY;
    private static final EnumMap<WorldgenCategory, ForgeConfigSpec.BooleanValue> WORLDGEN = new EnumMap<>(WorldgenCategory.class);

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        NATURAL_GOAT_SPAWNING = builder.comment("Spawn goats in biomes that spawn pigs; restart required.")
                .worldRestart().define("naturalGoatSpawning", false);
        NATURAL_DINOSAUR_SPAWNING = builder.comment("Enable natural dinosaur spawning; restart required.")
                .worldRestart().define("naturalDinosaurSpawning", false);
        ALLOW_CARCASS = builder.comment("Leave dinosaur carcasses that can be harvested for meat and bones.")
                .define("allowCarcass", true);
        HUNT_WHEN_HUNGRY = builder.comment("Only allow dinosaurs to hunt when they are hungry.")
                .define("huntOnlyWhenHungry", false);
        builder.push("items");
        DISABLE_GROWTH_SERUM = builder.comment("Disable the growth serum's effect on dinosaurs.")
                .define("disableGrowthSerum", false);
        LOOT_SPAWNRATE = builder.comment("Percentage of JurassiCraft items added to vanilla chest loot; reload loot tables after changing.")
                .defineInRange("lootSpawnrate", 100, 0, 10000);
        builder.pop();
        builder.push("structureGeneration");
        GENERATE_VISITOR_CENTRE = builder.comment("Generate the visitor centre; restart required.")
                .worldRestart().define("visitorCentreGeneration", true);
        GENERATE_RAPTOR_PADDOCK = builder.comment("Generate raptor paddocks; restart required.")
                .worldRestart().define("raptorPaddockGeneration", true);
        RAPTOR_PADDOCK_RARITY = builder.comment("Approximate chance of a paddock per eligible chunk, as one in this number; restart required.")
                .worldRestart().defineInRange("paddockRarity", 4000, 1, 16777216);
        builder.pop();
        builder.push("mineralGeneration");
        addWorldgenSettings(builder, "mineralGeneration");
        builder.pop();
        builder.push("plantGeneration");
        addWorldgenSettings(builder, "plantGeneration");
        PEAT_SPREAD_DISTANCE = builder.comment("Peat spread sampling distance; lower values spread faster. Zero disables spreading.")
                .defineInRange("peatGenerationSpeed", 20, 0, 1024);
        builder.pop();
        SPEC = builder.build();
    }

    private static void addWorldgenSettings(ForgeConfigSpec.Builder builder, String section) {
        for (WorldgenCategory category : WorldgenCategory.values()) {
            if (category.section().equals(section)) {
                WORLDGEN.put(category, builder.comment(category.description() + " Restart required.")
                        .worldRestart().define(category.key(), true));
            }
        }
    }

    private JurassicConfig() {}

    /** Register once during mod construction, before loading a world. */
    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC, "jurassicraft-common.toml");
    }

    public static boolean worldgenEnabled(WorldgenCategory category) {
        return WORLDGEN.get(category).get();
    }

    /** Data generation decodes placement codecs without loading common configs. */
    public static int paddockRarity() {
        return SPEC.isLoaded() ? RAPTOR_PADDOCK_RARITY.get() : RAPTOR_PADDOCK_RARITY.getDefault();
    }
}

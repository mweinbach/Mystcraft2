package ports.jurassicraft.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Server settings; world-generation changes apply to newly generated chunks. */
public final class JurassicConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("jurassicraft");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final Value<Boolean> NATURAL_GOAT_SPAWNING = new Value<>(false);
    public static final Value<Boolean> NATURAL_DINOSAUR_SPAWNING = new Value<>(false);
    public static final Value<Boolean> ALLOW_CARCASS = new Value<>(true);
    public static final Value<Boolean> HUNT_WHEN_HUNGRY = new Value<>(false);
    public static final Value<Boolean> DISABLE_GROWTH_SERUM = new Value<>(false);
    public static final Value<Integer> PEAT_SPREAD_DISTANCE = new Value<>(20);
    public static final Value<Integer> LOOT_SPAWNRATE = new Value<>(100);
    public static final Value<Boolean> GENERATE_VISITOR_CENTRE = new Value<>(true);
    public static final Value<Boolean> GENERATE_RAPTOR_PADDOCK = new Value<>(true);
    public static final Value<Integer> RAPTOR_PADDOCK_RARITY = new Value<>(4000);
    private static final Map<String, Value<Boolean>> GENERATION = new LinkedHashMap<>();
    static {
        for (String key : new String[] {"fossilGeneration", "nestFossilGeneration", "trackwayGeneration",
                "plantFossilGeneration", "amberGeneration", "iceShardGeneration", "gypsumGeneration",
                "petrifiedTreeGeneration", "mossGeneration", "peatGeneration", "flowerGeneration", "gracilariaGeneration"}) {
            GENERATION.put(key, new Value<>(true));
        }
    }

    private JurassicConfig() {}

    public static final class Value<T> {
        private T value;
        private Value(T value) { this.value = value; }
        public T get() { return value; }
        private void set(T value) { this.value = value; }
    }

    public static void init() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("jurassicraft-fabric.json");
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path.getParent());
                try (var writer = Files.newBufferedWriter(path)) { GSON.toJson(defaults(), writer); }
            } catch (IOException e) { LOGGER.warn("Could not create JurassiCraft configuration {}", path, e); }
            return;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            bool(json, "naturalGoatSpawning", NATURAL_GOAT_SPAWNING);
            bool(json, "naturalDinosaurSpawning", NATURAL_DINOSAUR_SPAWNING);
            bool(json, "allowCarcass", ALLOW_CARCASS);
            bool(json, "huntOnlyWhenHungry", HUNT_WHEN_HUNGRY);
            bool(json, "disableGrowthSerum", DISABLE_GROWTH_SERUM);
            number(json, "peatGenerationSpeed", PEAT_SPREAD_DISTANCE, 0, 1024);
            number(json, "lootSpawnrate", LOOT_SPAWNRATE, 0, 10000);
            bool(json, "visitorCentreGeneration", GENERATE_VISITOR_CENTRE);
            bool(json, "raptorPaddockGeneration", GENERATE_RAPTOR_PADDOCK);
            number(json, "paddockRarity", RAPTOR_PADDOCK_RARITY, 1, 16777216);
            GENERATION.forEach((key, value) -> bool(json, key, value));
        } catch (Exception e) {
            LOGGER.warn("Could not read JurassiCraft configuration {}; keeping defaults", path, e);
        }
    }

    private static JsonObject defaults() {
        JsonObject json = new JsonObject();
        json.addProperty("naturalGoatSpawning", NATURAL_GOAT_SPAWNING.get());
        json.addProperty("naturalDinosaurSpawning", NATURAL_DINOSAUR_SPAWNING.get());
        json.addProperty("allowCarcass", ALLOW_CARCASS.get());
        json.addProperty("huntOnlyWhenHungry", HUNT_WHEN_HUNGRY.get());
        json.addProperty("disableGrowthSerum", DISABLE_GROWTH_SERUM.get());
        json.addProperty("peatGenerationSpeed", PEAT_SPREAD_DISTANCE.get());
        json.addProperty("lootSpawnrate", LOOT_SPAWNRATE.get());
        json.addProperty("visitorCentreGeneration", GENERATE_VISITOR_CENTRE.get());
        json.addProperty("raptorPaddockGeneration", GENERATE_RAPTOR_PADDOCK.get());
        json.addProperty("paddockRarity", RAPTOR_PADDOCK_RARITY.get());
        GENERATION.forEach((key, value) -> json.addProperty(key, value.get()));
        return json;
    }

    public static boolean generation(String key) {
        Value<Boolean> value = GENERATION.get(key);
        return value == null || value.get();
    }

    private static void bool(JsonObject json, String key, Value<Boolean> target) {
        JsonElement element = json.get(key);
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
            target.set(element.getAsBoolean());
        }
    }

    private static void number(JsonObject json, String key, Value<Integer> target, int min, int max) {
        JsonElement element = json.get(key);
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            try {
                int value = element.getAsBigDecimal().intValueExact();
                if (value >= min && value <= max) target.set(value);
            } catch (ArithmeticException ignored) { /* Keep the default for invalid values. */ }
        }
    }
}

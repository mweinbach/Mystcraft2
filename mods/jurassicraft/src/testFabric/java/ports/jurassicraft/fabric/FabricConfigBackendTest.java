package ports.jurassicraft.fabric;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import ports.jurassicraft.config.ConfigOption;
import ports.jurassicraft.config.JurassicConfig;
import ports.jurassicraft.vehicle.VehicleConfig;

/** Standalone checks of the Fabric configuration files; runs without a game instance. */
public final class FabricConfigBackendTest {
    public static void main(String[] args) throws IOException {
        freshFilesHoldEveryOption();
        missingKeysAreMergedIntoExistingFiles();
        reloadAppliesEditsAndRestoresRemovedKeys();
        legacyTomlNamedPropertiesAreMigrated();
        unreadableJsonIsNeverOverwritten();
        System.out.println("FabricConfigBackendTest passed");
    }

    private static void freshFilesHoldEveryOption() throws IOException {
        Path dir = Files.createTempDirectory("jurassicraft-config");
        try {
            FabricConfigBackend.loadCommon(dir);
            FabricConfigBackend.loadClient(dir);
            JsonObject json = json(dir);
            for (ConfigOption<?> option : ConfigOption.all()) {
                if (option.file() == ConfigOption.File.COMMON) check(json.has(option.key()), "new JSON lacks " + option.key());
            }
            check(read(dir, "jurassicraft-vehicles.properties").contains("destroyBlocks=true"), "new vehicle file lacks destroyBlocks");
            check(read(dir, "jurassicraft-vehicle-client.properties").contains("helicopterZoomout=false"), "new client file lacks helicopterZoomout");
            check(!Files.exists(dir.resolve("jurassicraft-vehicles.toml")), "Fabric must not write a properties file named .toml");
        } finally { delete(dir); }
    }

    private static void missingKeysAreMergedIntoExistingFiles() throws IOException {
        Path dir = Files.createTempDirectory("jurassicraft-config");
        try {
            // A file from an older release: one known key, one unknown key, no newer keys.
            Files.writeString(dir.resolve(FabricConfigBackend.COMMON_FILE), "{\"lootSpawnrate\": 250, \"userNote\": \"keep me\"}");
            String vehicles = "# my own comment\nunrelated = 7\n";
            Files.writeString(dir.resolve("jurassicraft-vehicles.properties"), vehicles);
            FabricConfigBackend.loadCommon(dir);

            check(JurassicConfig.LOOT_SPAWNRATE.get() == 250, "existing value not applied");
            check(VehicleConfig.DESTROY_BLOCKS.get(), "missing vehicle key not defaulted");
            JsonObject json = json(dir);
            check(json.get("lootSpawnrate").getAsInt() == 250, "existing value overwritten");
            check("keep me".equals(json.get("userNote").getAsString()), "unknown user key dropped");
            check(json.has("paddockRarity") && json.get("paddockRarity").getAsInt() == 4000, "missing JSON key not added with its default");
            check(json.has("peatGenerationSpeed") && json.has("naturalGoatSpawning"), "missing JSON keys not added");
            String merged = read(dir, "jurassicraft-vehicles.properties");
            check(merged.startsWith(vehicles), "existing properties text changed: " + merged);
            check(merged.contains("\ndestroyBlocks=true"), "missing properties key not appended: " + merged);
            check(merged.contains("# Vehicles remove leaves and vines in their path."), "appended key lacks its comment");

            // A complete file is left byte-for-byte alone.
            String before = read(dir, "jurassicraft-vehicles.properties");
            FabricConfigBackend.loadCommon(dir);
            check(before.equals(read(dir, "jurassicraft-vehicles.properties")), "complete file rewritten");
        } finally { delete(dir); }
    }

    private static void reloadAppliesEditsAndRestoresRemovedKeys() throws IOException {
        Path dir = Files.createTempDirectory("jurassicraft-config");
        try {
            FabricConfigBackend.loadCommon(dir);
            check(JurassicConfig.LOOT_SPAWNRATE.get() == 100, "default loot rate");
            Path vehicles = dir.resolve("jurassicraft-vehicles.properties");
            Files.writeString(vehicles, read(dir, "jurassicraft-vehicles.properties").replace("destroyBlocks=true", "destroyBlocks = false"));
            JsonObject json = json(dir);
            json.addProperty("lootSpawnrate", 40);
            json.addProperty("paddockRarity", 0); // Out of range: the default applies, the file is not "corrected".
            Files.writeString(dir.resolve(FabricConfigBackend.COMMON_FILE), json.toString());
            FabricConfigBackend.loadCommon(dir);
            check(JurassicConfig.LOOT_SPAWNRATE.get() == 40, "edited value not reloaded");
            check(JurassicConfig.RAPTOR_PADDOCK_RARITY.get() == 4000, "out-of-range value not defaulted");
            check(!VehicleConfig.DESTROY_BLOCKS.get(), "edited vehicle value not reloaded");
            check(json(dir).get("paddockRarity").getAsInt() == 0, "user value rewritten");

            // Removing a key during a session puts the default back, in memory and in the file.
            json = json(dir);
            json.remove("lootSpawnrate");
            Files.writeString(dir.resolve(FabricConfigBackend.COMMON_FILE), json.toString());
            FabricConfigBackend.loadCommon(dir);
            check(JurassicConfig.LOOT_SPAWNRATE.get() == 100, "removed key kept its old value");
            check(json(dir).get("lootSpawnrate").getAsInt() == 100, "removed key not restored");
        } finally { delete(dir); }
    }

    private static void legacyTomlNamedPropertiesAreMigrated() throws IOException {
        Path dir = Files.createTempDirectory("jurassicraft-config");
        try {
            Files.writeString(dir.resolve("jurassicraft-vehicles.toml"), "#JurassiCraft vehicle settings\ndestroyBlocks=false\n");
            Files.writeString(dir.resolve("jurassicraft-vehicle-client.toml"), "#JurassiCraft vehicle settings\n");
            FabricConfigBackend.loadCommon(dir);
            FabricConfigBackend.loadClient(dir);
            check(!VehicleConfig.DESTROY_BLOCKS.get(), "legacy value lost");
            check(read(dir, "jurassicraft-vehicles.properties").contains("destroyBlocks=false"), "legacy value not carried over");
            check(read(dir, "jurassicraft-vehicle-client.properties").contains("helicopterZoomout=false"), "missing client key not added");
            check(!Files.exists(dir.resolve("jurassicraft-vehicles.toml")), "legacy file left in place");
            check(Files.exists(dir.resolve("jurassicraft-vehicles.toml.migrated")), "legacy file not kept as .migrated");
        } finally { delete(dir); }
    }

    private static void unreadableJsonIsNeverOverwritten() throws IOException {
        Path dir = Files.createTempDirectory("jurassicraft-config");
        try {
            String broken = "{\"lootSpawnrate\": 7,";
            Files.writeString(dir.resolve(FabricConfigBackend.COMMON_FILE), broken);
            FabricConfigBackend.loadCommon(dir);
            check(broken.equals(read(dir, FabricConfigBackend.COMMON_FILE)), "unreadable file overwritten");
            check(JurassicConfig.LOOT_SPAWNRATE.get() == 100, "unreadable file must leave defaults");
        } finally { delete(dir); }
    }

    private static JsonObject json(Path dir) throws IOException {
        return JsonParser.parseString(read(dir, FabricConfigBackend.COMMON_FILE)).getAsJsonObject();
    }

    private static String read(Path dir, String name) throws IOException {
        return Files.readString(dir.resolve(name), StandardCharsets.UTF_8).replace(System.lineSeparator(), "\n");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void delete(Path dir) throws IOException {
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }
}

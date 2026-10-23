package ports.jurassicraft.fabric;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Properties;
import javax.annotation.Nullable;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ports.jurassicraft.config.ConfigOption;

/**
 * Fabric stores the shared options without a TOML library: common settings in a flat JSON file
 * ({@value #COMMON_FILE}) and vehicle settings in Java properties files ({@code key=value}, named
 * like Forge's TOML files but ending in {@code .properties}). Up to 0.1.0-alpha.1 the properties
 * files were misleadingly named {@code .toml}; such a file is read once, renamed to
 * {@code .toml.migrated} and replaced by the {@code .properties} file.
 *
 * <p>Every load adds options missing from an existing file with their defaults, leaving the rest of
 * the file as it was. Missing, invalid or out-of-range values use their defaults. Files are re-read
 * after {@code /reload}, mirroring Forge's {@code ModConfigEvent.Reloading}.
 */
final class FabricConfigBackend {
    private static final Logger LOGGER = LoggerFactory.getLogger("jurassicraft");
    static final String COMMON_FILE = "jurassicraft-fabric.json";

    private FabricConfigBackend() {}

    static void loadCommon() { loadCommon(configDir()); }
    static void loadClient() { loadClient(configDir()); }

    /** Re-reads every file this side uses; the client file only exists on a client. */
    static void reload() {
        loadCommon();
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) loadClient();
    }

    static void loadCommon(Path dir) {
        List<ConfigOption<?>> options = ConfigOption.all();
        loadJson(dir.resolve(COMMON_FILE), options);
        loadProperties(dir, options, ConfigOption.File.VEHICLES);
    }
    static void loadClient(Path dir) { loadProperties(dir, ConfigOption.all(), ConfigOption.File.VEHICLE_CLIENT); }

    /** Forge's file name with the extension that matches what the file contains. */
    static String propertiesFileName(ConfigOption.File file) {
        return file.fileName().replaceFirst("\\.toml$", ".properties");
    }

    private static Path configDir() { return FabricLoader.getInstance().getConfigDir(); }

    private static void loadJson(Path path, List<ConfigOption<?>> options) {
        JsonObject json = new JsonObject();
        boolean changed = !Files.exists(path);
        if (!changed) {
            try {
                JsonElement root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
                if (!root.isJsonObject()) throw new IllegalStateException("not a JSON object");
                json = root.getAsJsonObject();
            } catch (Exception e) {
                // Never overwrite a file the user can still repair.
                LOGGER.warn("Could not read JurassiCraft configuration {}; using defaults", path, e);
                resetToDefaults(options, ConfigOption.File.COMMON);
                return;
            }
        }
        for (ConfigOption<?> option : options) {
            if (option.file() != ConfigOption.File.COMMON) continue;
            JsonElement element = json.get(option.key());
            if (element == null) {
                if (option.isInteger()) json.addProperty(option.key(), (Integer) option.defaultValue());
                else json.addProperty(option.key(), (Boolean) option.defaultValue());
                changed = true;
                option.set(option.defaultValue());
                continue;
            }
            Object value = element.isJsonPrimitive() ? jsonValue(option, element.getAsJsonPrimitive()) : null;
            apply(option, value, element, path);
        }
        if (changed) write(path, new GsonBuilder().setPrettyPrinting().create().toJson(json) + System.lineSeparator());
    }

    @Nullable private static Object jsonValue(ConfigOption<?> option, JsonPrimitive primitive) {
        if (!option.isInteger()) return primitive.isBoolean() ? primitive.getAsBoolean() : null;
        if (!primitive.isNumber()) return null;
        try {
            return primitive.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException invalid) {
            return null;
        }
    }

    private static void loadProperties(Path dir, List<ConfigOption<?>> options, ConfigOption.File file) {
        Path path = dir.resolve(propertiesFileName(file));
        Path legacy = dir.resolve(file.fileName());
        boolean migrating = !Files.exists(path) && Files.exists(legacy);
        String existing = "";
        Properties values = new Properties();
        try {
            if (Files.exists(path) || migrating) existing = Files.readString(migrating ? legacy : path, StandardCharsets.UTF_8);
            values.load(new StringReader(existing));
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.warn("Could not read JurassiCraft settings from {}; using defaults", migrating ? legacy : path, e);
            resetToDefaults(options, file);
            return;
        }
        StringBuilder missing = new StringBuilder();
        for (ConfigOption<?> option : options) {
            if (option.file() != file) continue;
            String raw = values.getProperty(option.key());
            if (raw == null) {
                missing.append(propertiesEntry(option));
                option.set(option.defaultValue());
                continue;
            }
            apply(option, propertiesValue(option, raw.trim()), raw, path);
        }
        if (missing.length() == 0 && !migrating && !existing.isEmpty()) return;
        String text = existing.isEmpty() ? propertiesHeader(file) : existing;
        if (!text.endsWith("\n")) text += System.lineSeparator();
        if (write(path, text + missing) && migrating) {
            Path moved = legacy.resolveSibling(legacy.getFileName() + ".migrated");
            try {
                Files.move(legacy, moved, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("Moved JurassiCraft settings from {} to {}", legacy, path);
            } catch (IOException e) {
                LOGGER.warn("Could not rename the old JurassiCraft settings file {}", legacy, e);
            }
        }
    }

    @Nullable private static Object propertiesValue(ConfigOption<?> option, String value) {
        if (option.isInteger()) {
            try { return Integer.parseInt(value); } catch (NumberFormatException invalid) { return null; }
        }
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) return Boolean.parseBoolean(value);
        return null;
    }

    private static String propertiesHeader(ConfigOption.File file) {
        String nl = System.lineSeparator();
        return "# JurassiCraft " + (file.client() ? "client vehicle" : "vehicle") + " settings for Fabric." + nl
                + "# Java properties format (key=value); Forge keeps the same keys in config/" + file.fileName() + "." + nl;
    }

    private static String propertiesEntry(ConfigOption<?> option) {
        String nl = System.lineSeparator();
        StringBuilder entry = new StringBuilder(nl);
        for (String line : option.comment().split("\\R")) entry.append("# ").append(line).append(nl);
        if (option.isInteger()) entry.append("# Range: ").append(option.min()).append(" ~ ").append(option.max()).append(nl);
        return entry.append(option.key()).append('=').append(option.defaultValue()).append(nl).toString();
    }

    private static void apply(ConfigOption<?> option, @Nullable Object value, Object raw, Path path) {
        if (value != null && option.set(value)) return;
        LOGGER.warn("Invalid JurassiCraft setting {}={} in {}; using the default {}", option.key(), raw, path, option.defaultValue());
        option.set(option.defaultValue());
    }

    private static void resetToDefaults(List<ConfigOption<?>> options, ConfigOption.File file) {
        for (ConfigOption<?> option : options) if (option.file() == file) option.set(option.defaultValue());
    }

    private static boolean write(Path path, String text) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, text, StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            LOGGER.warn("Could not write JurassiCraft configuration {}", path, e);
            return false;
        }
    }
}

package ports.jurassicraft.fabric;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ports.jurassicraft.config.ConfigOption;

/**
 * Fabric keeps the file formats of its first release: a flat JSON file for common settings and
 * key/value files for vehicles. Invalid or out-of-range values keep their defaults.
 */
final class FabricConfigBackend {
    private static final Logger LOGGER = LoggerFactory.getLogger("jurassicraft");
    private static final String COMMON_FILE = "jurassicraft-fabric.json";

    private FabricConfigBackend() {}

    static void loadCommon() {
        List<ConfigOption<?>> options = ConfigOption.all();
        loadJson(options);
        loadProperties(options, ConfigOption.File.VEHICLES);
    }
    static void loadClient() { loadProperties(ConfigOption.all(), ConfigOption.File.VEHICLE_CLIENT); }

    private static void loadJson(List<ConfigOption<?>> options) {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(COMMON_FILE);
        if (!Files.exists(path)) {
            JsonObject defaults = new JsonObject();
            for (ConfigOption<?> option : options) {
                if (option.file() != ConfigOption.File.COMMON) continue;
                if (option.isInteger()) defaults.addProperty(option.key(), (Integer) option.defaultValue());
                else defaults.addProperty(option.key(), (Boolean) option.defaultValue());
            }
            try {
                Files.createDirectories(path.getParent());
                try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                    new GsonBuilder().setPrettyPrinting().create().toJson(defaults, writer);
                }
            } catch (IOException e) { LOGGER.warn("Could not create JurassiCraft configuration {}", path, e); }
            return;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            for (ConfigOption<?> option : options) {
                if (option.file() != ConfigOption.File.COMMON) continue;
                JsonElement element = json.get(option.key());
                if (element != null && element.isJsonPrimitive()) option.set(value(option, element.getAsJsonPrimitive()));
            }
        } catch (Exception e) {
            LOGGER.warn("Could not read JurassiCraft configuration {}; keeping defaults", path, e);
        }
    }

    private static Object value(ConfigOption<?> option, JsonPrimitive primitive) {
        if (!option.isInteger()) return primitive.isBoolean() ? primitive.getAsBoolean() : null;
        if (!primitive.isNumber()) return null;
        try {
            return primitive.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException invalid) {
            return null;
        }
    }

    private static void loadProperties(List<ConfigOption<?>> options, ConfigOption.File file) {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(file.fileName());
        Properties values = new Properties();
        try {
            if (Files.exists(path)) {
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { values.load(reader); }
            } else {
                Files.createDirectories(path.getParent());
                for (ConfigOption<?> option : options) if (option.file() == file) values.setProperty(option.key(), String.valueOf(option.defaultValue()));
                try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) { values.store(writer, "JurassiCraft vehicle settings"); }
            }
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.warn("Could not read JurassiCraft settings from {}", path, e);
            return;
        }
        for (ConfigOption<?> option : options) {
            if (option.file() != file) continue;
            String value = values.getProperty(option.key());
            if (value == null) continue;
            value = value.trim();
            if (option.isInteger()) {
                try { option.set(Integer.parseInt(value)); } catch (NumberFormatException ignored) { /* Keep the default. */ }
            } else if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
                option.set(Boolean.parseBoolean(value));
            }
        }
    }
}

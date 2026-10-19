package net.mystcraft.minecraft.recipe;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** The original general crafting.linkbook.enabled option (default true). */
public final class ClassicCraftingConfig {
    private ClassicCraftingConfig() {}
    private static final class Loaded { private static final boolean ENABLED = load(); }

    public static boolean linkingBookEnabled() { return Loaded.ENABLED; }

    public static boolean parse(JsonObject json) {
        for (String key : json.keySet()) {
            if (!key.equals("crafting.linkbook.enabled")) throw new IllegalArgumentException("Unknown crafting option: " + key);
        }
        var value = json.get("crafting.linkbook.enabled");
        if (value == null) return true;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("crafting.linkbook.enabled must be boolean");
        }
        return value.getAsBoolean();
    }

    private static boolean load() {
        Path path = Path.of("config", "mystcraft-crafting.json");
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                JsonObject defaults = new JsonObject();
                defaults.addProperty("crafting.linkbook.enabled", true);
                Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(defaults) + "\n", StandardCharsets.UTF_8);
                return true;
            }
            try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                return parse(JsonParser.parseReader(reader).getAsJsonObject());
            }
        } catch (java.io.IOException | RuntimeException error) {
            throw new IllegalStateException("Invalid config/mystcraft-crafting.json: " + error.getMessage(), error);
        }
    }
}

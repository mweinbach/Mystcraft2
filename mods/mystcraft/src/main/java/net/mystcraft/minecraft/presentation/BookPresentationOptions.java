package net.mystcraft.minecraft.presentation;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loader-neutral classic renderlabels setting; server permission and local preference must both allow labels. */
public final class BookPresentationOptions {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("mystcraft-presentation");
    private static boolean localLabels;
    private static boolean serverLabels;
    private BookPresentationOptions() {}
    public static boolean readLabels(Path gameDirectory) {
        Path path = gameDirectory.resolve("config/mystcraft-presentation.json");
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                Files.writeString(path, "{\n  \"renderlabels\": false\n}\n");
                return false;
            }
            if (Files.size(path) > 65536) throw new IllegalArgumentException("Presentation configuration is too large");
            var root = JsonParser.parseString(Files.readString(path));
            if (!root.isJsonObject()) throw new IllegalArgumentException("Presentation configuration must be an object");
            var object = root.getAsJsonObject();
            if (!object.has("renderlabels")) return false;
            var setting = object.get("renderlabels");
            if (!setting.isJsonPrimitive() || !setting.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException("renderlabels must be boolean");
            return setting.getAsBoolean();
        } catch (java.io.IOException | IllegalArgumentException | com.google.gson.JsonParseException failure) {
            LOG.warn("Cannot load classic label settings from {}: {}", path, failure.toString());
            return false;
        }
    }
    public static void loadClient(Path gameDirectory) { localLabels = readLabels(gameDirectory); }
    public static void acceptServer(boolean permitted) { serverLabels = permitted; }
    public static boolean labelsEnabled() { return localLabels && serverLabels; }
}

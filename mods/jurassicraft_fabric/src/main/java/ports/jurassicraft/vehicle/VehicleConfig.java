package ports.jurassicraft.vehicle;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.function.Supplier;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Independent server/common and client settings; defaults match the original port. */
public final class VehicleConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("jurassicraft/vehicles");
    private static boolean destroyBlocks = true, helicopterZoom;
    public static final Supplier<Boolean> DESTROY_BLOCKS = () -> destroyBlocks;
    public static final Supplier<Boolean> HELICOPTER_ZOOM = () -> helicopterZoom;
    private VehicleConfig() {}
    static void init() { destroyBlocks = read("jurassicraft-vehicles.toml", "destroyBlocks", true); }
    public static void initClient() { helicopterZoom = read("jurassicraft-vehicle-client.toml", "helicopterZoomout", false); }
    private static boolean read(String filename, String key, boolean fallback) {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(filename);
        Properties values = new Properties();
        try {
            if (Files.exists(path)) {
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) { values.load(reader); }
            } else {
                Files.createDirectories(path.getParent());
                values.setProperty(key, Boolean.toString(fallback));
                try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) { values.store(writer, "JurassiCraft vehicle settings"); }
            }
            String value = values.getProperty(key, Boolean.toString(fallback)).trim();
            return value.equalsIgnoreCase("true") ? true : value.equalsIgnoreCase("false") ? false : fallback;
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.warn("Could not read vehicle setting {} from {}", key, path, e);
            return fallback;
        }
    }
}

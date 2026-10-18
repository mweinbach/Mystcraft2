package dev.icbm.port.energy;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Battery settings are read on every new item energy lookup; invalid reloads retain active values.
 */
public final class EnergyConfig {
  public record Settings(long batteryCapacity, long batteryInput, long batteryOutput) {
    public Settings {
      if (batteryCapacity < 1
          || batteryCapacity > 1_000_000_000L
          || batteryInput < 1
          || batteryInput > 1_000_000_000L
          || batteryOutput < 1
          || batteryOutput > 1_000_000_000L)
        throw new IllegalArgumentException("Battery values must be integers in 1..1000000000");
    }
  }

  private static volatile Settings active = new Settings(100000, 10000, 10000);

  private EnergyConfig() {}

  public static Settings get() {
    return active;
  }

  public static synchronized void reload() throws IOException {
    Path file = FabricLoader.getInstance().getConfigDir().resolve("icbm_port/energy.json");
    if (!Files.exists(file)) {
      Files.createDirectories(file.getParent());
      Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(active));
    }
    if (Files.size(file) > 16384) throw new IOException("Energy configuration exceeds16KiB");
    try {
      var root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
      for (String key : root.keySet())
        if (!java.util.Set.of("batteryCapacity", "batteryInput", "batteryOutput").contains(key))
          throw new IllegalArgumentException("Unknown energy setting " + key);
      Settings next =
          new Settings(
              integer(root, "batteryCapacity", 100000),
              integer(root, "batteryInput", 10000),
              integer(root, "batteryOutput", 10000));
      active = next;
    } catch (RuntimeException e) {
      throw new IOException("Invalid energy configuration: " + e.getMessage(), e);
    }
  }

  private static long integer(com.google.gson.JsonObject root, String key, long fallback) {
    if (!root.has(key)) return fallback;
    var value = root.get(key);
    if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
      throw new IllegalArgumentException(key + " must be an integer");
    return value.getAsBigDecimal().longValueExact();
  }
}

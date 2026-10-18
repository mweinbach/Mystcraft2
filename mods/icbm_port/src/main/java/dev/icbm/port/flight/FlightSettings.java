package dev.icbm.port.flight;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** Operator controls for short-lived, bounded reentry chunk tickets. */
public final class FlightSettings {
  public static FlightSettings current = new FlightSettings();
  public int maxRange = 10000;
  public boolean temporaryReentryTickets = true;
  public int maxActiveTickets = 4;
  public int pendingLifetimeTicks = 72000;

  public static void load() {
    Path path = FabricLoader.getInstance().getConfigDir().resolve("icbm-flight.json");
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    try {
      if (Files.exists(path)) {
        try (var reader = Files.newBufferedReader(path)) {
          FlightSettings loaded = gson.fromJson(reader, FlightSettings.class);
          if (loaded != null) current = loaded;
        }
      } else {
        Files.createDirectories(path.getParent());
        try (var writer = Files.newBufferedWriter(path)) {
          gson.toJson(current, writer);
        }
      }
      current.maxRange = Math.max(1, Math.min(30000, current.maxRange));
      current.maxActiveTickets = Math.max(0, Math.min(16, current.maxActiveTickets));
      current.pendingLifetimeTicks = Math.max(200, Math.min(72000, current.pendingLifetimeTicks));
    } catch (java.io.IOException | com.google.gson.JsonParseException invalid) {
      org.slf4j.LoggerFactory.getLogger("icbm-flight")
          .warn("Could not read flight configuration; using bounded defaults", invalid);
    }
  }
}

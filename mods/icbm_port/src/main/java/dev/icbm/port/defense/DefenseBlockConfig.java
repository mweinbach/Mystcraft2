package dev.icbm.port.defense;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Spike damage settings. Defaults and the minimum of one point follow upstream ICBM-Classic
 * {@code icbm/classic/config/blocks/ConfigSpikes.java} (MIT), independently re-implemented as a
 * JSON file at {@code config/icbm_port-blocks.json}.
 */
public final class DefenseBlockConfig {
  public float normalSpikeDamage = 1, poisonSpikeDamage = 1, fireSpikeDamage = 1;

  /** Clamp to upstream's {@code RangeDouble(min = 1)} and reject non-finite input. */
  public float damageFor(SpikeBlock.Kind kind) {
    float value =
        switch (kind) {
          case NORMAL -> normalSpikeDamage;
          case POISON -> poisonSpikeDamage;
          case FIRE -> fireSpikeDamage;
        };
    return Float.isFinite(value) ? Math.max(1, Math.min(1_000_000, value)) : 1;
  }

  static DefenseBlockConfig load() {
    Path path = FabricLoader.getInstance().getConfigDir().resolve("icbm_port-blocks.json");
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    try {
      if (Files.exists(path)) {
        DefenseBlockConfig value = gson.fromJson(Files.readString(path), DefenseBlockConfig.class);
        if (value != null) return value;
      } else {
        Files.createDirectories(path.getParent());
        Files.writeString(path, gson.toJson(new DefenseBlockConfig()));
      }
    } catch (Exception error) {
      System.err.println("[icbm_port] Block config could not be loaded: " + error.getMessage());
    }
    return new DefenseBlockConfig();
  }
}

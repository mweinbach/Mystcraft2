package dev.icbm.port.exposure;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

/** Defaults from ConfigMain.protectiveArmor and ConfigRadioactive, independently ported. */
public final class ExposureConfig {
  public boolean requireHelmet = true;
  public float debilitationProtection = .1f,
      chemicalProtection = .5f,
      viralProtection = .8f,
      radiationProtection = 1;
  public int decayDelay = 5, decayRange = 5, witherDuration = 20;
  public float decayChance = .0001f, effectChance = .85f, radiationDamage = 2;
  public boolean randomTickOnly = false;
  public boolean viralSpread = true;
  public int viralSpreadInterval = 100, viralSpreadRange = 2;
  public Map<String, Float> armorRatings = new LinkedHashMap<>();

  public ExposureConfig() {
    for (String prefix : new String[] {"hazmat_", "hazmat_color_"}) {
      armorRatings.put("atomicscience:" + prefix + "mask", .75f);
      armorRatings.put("atomicscience:" + prefix + "body", .15f);
      armorRatings.put("atomicscience:" + prefix + "leggings", .05f);
      armorRatings.put("atomicscience:" + prefix + "boots", .05f);
    }
  }

  static ExposureConfig load() {
    Path path = FabricLoader.getInstance().getConfigDir().resolve("icbm_port-exposure.json");
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    try {
      if (Files.exists(path)) {
        ExposureConfig value = gson.fromJson(Files.readString(path), ExposureConfig.class);
        if (value != null && value.armorRatings != null) return value;
      } else {
        Files.createDirectories(path.getParent());
        Files.writeString(path, gson.toJson(new ExposureConfig()));
      }
    } catch (Exception error) {
      System.err.println("[icbm_port] Exposure config could not be loaded: " + error.getMessage());
    }
    return new ExposureConfig();
  }
}

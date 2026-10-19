package dev.icbm.port.client;

import com.google.gson.JsonObject;
import dev.icbm.port.payload.PayloadType;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.math.Vec3f;

public final class PayloadVisuals implements SimpleSynchronousResourceReloadListener {
  public static final Identifier ID = new Identifier("icbm_port", "payload_visuals");
  private static final Identifier FILE = new Identifier("icbm_port", "payload_visuals.json");
  private static final Map<PayloadType, Profile> PROFILES = new EnumMap<>(PayloadType.class);

  public static Profile get(PayloadType type) {
    Profile profile = PROFILES.get(type);
    if (profile != null) return profile;
    int mix = type.ordinal() * 47 + 30;
    return new Profile(
        new Vec3f((mix & 255) / 255f, ((mix * 3) & 255) / 255f, ((mix * 7) & 255) / 255f),
        Shape.BURST,
        Particle.FLAME,
        Sound.EXPLODE);
  }

  @Override
  public Identifier getFabricId() {
    return ID;
  }

  @Override
  public void reload(ResourceManager manager) {
    PROFILES.clear();
    var resource = manager.getResource(FILE);
    if (resource.isEmpty()) return;
    try (var reader = new InputStreamReader(resource.get().getInputStream(), StandardCharsets.UTF_8)) {
      JsonObject root = JsonHelper.deserialize(reader);
      for (PayloadType type : PayloadType.values()) {
        if (!type.hasContent()) continue;
        JsonObject entry = JsonHelper.getObject(root, type.id());
        var rgb = entry.getAsJsonArray("rgb");
        PROFILES.put(
            type,
            new Profile(
                new Vec3f(
                    rgb.get(0).getAsInt() / 255f,
                    rgb.get(1).getAsInt() / 255f,
                    rgb.get(2).getAsInt() / 255f),
                Shape.valueOf(JsonHelper.getString(entry, "shape").toUpperCase(Locale.ROOT)),
                Particle.valueOf(JsonHelper.getString(entry, "particle").toUpperCase(Locale.ROOT)),
                Sound.valueOf(JsonHelper.getString(entry, "sound").toUpperCase(Locale.ROOT))));
      }
    } catch (IOException | RuntimeException failure) {
      PROFILES.clear();
      throw new IllegalStateException("Unreadable payload visuals", failure);
    }
  }

  public enum Shape {
    RING,
    BURST,
    COLUMN,
    MUSHROOM,
    VORTEX,
    SNOW,
    SPARK,
    CLOUD,
    PORTAL,
    PRISMATIC
  }

  public enum Particle {
    FLAME,
    SMOKE,
    SNOWFLAKE,
    REVERSE_PORTAL,
    ELECTRIC_SPARK,
    SNEEZE,
    END_ROD,
    PORTAL
  }

  public enum Sound {
    EXPLODE,
    SONIC_BOOM,
    ANCHOR,
    TELEPORT,
    TWINKLE,
    FIRECHARGE,
    GLASS
  }

  public record Profile(Vec3f color, Shape shape, Particle particle, Sound sound) {}
}

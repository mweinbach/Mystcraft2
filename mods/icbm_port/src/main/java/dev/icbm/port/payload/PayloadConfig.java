package dev.icbm.port.payload;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Validated JSON configuration. An invalid reload never replaces the working configuration. */
public final class PayloadConfig {
  public record Settings(
      boolean enabled,
      int radius,
      double entityDamage,
      int fragments,
      int durationTicks,
      double energy,
      int blockFuseTicks,
      int cartFuseTicks,
      int grenadeFuseTicks) {
    public Settings {
      range("radius", radius, 0, PayloadType.MAX_EXTREME_RADIUS);
      range("entityDamage", entityDamage, 0, Integer.MAX_VALUE);
      range("fragments", fragments, 0, 128);
      range("durationTicks", durationTicks, 0, 72000);
      range("energy", energy, 0, 20000);
      range("blockFuseTicks", blockFuseTicks, 0, 72000);
      range("cartFuseTicks", cartFuseTicks, 0, 72000);
      range("grenadeFuseTicks", grenadeFuseTicks, 0, 72000);
    }

    private static void range(String name, double value, double min, double max) {
      if (!Double.isFinite(value) || value < min || value > max)
        throw new IllegalArgumentException(name + " must be between " + min + " and " + max);
    }
  }

  /**
   * How EMP blasts drain other mods' Team Reborn Energy blocks and items. The caps bound the work a
   * single blast does: it examines at most the nearest {@code maxBlocks} loaded block entities and
   * the nearest {@code maxEntities} players or inventory entities.
   */
  public record EmpEnergyDrain(boolean enabled, int maxBlocks, int maxEntities) {
    public static final EmpEnergyDrain DEFAULT = new EmpEnergyDrain(true, 1024, 64);

    public EmpEnergyDrain {
      Settings.range("maxBlocks", maxBlocks, 0, 8192);
      Settings.range("maxEntities", maxEntities, 0, 1024);
    }
  }

  /** Top-level key of the EMP energy drain section in {@code payloads.json}. */
  public static final String EMP_ENERGY_DRAIN = "emp_energy_drain";

  /**
   * Top-level schema version. Files without it were written before the antimatter/red matter
   * rework; their untouched old defaults are migrated once and the file is rewritten.
   */
  public static final String CONFIG_VERSION = "config_version";

  public static final int CURRENT_VERSION = 2;

  record Parsed(
      Map<PayloadType, Settings> payloads, EmpEnergyDrain empEnergyDrain, boolean migrated) {
    Parsed(Map<PayloadType, Settings> payloads, EmpEnergyDrain empEnergyDrain) {
      this(payloads, empEnergyDrain, false);
    }
  }

  private static volatile Parsed active = new Parsed(defaults(), EmpEnergyDrain.DEFAULT);
  private static Path path;

  private PayloadConfig() {}

  public static Settings forType(PayloadType type) {
    return active.payloads().get(type);
  }

  public static EmpEnergyDrain empEnergyDrain() {
    return active.empEnergyDrain();
  }

  public static void initialize(Path configDirectory) throws IOException {
    path = configDirectory.resolve("icbm_port").resolve("payloads.json");
    reload();
  }

  public static synchronized void reload() throws IOException {
    if (path == null) throw new IOException("Payload configuration path is not initialized");
    if (!Files.exists(path)) write(new Parsed(defaults(), EmpEnergyDrain.DEFAULT));
    if (Files.size(path) > 1024 * 1024) throw new IOException("payloads.json exceeds 1 MiB");
    String text = Files.readString(path);
    Parsed parsed;
    try {
      parsed = parseAll(text);
    } catch (RuntimeException e) {
      throw new IOException("Invalid payloads.json: " + e.getMessage(), e);
    }
    active = parsed;
    if (parsed.migrated()) write(parsed);
  }

  static String serialize(Parsed parsed) {
    JsonObject root = new JsonObject();
    root.addProperty(CONFIG_VERSION, CURRENT_VERSION);
    parsed.payloads().forEach((type, settings) -> root.add(type.id(), new Gson().toJsonTree(settings)));
    root.add(EMP_ENERGY_DRAIN, new Gson().toJsonTree(parsed.empEnergyDrain()));
    return new GsonBuilder().setPrettyPrinting().create().toJson(root);
  }

  private static void write(Parsed parsed) throws IOException {
    Files.createDirectories(path.getParent());
    Path temporary = Files.createTempFile(path.getParent(), "payloads-", ".json.tmp");
    try {
      Files.writeString(temporary, serialize(parsed));
      Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  static Map<PayloadType, Settings> parse(String text) {
    return parseAll(text).payloads();
  }

  static Parsed parseAll(String text) {
    JsonElement element = JsonParser.parseString(text);
    if (!element.isJsonObject()) throw new IllegalArgumentException("Root must be an object");
    JsonObject root = element.getAsJsonObject();
    Map<PayloadType, Settings> next = defaults();
    EmpEnergyDrain drain = EmpEnergyDrain.DEFAULT;
    int version = root.has(CONFIG_VERSION) ? integer(root, CONFIG_VERSION, 1) : 1;
    if (version < 1 || version > CURRENT_VERSION)
      throw new IllegalArgumentException(CONFIG_VERSION + " must be between 1 and " + CURRENT_VERSION);
    for (String key : root.keySet()) {
      if (key.equals(CONFIG_VERSION)) continue;
      if (key.equals(EMP_ENERGY_DRAIN)) {
        drain = parseDrain(root.getAsJsonObject(key));
        continue;
      }
      PayloadType type =
          Arrays.stream(PayloadType.values())
              .filter(t -> t.id().equals(key))
              .findFirst()
              .orElseThrow(() -> new IllegalArgumentException("Unknown payload " + key));
      JsonObject value = root.getAsJsonObject(key);
      Settings d = next.get(type);
      Set<String> fields =
          Set.of(
              "enabled",
              "radius",
              "entityDamage",
              "fragments",
              "durationTicks",
              "energy",
              "blockFuseTicks",
              "cartFuseTicks",
              "grenadeFuseTicks");
      for (String field : value.keySet())
        if (!fields.contains(field))
          throw new IllegalArgumentException("Unknown setting " + key + "." + field);
      Settings parsed =
          new Settings(
              bool(value, "enabled", d.enabled),
              integer(value, "radius", d.radius),
              number(value, "entityDamage", d.entityDamage),
              integer(value, "fragments", d.fragments),
              integer(value, "durationTicks", d.durationTicks),
              number(value, "energy", d.energy),
              integer(value, "blockFuseTicks", d.blockFuseTicks),
              integer(value, "cartFuseTicks", d.cartFuseTicks),
              integer(value, "grenadeFuseTicks", d.grenadeFuseTicks));
      if (parsed.radius() > type.maxRadius())
        throw new IllegalArgumentException(
            key + ".radius must be between 0 and " + type.maxRadius());
      next.put(type, version < CURRENT_VERSION ? migrate(type, parsed) : parsed);
    }
    return new Parsed(
        Collections.unmodifiableMap(next), drain, version < CURRENT_VERSION);
  }

  /** Version 1 wrote the old defaults explicitly; only exact old defaults move to new ones. */
  private static Settings migrate(PayloadType type, Settings s) {
    int radius = s.radius(), duration = s.durationTicks();
    if (type == PayloadType.ANTIMATTER && radius == PayloadDefaults.LEGACY_ANTIMATTER_RADIUS)
      radius = PayloadDefaults.ANTIMATTER_RADIUS;
    if (type == PayloadType.REDMATTER) {
      if (radius == PayloadDefaults.LEGACY_REDMATTER_RADIUS) radius = PayloadDefaults.REDMATTER_RADIUS;
      if (duration == PayloadDefaults.LEGACY_REDMATTER_DURATION)
        duration = PayloadDefaults.REDMATTER_DURATION;
    }
    return new Settings(
        s.enabled(),
        radius,
        s.entityDamage(),
        s.fragments(),
        duration,
        s.energy(),
        s.blockFuseTicks(),
        s.cartFuseTicks(),
        s.grenadeFuseTicks());
  }

  private static EmpEnergyDrain parseDrain(JsonObject value) {
    EmpEnergyDrain d = EmpEnergyDrain.DEFAULT;
    for (String field : value.keySet())
      if (!Set.of("enabled", "maxBlocks", "maxEntities").contains(field))
        throw new IllegalArgumentException("Unknown setting " + EMP_ENERGY_DRAIN + "." + field);
    return new EmpEnergyDrain(
        bool(value, "enabled", d.enabled()),
        integer(value, "maxBlocks", d.maxBlocks()),
        integer(value, "maxEntities", d.maxEntities()));
  }

  private static double number(JsonObject o, String key, double fallback) {
    if (!o.has(key)) return fallback;
    JsonElement v = o.get(key);
    if (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber())
      throw new IllegalArgumentException(key + " must be numeric");
    return v.getAsDouble();
  }

  private static int integer(JsonObject o, String key, int fallback) {
    double n = number(o, key, fallback);
    if (!Double.isFinite(n) || n != Math.rint(n) || n < Integer.MIN_VALUE || n > Integer.MAX_VALUE)
      throw new IllegalArgumentException(key + " must be an integer");
    return (int) n;
  }

  private static boolean bool(JsonObject o, String key, boolean fallback) {
    if (!o.has(key)) return fallback;
    JsonElement v = o.get(key);
    if (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isBoolean())
      throw new IllegalArgumentException(key + " must be boolean");
    return v.getAsBoolean();
  }

  private static Map<PayloadType, Settings> defaults() {
    Map<PayloadType, Settings> map = new EnumMap<>(PayloadType.class);
    for (PayloadType type : PayloadType.values()) {
      PayloadDefaults d = PayloadDefaults.forType(type);
      map.put(
          type,
          new Settings(
              true,
              d.radius(),
              d.entityDamage(),
              d.fragments(),
              d.durationTicks(),
              d.energy(),
              type.defaultBlockFuseTicks(),
              type.defaultCartFuseTicks(),
              type.defaultGrenadeFuseTicks()));
    }
    return map;
  }
}

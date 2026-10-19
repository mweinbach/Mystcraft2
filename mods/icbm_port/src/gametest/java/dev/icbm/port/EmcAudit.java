package dev.icbm.port;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * EMC coverage check (see docs/emc.md). Every registered item must have a decision in
 * emc/values.json and the shipped data must load. With Item Alchemy installed, its live values
 * must equal the expected ones. ProjectE has no Fabric build, so it is not consulted here.
 */
public final class EmcAudit {
  private static final Logger LOGGER = LoggerFactory.getLogger("icbm_port/emc");

  private EmcAudit() {}

  public static List<String> run(MinecraftServer server) {
    List<String> failures = new ArrayList<>();
    Set<String> registered = new TreeSet<>();
    for (Identifier id : Registry.ITEM.getIds()) {
      if (id.getNamespace().equals(IcbmPort.MOD_ID)) registered.add(id.toString());
    }
    write("emc-registry.txt", String.join("\n", registered) + "\n");
    for (String directory : List.of("pe_custom_conversions", "default_emcs")) {
      Identifier file = IcbmPort.id(directory + "/" + IcbmPort.MOD_ID + ".json");
      if (server.getResourceManager().getResource(file).isEmpty())
        failures.add("data pack file not loaded: " + file);
    }
    String path = System.getProperty("emc.values");
    if (path == null) {
      failures.add("system property emc.values is not set");
      return report(failures);
    }
    JsonObject items;
    try {
      JsonObject values = JsonParser.parseString(Files.readString(Path.of(path))).getAsJsonObject();
      items = values.getAsJsonObject("items");
      values.getAsJsonArray("ignored").forEach(id -> registered.remove(id.getAsString()));
    } catch (IOException | RuntimeException e) {
      failures.add("cannot read " + path + ": " + e);
      return report(failures);
    }
    for (String id : registered)
      if (!items.has(id)) failures.add("registered item has neither an EMC value nor an exclusion: " + id);
    for (String id : items.keySet())
      if (!registered.contains(id)) failures.add("EMC data names an unregistered item: " + id);
    Map<String, Long> itemAlchemy = itemAlchemy();
    if (itemAlchemy != null) {
      Map<String, Long> actual = new TreeMap<>();
      for (String id : registered) {
        Long value = itemAlchemy.containsKey(id) ? ((Number) itemAlchemy.get(id)).longValue() : null;
        actual.put(id, value);
        if (!items.has(id)) continue;
        JsonObject expected = items.getAsJsonObject(id);
        long want = expected.has("itemalchemy") ? expected.get("itemalchemy").getAsLong() : 0L;
        if (value == null || value != want)
          failures.add("itemalchemy value for " + id + " is " + (value == null ? "unset" : value) + ", expected " + want);
      }
      write("emc-actual-itemalchemy.json", new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(actual) + "\n");
      LOGGER.info("EMC audit compared {} items with itemalchemy", actual.size());
    }
    return report(failures);
  }

  /** Item Alchemy stores excluded items as 0 and has no entry for items it never valued. */
  @SuppressWarnings("unchecked")
  private static Map<String, Long> itemAlchemy() {
    try {
      return (Map<String, Long>) Class.forName("net.pitan76.itemalchemy.EMCManager").getMethod("getMap").invoke(null);
    } catch (ReflectiveOperationException e) {
      return null;
    }
  }

  private static List<String> report(List<String> failures) {
    failures.forEach(failure -> LOGGER.error("EMC audit: {}", failure));
    return failures;
  }

  private static void write(String name, String text) {
    try {
      Files.writeString(Path.of(name), text);
    } catch (IOException e) {
      LOGGER.warn("Could not write {}", name, e);
    }
  }

  public static String summary(List<String> failures) {
    return failures.size() + " EMC problem(s), first: " + failures.get(0);
  }
}

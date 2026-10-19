package ports.buildcraft.gametest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;

/**
 * Loader-neutral EMC coverage check, kept identical across ports (see docs/emc.md).
 * Every registered item must have a decision in emc/values.json, and the shipped data must load;
 * entries listed as ignored there (test fixtures) are skipped.
 * When ProjectE or Item Alchemy is installed, their live values must equal the expected ones.
 * The registry and live values are also written to the run directory for scripts/emc.py.
 */
public final class EmcAudit {
    private static final Logger LOGGER = LogUtils.getLogger();

    private EmcAudit() {}

    public static List<String> run(MinecraftServer server, String namespace) {
        List<String> failures = new ArrayList<>();
        Set<String> registered = new TreeSet<>();
        for (ResourceLocation id : Registry.ITEM.keySet()) {
            if (id.getNamespace().equals(namespace)) registered.add(id.toString());
        }
        write("emc-registry.txt", String.join("\n", registered) + "\n");
        for (String directory : List.of("pe_custom_conversions", "default_emcs")) {
            ResourceLocation file = new ResourceLocation(namespace, directory + "/" + namespace + ".json");
            if (server.getResourceManager().getResource(file).isEmpty()) failures.add("data pack file not loaded: " + file);
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
        for (String id : registered) {
            if (!items.has(id)) failures.add("registered item has neither an EMC value nor an exclusion: " + id);
        }
        for (String id : items.keySet()) {
            if (!registered.contains(id)) failures.add("EMC data names an unregistered item: " + id);
        }
        compare(failures, items, registered, "projecte", projectE());
        compare(failures, items, registered, "itemalchemy", itemAlchemy());
        return report(failures);
    }

    private static void compare(List<String> failures, JsonObject items, Set<String> registered, String mod,
                                Function<String, Long> live) {
        if (live == null) return;
        Map<String, Long> actual = new TreeMap<>();
        for (String id : registered) {
            Long value = live.apply(id);
            actual.put(id, value);
            if (!items.has(id)) continue;
            JsonObject expected = items.getAsJsonObject(id);
            long want = expected.has(mod) ? expected.get(mod).getAsLong() : 0L;
            long got = value == null ? -1L : value;
            if (got != want) failures.add(mod + " value for " + id + " is " + (value == null ? "unset" : value) + ", expected " + want);
        }
        write("emc-actual-" + mod + ".json", new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(actual) + "\n");
        LOGGER.info("EMC audit compared {} items with {}", actual.size(), mod);
    }

    private static Function<String, Long> projectE() {
        try {
            Object proxy = Class.forName("moze_intel.projecte.api.ProjectEAPI").getMethod("getEMCProxy").invoke(null);
            Method value = Class.forName("moze_intel.projecte.api.proxy.IEMCProxy").getMethod("getValue", Item.class);
            return id -> {
                try {
                    return (Long) value.invoke(proxy, Registry.ITEM.get(new ResourceLocation(id)));
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            };
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /** Item Alchemy stores excluded items as 0 and has no entry for items it never valued. */
    @SuppressWarnings("unchecked")
    private static Function<String, Long> itemAlchemy() {
        try {
            Map<String, Long> map = (Map<String, Long>) Class.forName("net.pitan76.itemalchemy.EMCManager").getMethod("getMap").invoke(null);
            return id -> map.containsKey(id) ? ((Number) map.get(id)).longValue() : null;
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

/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

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
 * Loader-neutral EMC audit (see docs/emc.md).
 * Mekanism ships values for four namespaces, so every registered item in any of them must have a decision in
 * emc/values.json and both shipped data files must load for each namespace. Entries listed as ignored in values.json
 * (test fixtures) are skipped.
 * When ProjectE (Forge, {@code -PwithProjectE}) or Item Alchemy (Fabric, {@code -PwithItemAlchemy}) is installed,
 * every live value must equal the expected one in values.json, as in the other ports. With Item Alchemy, the vanilla
 * pack overrides listed under itemalchemy_overrides must hold too.
 * The registry and live values are also written to the run directory for scripts/emc.py.
 */
public final class EmcAudit {
    /** The core module and the three separate module jars, which share one policy and one set of values. */
    public static final List<String> NAMESPACES = List.of("mekanism", "mekanismgenerators", "mekanismtools", "mekanismadditions");
    private static final Logger LOGGER = LogUtils.getLogger();

    private EmcAudit() {}

    public static List<String> run(MinecraftServer server) {
        List<String> failures = new ArrayList<>();
        Set<String> registered = new TreeSet<>();
        for (ResourceLocation id : Registry.ITEM.keySet()) {
            if (NAMESPACES.contains(id.getNamespace())) {
                registered.add(id.toString());
            }
        }
        Set<String> present = new TreeSet<>();
        for (String id : registered) {
            present.add(id.substring(0, id.indexOf(':')));
        }
        for (String namespace : NAMESPACES) {
            if (!present.contains(namespace)) {
                failures.add("live registry has no items for " + namespace);
            }
        }
        write("emc-registry.txt", String.join("\n", registered) + "\n");
        for (String namespace : NAMESPACES) {
            for (String directory : List.of("pe_custom_conversions", "default_emcs")) {
                ResourceLocation file = new ResourceLocation(namespace, directory + "/" + namespace + ".json");
                if (server.getResourceManager().getResource(file).isEmpty()) {
                    failures.add("data pack file not loaded: " + file);
                }
            }
        }
        String path = System.getProperty("emc.values");
        if (path == null) {
            failures.add("system property emc.values is not set");
            return report(failures);
        }
        JsonObject items;
        JsonObject overrides;
        try {
            JsonObject values = JsonParser.parseString(Files.readString(Path.of(path))).getAsJsonObject();
            items = values.getAsJsonObject("items");
            overrides = values.has("itemalchemy_overrides") ? values.getAsJsonObject("itemalchemy_overrides") : new JsonObject();
            values.getAsJsonArray("ignored").forEach(id -> registered.remove(id.getAsString()));
        } catch (IOException | RuntimeException e) {
            failures.add("cannot read " + path + ": " + e);
            return report(failures);
        }
        failures.addAll(coverage(registered, items));
        compareLive(failures, items, registered, "projecte", projectE());
        Function<String, Long> alchemy = itemAlchemy();
        compareLive(failures, items, registered, "itemalchemy", alchemy);
        if (alchemy != null) {
            failures.addAll(compareOverrides(overrides, alchemy));
        }
        return report(failures);
    }

    /**
     * The vanilla pack overrides (values.json {@code itemalchemy_overrides}) against live Item Alchemy values. They
     * ship in a default_emcs file Item Alchemy must read after its own minecraft.json, so a wrong value means the
     * file lost that race.
     */
    static List<String> compareOverrides(JsonObject overrides, Function<String, Long> live) {
        List<String> failures = new ArrayList<>();
        for (String id : overrides.keySet()) {
            Long value = live.apply(id);
            long want = overrides.get(id).getAsLong();
            if (value == null || value != want) {
                failures.add("itemalchemy override for " + id + " is " + (value == null ? "unset" : value) + ", expected " + want);
            }
        }
        return failures;
    }

    /**
     * The data-side half of the audit, split out so it can run in a unit test without a server.
     * {@code registered} is the live item set after ignored entries are removed.
     */
    static List<String> coverage(Set<String> registered, JsonObject items) {
        List<String> failures = new ArrayList<>();
        for (String id : registered) {
            if (!items.has(id)) {
                failures.add("registered item has neither an EMC value nor an exclusion: " + id);
            }
        }
        for (String id : items.keySet()) {
            if (!registered.contains(id)) {
                failures.add("EMC data names an unregistered item: " + id);
            }
        }
        return failures;
    }

    private static void compareLive(List<String> failures, JsonObject items, Set<String> registered, String mod,
          Function<String, Long> live) {
        if (live == null) {
            return;
        }
        Map<String, Long> actual = new TreeMap<>();
        for (String id : registered) {
            actual.put(id, live.apply(id));
        }
        failures.addAll(compare(items, actual, mod));
        write("emc-actual-" + mod + ".json", new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(actual) + "\n");
        LOGGER.info("EMC audit compared {} items with {}", actual.size(), mod);
    }

    /**
     * Live values ({@code null} when the mod has none) against the {@code mod} column of values.json, where an
     * excluded item or a missing column means 0. Items without a decision are reported by {@link #coverage}.
     */
    static List<String> compare(JsonObject items, Map<String, Long> actual, String mod) {
        List<String> failures = new ArrayList<>();
        actual.forEach((id, value) -> {
            if (!items.has(id)) {
                return;
            }
            JsonObject expected = items.getAsJsonObject(id);
            long want = expected.has(mod) ? expected.get(mod).getAsLong() : 0L;
            long got = value == null ? -1L : value;
            if (got != want) {
                failures.add(mod + " value for " + id + " is " + (value == null ? "unset" : value) + ", expected " + want);
            }
        });
        return failures;
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

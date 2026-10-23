/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

/**
 * Loader-neutral EMC coverage check (see docs/emc.md).
 * Mekanism ships values for four namespaces, so every registered item in any of them must have a decision in
 * emc/values.json and both shipped data files must load for each namespace. Entries listed as ignored in values.json
 * (test fixtures) are skipped.
 * The registry is also written to the run directory for scripts/emc.py.
 * <p>
 * Unlike the single-namespace copies in the other ports, this audit does not compare live ProjectE or Item Alchemy
 * values. ProjectE's ore blacklist zeros the ores the static table prices and its recipe mappers may raise mapped
 * values, so equality with the static table does not hold for valid data; see parity/15-projecte.md.
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
        try {
            JsonObject values = JsonParser.parseString(Files.readString(Path.of(path))).getAsJsonObject();
            items = values.getAsJsonObject("items");
            values.getAsJsonArray("ignored").forEach(id -> registered.remove(id.getAsString()));
        } catch (IOException | RuntimeException e) {
            failures.add("cannot read " + path + ": " + e);
            return report(failures);
        }
        failures.addAll(coverage(registered, items));
        return report(failures);
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

/*
 * Mekanism 1.19.2 port - in-world tests shared by the Forge and Fabric builds.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EMC audit coverage")
class EmcAuditTest {

    @Test
    @DisplayName("Coverage reports both a missing decision and an unregistered item")
    void coverageReportsMissingAndUnregisteredItems() {
        Set<String> registered = new TreeSet<>(Set.of("mekanism:a", "mekanism:b"));
        JsonObject items = new JsonObject();
        items.add("mekanism:a", new JsonObject());
        items.add("mekanismgenerators:c", new JsonObject());

        Assertions.assertEquals(List.of(
              "registered item has neither an EMC value nor an exclusion: mekanism:b",
              "EMC data names an unregistered item: mekanismgenerators:c"), EmcAudit.coverage(registered, items));
    }

    @Test
    @DisplayName("Shipped values decide every registered item in all four namespaces")
    void shippedValuesCoverRegistry() throws IOException {
        Path emc = emcDirectory();
        JsonObject values = JsonParser.parseString(Files.readString(emc.resolve("values.json"))).getAsJsonObject();
        JsonObject items = values.getAsJsonObject("items");
        Set<String> registered = new TreeSet<>();
        for (String line : Files.readAllLines(emc.resolve("registry.txt"))) {
            if (!line.isBlank()) {
                registered.add(line);
            }
        }
        values.getAsJsonArray("ignored").forEach(id -> registered.remove(id.getAsString()));

        Assertions.assertEquals(List.of(), EmcAudit.coverage(registered, items));
        Set<String> namespaces = new TreeSet<>();
        registered.forEach(id -> namespaces.add(id.substring(0, id.indexOf(':'))));
        Assertions.assertEquals(new TreeSet<>(EmcAudit.NAMESPACES), namespaces,
              "registry.txt must list all four namespaces");
    }

    private static Path emcDirectory() {
        Path directory = Path.of("").toAbsolutePath();
        for (int i = 0; i < 8 && directory != null; i++) {
            Path candidate = directory.resolve("emc/values.json");
            if (Files.isRegularFile(candidate)) {
                return candidate.getParent();
            }
            directory = directory.getParent();
        }
        throw new AssertionError("emc/values.json not found from " + Path.of("").toAbsolutePath());
    }
}

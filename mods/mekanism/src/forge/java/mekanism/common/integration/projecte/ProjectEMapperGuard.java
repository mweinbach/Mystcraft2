package mekanism.common.integration.projecte;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import mekanism.common.Mekanism;
import moze_intel.projecte.api.nss.NormalizedSimpleStack;

/**
 * Refuses a ProjectE mapper conversion that would price an output below the static EMC table.
 * The table is the generated guard, the same values {@code scripts/emc.py} checks.
 */
public final class ProjectEMapperGuard {

    private static final Map<String, Integer> VALUES = load();

    private ProjectEMapperGuard() {
    }

    public static boolean permits(Map<NormalizedSimpleStack, Integer> ingredients, NormalizedSimpleStack output, int count) {
        if (count < 1 || ingredients == null || ingredients.isEmpty()) {
            return false;
        }
        Integer outputValue = valueOf(output);
        if (outputValue == null) {
            return false;
        }
        long cost = 0L;
        for (Map.Entry<NormalizedSimpleStack, Integer> entry : ingredients.entrySet()) {
            Integer amount = entry.getValue();
            Integer value = valueOf(entry.getKey());
            if (amount == null || amount < 1 || value == null) {
                return false;
            }
            try {
                cost = Math.addExact(cost, Math.multiplyExact(amount.longValue(), value.longValue()));
            } catch (ArithmeticException e) {
                return false;
            }
        }
        return cost / count >= outputValue;
    }

    private static Integer valueOf(NormalizedSimpleStack stack) {
        if (stack == null) {
            return null;
        }
        String json = stack.json();
        int nbt = json.indexOf('{');
        if (nbt >= 0) {
            json = json.substring(0, nbt);
        }
        if (json.isEmpty() || json.indexOf('|') >= 0) {
            return null;
        }
        return VALUES.get(json);
    }

    private static Map<String, Integer> load() {
        try (InputStream input = ProjectEMapperGuard.class.getResourceAsStream("/data/mekanism/projecte_mapper_guard.json")) {
            if (input == null) {
                Mekanism.logger.error("ProjectE mapper guard is missing; machine recipes will not become EMC conversions");
                return Map.of();
            }
            try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                JsonObject values = root.getAsJsonObject("values");
                Map<String, Integer> loaded = new HashMap<>();
                if (values != null) {
                    for (Map.Entry<String, JsonElement> entry : values.entrySet()) {
                        JsonElement element = entry.getValue();
                        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                            int value = element.getAsInt();
                            if (value >= 1) {
                                loaded.put(entry.getKey(), value);
                            }
                        }
                    }
                }
                return Collections.unmodifiableMap(loaded);
            }
        } catch (Exception e) {
            Mekanism.logger.error("ProjectE mapper guard could not be read; machine recipes will not become EMC conversions", e);
            return Map.of();
        }
    }
}

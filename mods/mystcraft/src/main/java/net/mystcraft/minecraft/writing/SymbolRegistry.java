package net.mystcraft.minecraft.writing;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** Reference metadata, deliberately separate from executing Age symbol logic or random completion. */
public final class SymbolRegistry {
    public enum Outcome { IMPLEMENTED, KNOWN_UNPORTED, UNKNOWN }
    public record Definition(ResourceLocation id, String legacyId, String category,
                             List<String> parameterGroups, String sourceClass, boolean implemented,
                             String displayName, List<String> poem, int modelValue) {
        public Definition { parameterGroups = List.copyOf(parameterGroups); poem = List.copyOf(poem); }
    }
    private static final Map<ResourceLocation, Definition> DEFINITIONS = load();
    private SymbolRegistry() {}

    public static List<Definition> all() { return List.copyOf(DEFINITIONS.values()); }
    public static Optional<Definition> find(ResourceLocation id) { return Optional.ofNullable(DEFINITIONS.get(id)); }
    public static Optional<Definition> find(String legacyId) { return PageData.identifier(legacyId).flatMap(SymbolRegistry::find); }
    public static Outcome outcome(String legacyId) {
        return find(legacyId).map(d -> d.implemented() ? Outcome.IMPLEMENTED : Outcome.KNOWN_UNPORTED).orElse(Outcome.UNKNOWN);
    }
    private static Map<ResourceLocation, Definition> load() {
        Map<ResourceLocation, Definition> result = new LinkedHashMap<>();
        try (InputStream input = SymbolRegistry.class.getResourceAsStream("/assets/mystcraft/classic_symbols.json")) {
            if (input == null) throw new IllegalStateException("Missing classic symbol reference catalog");
            try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                for (JsonElement element : JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("symbols")) {
                    var data = element.getAsJsonObject();
                    ResourceLocation id = new ResourceLocation(data.get("id").getAsString());
                    List<String> parameters = new ArrayList<>();
                    for (JsonElement parameter : data.getAsJsonArray("parameter_groups")) parameters.add(parameter.getAsString());
                    List<String> poem = new ArrayList<>();
                    for (JsonElement word : data.getAsJsonArray("poem")) poem.add(word.getAsString());
                    Definition definition = new Definition(id, data.get("legacy_id").getAsString(),
                            data.get("category").getAsString(), parameters,
                            data.get("source_class").getAsString(), data.get("modern_implemented").getAsBoolean(),
                            data.get("display_name").getAsString(), poem, data.get("model_value").getAsInt());
                    if (result.putIfAbsent(id, definition) != null) throw new IllegalStateException("Duplicate symbol " + id);
                }
            }
        } catch (java.io.IOException error) { throw new IllegalStateException("Cannot load classic symbol reference", error); }
        return Collections.unmodifiableMap(result);
    }
}

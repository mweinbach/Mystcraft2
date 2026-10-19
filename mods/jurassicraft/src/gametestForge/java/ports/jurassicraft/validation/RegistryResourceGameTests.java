package ports.jurassicraft.validation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import ports.jurassicraft.PortMod;

/** Checks real Forge registries after startup, including dynamically registered IDs. */
@GameTestHolder(PortMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RegistryResourceGameTests {
    private RegistryResourceGameTests() {}

    @GameTest(template = "cleaning_station_test", templateNamespace = PortMod.MOD_ID)
    public static void generatedResourcesResolve(GameTestHelper helper) {
        List<String> errors = new ArrayList<>();
        scan(helper, "recipes", false, errors, (id, json) -> walk(json, node -> {
            checkValue(node, "item", ForgeRegistries.ITEMS, id, errors);
            checkValue(node, "result", ForgeRegistries.ITEMS, id, errors);
        }));
        scan(helper, "loot_tables", false, errors, (id, json) -> walk(json, node -> {
            if ("minecraft:item".equals(string(node.get("type"))) || "item".equals(string(node.get("type"))))
                checkValue(node, "name", ForgeRegistries.ITEMS, id, errors);
        }));
        scan(helper, "tags/blocks", true, errors, (id, json) -> checkTag(json, ForgeRegistries.BLOCKS, id, errors));
        scan(helper, "tags/items", true, errors, (id, json) -> checkTag(json, ForgeRegistries.ITEMS, id, errors));
        var configured = helper.getLevel().registryAccess().registryOrThrow(Registry.CONFIGURED_FEATURE_REGISTRY);
        var placed = helper.getLevel().registryAccess().registryOrThrow(Registry.PLACED_FEATURE_REGISTRY);
        scan(helper, "worldgen/configured_feature", false, errors, (id, json) -> {
            checkValue(json, "type", ForgeRegistries.FEATURES, id, errors);
            walk(json, node -> {
                checkValue(node, "Name", ForgeRegistries.BLOCKS, id, errors);
                checkValue(node, "block", ForgeRegistries.BLOCKS, id, errors);
            });
        });
        scan(helper, "worldgen/placed_feature", false, errors, (id, json) -> {
            String value = string(json.get("feature"));
            if (local(value) && !configured.containsKey(new ResourceLocation(value))) errors.add(id + ": unknown configured feature " + value);
        });
        scan(helper, "forge/biome_modifier", false, errors, (id, json) -> {
            JsonElement features = json.get("features");
            if (features == null) return;
            if (features.isJsonArray()) {
                for (JsonElement feature : features.getAsJsonArray()) checkPlaced(feature, id, placed, errors);
            } else checkPlaced(features, id, placed, errors);
        });
        if (!errors.isEmpty()) helper.fail(String.join("\n", errors));
        helper.succeed();
    }

    private interface Reader { void read(ResourceLocation id, JsonObject json); }

    private static void scan(GameTestHelper helper, String path, boolean includeMinecraft, List<String> errors, Reader reader) {
        Map<ResourceLocation, List<Resource>> resources = helper.getLevel().getServer().getResourceManager()
                .listResourceStacks(path, id -> id.getPath().endsWith(".json")
                        && (id.getNamespace().equals(PortMod.MOD_ID) || includeMinecraft && id.getNamespace().equals("minecraft")));
        for (var entry : resources.entrySet()) {
            for (Resource resource : entry.getValue()) {
                try (var input = resource.openAsReader()) {
                    JsonElement json = JsonParser.parseReader(input);
                    if (json.isJsonObject()) reader.read(entry.getKey(), json.getAsJsonObject());
                } catch (IOException | RuntimeException invalid) {
                    errors.add(entry.getKey() + ": " + invalid.getMessage());
                }
            }
        }
    }

    private static void walk(JsonElement value, Consumer<JsonObject> consumer) {
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            consumer.accept(object);
            object.entrySet().forEach(entry -> walk(entry.getValue(), consumer));
        } else if (value.isJsonArray()) {
            for (JsonElement element : value.getAsJsonArray()) walk(element, consumer);
        }
    }

    private static String string(JsonElement element) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
                ? element.getAsString() : null;
    }

    private static boolean local(String value) { return value != null && value.startsWith(PortMod.MOD_ID + ":"); }

    private static void checkValue(JsonObject object, String key, IForgeRegistry<?> registry, ResourceLocation file, List<String> errors) {
        String value = string(object.get(key));
        if (local(value) && !registry.containsKey(new ResourceLocation(value))) errors.add(file + ": unknown " + key + " " + value);
    }

    private static void checkTag(JsonObject object, IForgeRegistry<?> registry, ResourceLocation file, List<String> errors) {
        JsonArray values = object.has("values") && object.get("values").isJsonArray() ? object.getAsJsonArray("values") : new JsonArray();
        for (JsonElement element : values) {
            String value;
            if (element.isJsonObject()) {
                JsonObject entry = element.getAsJsonObject();
                if (entry.has("required") && !entry.get("required").getAsBoolean()) continue;
                value = string(entry.get("id"));
            } else value = string(element);
            if (local(value) && !registry.containsKey(new ResourceLocation(value))) errors.add(file + ": unknown tag value " + value);
        }
    }

    private static void checkPlaced(JsonElement element, ResourceLocation file, Registry<?> registry, List<String> errors) {
        String value = string(element);
        if (local(value) && !registry.containsKey(new ResourceLocation(value))) errors.add(file + ": unknown placed feature " + value);
    }
}

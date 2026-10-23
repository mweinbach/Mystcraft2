package ports.jurassicraft.validation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biomes;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.gametest.TestPlatform;

/** Verify resources were not merely packaged, but loaded into the running world. */
public final class DatapackLoadingGameTests {
    private DatapackLoadingGameTests() {}

    @GameTest(template = "jurassicraft:empty")
    public static void recipesLootAndWorldgenAreLoaded(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        var access = level.registryAccess();
        var errors = new ArrayList<String>();
        check(helper, "recipes", id -> server.getRecipeManager().byKey(id).isPresent(), errors);
        check(helper, "loot_tables", id -> server.getLootTables().getIds().contains(id), errors);
        checkRegistry(helper, "worldgen/configured_feature", access.registryOrThrow(Registry.CONFIGURED_FEATURE_REGISTRY), errors);
        checkRegistry(helper, "worldgen/placed_feature", access.registryOrThrow(Registry.PLACED_FEATURE_REGISTRY), errors);
        checkRegistry(helper, "worldgen/structure", access.registryOrThrow(Registry.STRUCTURE_REGISTRY), errors);
        checkRegistry(helper, "worldgen/structure_set", access.registryOrThrow(Registry.STRUCTURE_SET_REGISTRY), errors);
        TestPlatform.get().loaderDataRegistries(access).forEach((folder, registry) -> checkRegistry(helper, folder, registry, errors));

        var plains = access.registryOrThrow(Registry.BIOME_REGISTRY).getHolderOrThrow(Biomes.PLAINS).value();
        ResourceLocation amber = new ResourceLocation(PortMod.MOD_ID, "amber_ore");
        boolean applied = plains.getGenerationSettings().features().stream().flatMap(set -> set.stream())
                .anyMatch(holder -> holder.unwrapKey().map(key -> key.location().equals(amber)).orElse(false));
        if (!applied) errors.add("the amber ore biome modification did not add its feature to plains");
        if (!errors.isEmpty()) helper.fail(String.join("\n", errors));
        helper.succeed();
    }

    @GameTest(template = "jurassicraft:empty")
    public static void requiredModTagValuesAreRegistered(GameTestHelper helper) {
        validateTags(helper, "tags/items", Registry.ITEM);
        validateTags(helper, "tags/blocks", Registry.BLOCK);
        helper.succeed();
    }

    private static void checkRegistry(GameTestHelper helper, String path, Registry<?> registry, List<String> errors) {
        check(helper, path, registry::containsKey, errors);
    }

    private static boolean ours(ResourceLocation file) {
        return file.getNamespace().equals(PortMod.MOD_ID) && file.getPath().endsWith(".json");
    }

    private static ResourceLocation contentId(ResourceLocation file, String directory) {
        String path = file.getPath();
        return new ResourceLocation(file.getNamespace(), path.substring(directory.length() + 1, path.length() - 5));
    }

    private static void check(GameTestHelper helper, String folder, Predicate<ResourceLocation> loaded, List<String> errors) {
        var resources = helper.getLevel().getServer().getResourceManager().listResources(folder, DatapackLoadingGameTests::ours);
        if (resources.isEmpty()) { errors.add("no JurassiCraft resources loaded from " + folder); return; }
        String prefix = folder + "/";
        for (ResourceLocation resource : resources.keySet()) {
            String path = resource.getPath();
            if (!path.startsWith(prefix)) continue;
            ResourceLocation id = new ResourceLocation(PortMod.MOD_ID, path.substring(prefix.length(), path.length() - 5));
            if (!loaded.test(id)) errors.add("not loaded: " + id + " (" + folder + ")");
        }
    }

    private static <T> void validateTags(GameTestHelper h, String directory, Registry<T> registry) {
        var resources = h.getLevel().getServer().getResourceManager().listResources(directory, DatapackLoadingGameTests::ours);
        resources.forEach((file, resource) -> {
            try (var reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                for (JsonElement value : json.getAsJsonArray("values")) {
                    String text;
                    boolean required = true;
                    if (value.isJsonPrimitive()) text = value.getAsString();
                    else {
                        JsonObject object = value.getAsJsonObject(); text = object.get("id").getAsString();
                        if (object.has("required")) required = object.get("required").getAsBoolean();
                    }
                    if (!required) continue;
                    boolean nested = text.startsWith("#");
                    ResourceLocation id = ResourceLocation.tryParse(nested ? text.substring(1) : text);
                    if (id == null) { h.fail("invalid tag reference " + text + " in " + file); return; }
                    if (!id.getNamespace().equals(PortMod.MOD_ID)) continue;
                    boolean present = nested ? registry.getTag(TagKey.create(registry.key(), id)).isPresent() : registry.containsKey(id);
                    if (!present) h.fail("unregistered tag value " + text + " in " + file);
                }
                var tag = TagKey.create(registry.key(), contentId(file, directory));
                if (registry.getTag(tag).isEmpty()) h.fail("tag did not load: " + tag.location());
            } catch (Exception exception) { h.fail("cannot inspect tag " + file + ": " + exception); }
        });
    }
}

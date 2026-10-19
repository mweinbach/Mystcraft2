package ports.jurassicraft.validation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagFile;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import ports.jurassicraft.PortMod;

/** Datagen-only validation using the same serializers, codecs and registries as the game. */
@Mod.EventBusSubscriber(modid = PortMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PackDataValidation implements DataProvider {
    private final ExistingFileHelper existing;
    private final Path resources;
    private final List<String> errors = new ArrayList<>();

    private PackDataValidation(ExistingFileHelper existing, Path resources) {
        this.existing = existing;
        this.resources = resources;
    }

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        if (!event.includeServer()) return;
        // The usual runData working directory is <mod>/run/data. Search parents so
        // this also works when invoked from the mod root or another run folder.
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.isDirectory(current.resolve("src/main/resources/data/jurassicraft")))
            current = current.getParent();
        if (current == null) throw new IllegalStateException("Unable to locate JurassiCraft source resources for validation");
        event.getGenerator().addProvider(event.includeServer(),
                new PackDataValidation(event.getExistingFileHelper(), current.resolve("src/main/resources")));
    }

    @Override public String getName() { return "JurassiCraft packaged data validation"; }

    @Override public void run(CachedOutput output) throws IOException {
        Path data = resources.resolve("data");
        validateRecipes(data.resolve("jurassicraft/recipes"));
        PotionDartValidation.validate(data.resolve("jurassicraft/recipes/dart_tipped_potion.json"), errors);
        RevivalDataValidation.validate(errors);
        validateTags(data);
        validateWorldgen(data.resolve("jurassicraft"));
        StructureDataValidation.validate(data.resolve("jurassicraft"), existing, errors);
        if (!errors.isEmpty()) throw new IOException("JurassiCraft data validation failed:\n" + String.join("\n", errors));
    }

    private List<Path> files(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) throw new IOException("Missing data directory " + directory);
        try (Stream<Path> paths = Files.walk(directory)) {
            return paths.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".json")).sorted().toList();
        }
    }

    private JsonElement read(Path path) {
        try (var reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader);
        } catch (IOException | RuntimeException ex) {
            errors.add(path + ": " + ex.getMessage());
            return null;
        }
    }

    private ResourceLocation id(Path directory, Path path) {
        String name = directory.relativize(path).toString().replace('\\', '/');
        return new ResourceLocation(PortMod.MOD_ID, name.substring(0, name.length() - 5));
    }

    private void validateRecipes(Path directory) throws IOException {
        List<Path> paths = files(directory);
        if (paths.isEmpty()) throw new IOException("No recipes found in " + directory);
        for (Path path : paths) {
            JsonElement value = read(path);
            if (value == null) continue;
            try {
                if (!value.isJsonObject()) throw new IllegalArgumentException("expected JSON object");
                RecipeManager.fromJson(id(directory, path), value.getAsJsonObject());
                walk(value, node -> {
                    JsonElement tag = node.get("tag");
                    if (tag != null && tag.isJsonPrimitive() && tag.getAsJsonPrimitive().isString())
                        checkTag(path, new ResourceLocation(tag.getAsString()), "items");
                });
            } catch (RuntimeException ex) { errors.add(path + ": " + ex.getMessage()); }
        }
    }

    private void validateTags(Path data) throws IOException {
        int checked = 0;
        for (Path path : files(data)) {
            Path relative = data.relativize(path);
            if (relative.getNameCount() < 4 || !relative.getName(1).toString().equals("tags")) continue;
            String kind = relative.getName(2).toString();
            IForgeRegistry<?> registry = kind.equals("blocks") ? ForgeRegistries.BLOCKS
                    : kind.equals("items") ? ForgeRegistries.ITEMS : null;
            if (registry == null) continue;
            checked++;
            JsonElement value = read(path);
            if (value == null) continue;
            TagFile tag = decode(path, TagFile.CODEC, JsonOps.INSTANCE, value);
            if (tag == null) continue;
            for (TagEntry entry : tag.entries()) {
                if (!entry.isRequired()) continue;
                if (entry.isTag()) checkTag(path, entry.getId(), kind);
                else if (!registry.containsKey(entry.getId())) errors.add(path + ": unknown " + kind + " entry " + entry.getId());
            }
        }
        if (checked == 0) throw new IOException("No block or item tags found in " + data);
    }

    private void checkTag(Path path, ResourceLocation id, String kind) {
        try {
            // getResource also checks when ExistingFileHelper's optional validation flag is off.
            existing.getResource(id, PackType.SERVER_DATA, ".json", "tags/" + kind);
        } catch (IOException | java.util.NoSuchElementException missing) {
            errors.add(path + ": missing " + kind + " tag " + id);
        }
    }

    private void validateWorldgen(Path data) throws IOException {
        RegistryAccess.Writable access = RegistryAccess.builtinCopy();
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, access);
        var configured = access.ownedWritableRegistryOrThrow(Registry.CONFIGURED_FEATURE_REGISTRY);
        var placed = access.ownedWritableRegistryOrThrow(Registry.PLACED_FEATURE_REGISTRY);
        var biomes = access.ownedWritableRegistryOrThrow(Registry.BIOME_REGISTRY);
        Path biomeTags = data.resolve("tags/worldgen/biome");
        if (Files.isDirectory(biomeTags)) for (Path path : files(biomeTags)) {
            JsonElement value = read(path);
            if (value == null) continue;
            TagFile tag = decode(path, TagFile.CODEC, JsonOps.INSTANCE, value);
            if (tag == null) continue;
            for (TagEntry entry : tag.entries()) {
                if (!entry.isRequired()) continue;
                if (entry.isTag()) checkTag(path, entry.getId(), "worldgen/biome");
                else if (!biomes.containsKey(entry.getId())) errors.add(path + ": unknown biome " + entry.getId());
            }
        }
        Path configs = data.resolve("worldgen/configured_feature");
        for (Path path : files(configs)) {
            JsonElement value = read(path);
            if (value == null) continue;
            walk(value, node -> {
                checkRegistered(path, node.get("Name"), ForgeRegistries.BLOCKS);
                checkRegistered(path, node.get("block"), ForgeRegistries.BLOCKS);
                JsonElement tag = node.get("tag");
                if (tag != null && tag.isJsonPrimitive() && tag.getAsJsonPrimitive().isString())
                    checkTag(path, new ResourceLocation(tag.getAsString()), "blocks");
            });
            ConfiguredFeature<?, ?> feature = decode(path, ConfiguredFeature.DIRECT_CODEC, ops, value);
            if (feature != null) Registry.register(configured, id(configs, path), feature);
        }
        Path placements = data.resolve("worldgen/placed_feature");
        for (Path path : files(placements)) {
            JsonElement value = read(path);
            if (value == null) continue;
            if (value.isJsonObject()) {
                JsonElement feature = value.getAsJsonObject().get("feature");
                if (feature != null && feature.isJsonPrimitive() && !configured.containsKey(new ResourceLocation(feature.getAsString())))
                    errors.add(path + ": unknown configured feature " + feature.getAsString());
            }
            PlacedFeature feature = decode(path, PlacedFeature.DIRECT_CODEC, ops, value);
            if (feature != null) Registry.register(placed, id(placements, path), feature);
        }
        for (Path path : files(data.resolve("forge/biome_modifier"))) {
            JsonElement value = read(path);
            if (value == null) continue;
            if (value.isJsonObject()) {
                JsonObject object = value.getAsJsonObject();
                JsonElement selectedBiomes = object.get("biomes");
                if (selectedBiomes != null) {
                    JsonArray array = selectedBiomes.isJsonArray() ? selectedBiomes.getAsJsonArray() : new JsonArray();
                    if (!selectedBiomes.isJsonArray()) array.add(selectedBiomes);
                    for (JsonElement entry : array) if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) {
                        String name = entry.getAsString();
                        if (name.startsWith("#")) checkTag(path, new ResourceLocation(name.substring(1)), "worldgen/biome");
                        else if (!biomes.containsKey(new ResourceLocation(name))) errors.add(path + ": unknown biome " + name);
                    }
                }
                JsonElement features = object.get("features");
                if (features != null) {
                    JsonArray array = features.isJsonArray() ? features.getAsJsonArray() : new JsonArray();
                    if (!features.isJsonArray()) array.add(features);
                    for (JsonElement entry : array) {
                        if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()
                                && !placed.containsKey(new ResourceLocation(entry.getAsString())))
                            errors.add(path + ": unknown placed feature " + entry.getAsString());
                    }
                }
            }
            decode(path, BiomeModifier.DIRECT_CODEC, ops, value);
        }
    }

    private void checkRegistered(Path path, JsonElement value, IForgeRegistry<?> registry) {
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            ResourceLocation id = new ResourceLocation(value.getAsString());
            if (!registry.containsKey(id)) errors.add(path + ": unknown block " + id);
        }
    }

    private <T> T decode(Path path, Codec<T> codec, com.mojang.serialization.DynamicOps<JsonElement> ops, JsonElement value) {
        try {
            DataResult<T> result = codec.parse(ops, value);
            if (result.error().isPresent()) errors.add(path + ": " + result.error().get().message());
            return result.result().orElse(null);
        } catch (RuntimeException ex) {
            errors.add(path + ": " + ex.getMessage());
            return null;
        }
    }

    private static void walk(JsonElement value, Consumer<JsonObject> consumer) {
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            consumer.accept(object);
            object.entrySet().forEach(entry -> walk(entry.getValue(), consumer));
        } else if (value.isJsonArray()) {
            value.getAsJsonArray().forEach(element -> walk(element, consumer));
        }
    }
}

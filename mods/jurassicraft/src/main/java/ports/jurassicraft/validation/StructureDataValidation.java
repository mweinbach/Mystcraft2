package ports.jurassicraft.validation;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.registries.ForgeRegistries;
import ports.jurassicraft.PortMod;
import ports.jurassicraft.mural.MuralType;

/** Headless validation of the actual structure codecs and compressed templates. */
final class StructureDataValidation {
    private static final Map<String, String> MARKERS = Map.ofEntries(
            Map.entry("Chest", "minecraft:chests/village/village_weaponsmith"),
            Map.entry("GroundStorage", "jurassicraft:structure/visitor_centre/ground_storage"),
            Map.entry("ControlRoom", "jurassicraft:structure/visitor_centre/control_room"),
            Map.entry("Laboratory", "jurassicraft:structure/visitor_centre/laboratory"),
            Map.entry("Cryonics", "jurassicraft:structure/visitor_centre/cryonics"),
            Map.entry("Infirmary", "jurassicraft:structure/visitor_centre/infirmary"),
            Map.entry("Garage", "jurassicraft:structure/visitor_centre/garage"),
            Map.entry("StaffDorms", "jurassicraft:structure/visitor_centre/staff_dorms"),
            Map.entry("Kitchen", "jurassicraft:structure/visitor_centre/kitchen"),
            Map.entry("DormTower", "jurassicraft:structure/visitor_centre/dorm_tower"),
            Map.entry("DiningHall", "jurassicraft:structure/visitor_centre/dining_hall"));

    private StructureDataValidation() {}

    static void validate(Path data, ExistingFileHelper existing, List<String> errors) throws IOException {
        Path structureDir = data.resolve("worldgen/structure");
        Path setDir = data.resolve("worldgen/structure_set");
        RegistryAccess.Writable access = RegistryAccess.builtinCopy();
        var biomes = access.ownedWritableRegistryOrThrow(Registry.BIOME_REGISTRY);
        var structures = access.ownedWritableRegistryOrThrow(Registry.STRUCTURE_REGISTRY);
        var ops = RegistryOps.create(JsonOps.INSTANCE, access);
        List<Path> structurePaths = files(structureDir, ".json");
        Map<Path, JsonElement> json = new HashMap<>();
        for (Path path : structurePaths) {
            JsonElement value = readJson(path, errors);
            if (value == null) continue;
            json.put(path, value);
            if (!value.isJsonObject()) continue;
            JsonElement selection = value.getAsJsonObject().get("biomes");
            if (selection == null) continue;
            List<JsonElement> entries = new ArrayList<>();
            if (selection.isJsonArray()) selection.getAsJsonArray().forEach(entries::add);
            else entries.add(selection);
            for (JsonElement entry : entries) {
                if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) continue;
                String id = entry.getAsString();
                if (id.startsWith("#")) {
                    ResourceLocation tag = new ResourceLocation(id.substring(1));
                    checkResource(path, tag, "tags/worldgen/biome", existing, errors);
                    // Data-generation registries have no loaded tag contents; resolve the
                    // named holder after checking the real packaged tag exists.
                    biomes.getOrCreateTag(TagKey.create(Registry.BIOME_REGISTRY, tag));
                } else if (!biomes.containsKey(new ResourceLocation(id))) {
                    errors.add(path + ": unknown biome " + id);
                }
            }
        }
        for (Path path : structurePaths) {
            JsonElement value = json.get(path);
            if (value == null) continue;
            Structure structure = decode(path, Structure.DIRECT_CODEC, ops, value, errors);
            if (structure != null) Registry.register(structures, id(structureDir, path), structure);
        }
        for (Path path : files(setDir, ".json")) {
            JsonElement value = readJson(path, errors);
            if (value != null) decode(path, StructureSet.DIRECT_CODEC, ops, value, errors);
        }
        for (Path path : files(data.resolve("structures"), ".nbt")) validateTemplate(path, existing, errors);
    }

    private static List<Path> files(Path directory, String extension) throws IOException {
        if (!Files.isDirectory(directory)) throw new IOException("Missing structure directory " + directory);
        try (var paths = Files.walk(directory)) {
            return paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(extension)).sorted().toList();
        }
    }

    private static ResourceLocation id(Path directory, Path path) {
        String name = directory.relativize(path).toString().replace('\\', '/');
        return new ResourceLocation(PortMod.MOD_ID, name.substring(0, name.length() - 5));
    }

    private static JsonElement readJson(Path path, List<String> errors) {
        try (var reader = Files.newBufferedReader(path)) { return JsonParser.parseReader(reader); }
        catch (IOException | RuntimeException e) { errors.add(path + ": " + e.getMessage()); return null; }
    }

    private static <T> T decode(Path path, Codec<T> codec, RegistryOps<JsonElement> ops, JsonElement json, List<String> errors) {
        try {
            var result = codec.parse(ops, json);
            result.error().ifPresent(error -> errors.add(path + ": " + error.message()));
            return result.result().orElse(null);
        } catch (RuntimeException e) { errors.add(path + ": " + e.getMessage()); return null; }
    }

    private static void checkResource(Path path, ResourceLocation id, String directory,
            ExistingFileHelper existing, List<String> errors) {
        try { existing.getResource(id, PackType.SERVER_DATA, ".json", directory); }
        catch (IOException | java.util.NoSuchElementException missing) { errors.add(path + ": missing " + directory + " " + id); }
    }

    private static void validateTemplate(Path path, ExistingFileHelper existing, List<String> errors) {
        try {
            CompoundTag root = NbtIo.readCompressed(path.toFile());
            ListTag palette = root.getList("palette", Tag.TAG_COMPOUND);
            ListTag blocks = root.getList("blocks", Tag.TAG_COMPOUND);
            if (palette.isEmpty()) { errors.add(path + ": empty or missing palette"); return; }
            List<Block> resolved = new ArrayList<>();
            for (int i = 0; i < palette.size(); i++) {
                CompoundTag state = palette.getCompound(i);
                ResourceLocation name = new ResourceLocation(state.getString("Name"));
                Block block = ForgeRegistries.BLOCKS.containsKey(name) ? ForgeRegistries.BLOCKS.getValue(name) : null;
                resolved.add(block);
                if (block == null) { errors.add(path + ": unknown palette block " + name); continue; }
                CompoundTag properties = state.getCompound("Properties");
                for (String key : properties.getAllKeys()) {
                    var property = block.getStateDefinition().getProperty(key);
                    if (property == null || property.getValue(properties.getString(key)).isEmpty())
                        errors.add(path + ": invalid state property " + name + "[" + key + "=" + properties.getString(key) + "]");
                }
            }
            for (int i = 0; i < blocks.size(); i++) {
                CompoundTag block = blocks.getCompound(i);
                int index = block.getInt("state");
                if (index < 0 || index >= resolved.size()) { errors.add(path + ": invalid block palette index " + index); continue; }
                if (!block.contains("nbt", Tag.TAG_COMPOUND)) continue;
                CompoundTag nbt = block.getCompound("nbt");
                ResourceLocation type = new ResourceLocation(nbt.getString("id"));
                if (!ForgeRegistries.BLOCK_ENTITY_TYPES.containsKey(type)) errors.add(path + ": unknown block entity " + type);
                else if (resolved.get(index) != null && !ForgeRegistries.BLOCK_ENTITY_TYPES.getValue(type).isValid(resolved.get(index).defaultBlockState()))
                    errors.add(path + ": block entity " + type + " does not match " + ForgeRegistries.BLOCKS.getKey(resolved.get(index)));
                if (nbt.contains("LootTable", Tag.TAG_STRING))
                    checkResource(path, new ResourceLocation(nbt.getString("LootTable")), "loot_tables", existing, errors);
                if (type.toString().equals("minecraft:structure_block") && nbt.getString("mode").equals("DATA")) {
                    String marker = nbt.getString("metadata");
                    String loot = MARKERS.get(marker);
                    if (loot == null) errors.add(path + ": unknown structure marker " + marker);
                    else checkResource(path, new ResourceLocation(loot), "loot_tables", existing, errors);
                }
            }
            ListTag entities = root.getList("entities", Tag.TAG_COMPOUND);
            for (int i = 0; i < entities.size(); i++) {
                CompoundTag nbt = entities.getCompound(i).getCompound("nbt");
                ResourceLocation type = new ResourceLocation(nbt.getString("id"));
                if (!ForgeRegistries.ENTITY_TYPES.containsKey(type)) errors.add(path + ": unknown structure entity " + type);
                if (type.toString().equals("minecraft:painting") && !ForgeRegistries.PAINTING_VARIANTS.containsKey(new ResourceLocation(nbt.getString("variant"))))
                    errors.add(path + ": unknown painting variant " + nbt.getString("variant"));
                if (type.toString().equals("jurassicraft:mural") && java.util.Arrays.stream(MuralType.values()).noneMatch(art -> art.id().equals(nbt.getString("Artwork"))))
                    errors.add(path + ": unknown mural artwork " + nbt.getString("Artwork"));
            }
        } catch (IOException | RuntimeException e) { errors.add(path + ": " + e.getMessage()); }
    }
}

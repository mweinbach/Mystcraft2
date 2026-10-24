package ports.tekxitprobe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

/** Server-side assertions shared by the headless GameTest run and the client's integrated server. */
public final class ServerChecks {
    /** Mod ids that must be loaded, in install order. */
    public static final List<String> PORT_MODS = List.of("mystcraft", "jurassicraft", "advancedgenetics", "buildcraft",
            "bigreactors", "mekanism", "mekanismgenerators", "mekanismtools", "mekanismadditions", "icbm_port",
            "gridwright", "fluidphysics");
    /** Registry namespaces the ports own; every one must register items and recipes. */
    /** Blocks that drop through code rather than a loot table: the action figure keeps its figure NBT, and Mekanism's
     *  bounding block is the filler of multiblock machines, whose main block drops (as upstream). */
    private static final List<String> NO_LOOT_TABLE = List.of("jurassicraft:action_figure", "mekanism:bounding_block");
    public static final List<String> NAMESPACES = List.of("mystcraft", "jurassicraft", "advancedgenetics", "buildcraft",
            "bigreactors", "mekanism", "mekanismgenerators", "mekanismtools", "mekanismadditions", "icbm_port",
            "gridwright");
    public static final String WITHER_SKULL = "minecraft:wither_skeleton_skull";
    public static final long WITHER_SKULL_EMC = 46_422L;

    private ServerChecks() {}

    public static boolean ours(ResourceLocation id) {
        return id != null && NAMESPACES.contains(id.getNamespace());
    }

    public static JsonObject run(MinecraftServer server) {
        List<String> failures = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        JsonObject report = new JsonObject();
        report.addProperty("server", server.getClass().getName());
        report.addProperty("modCount", FabricLoader.getInstance().getAllMods().size());

        JsonObject mods = new JsonObject();
        for (String id : List.of("fabricloader", "fabric-api", "minecraft", "itemalchemy", "emi", "sodium", "iris",
                "distanthorizons", "metalrender", "team_reborn_energy", "mixinextras", "fabric-permissions-api-v0",
                "fabric-gametest-api-v1", "porting_lib", "cardinal-components-base", "cloth-config")) {
            mods.addProperty(id, FabricLoader.getInstance().getModContainer(id)
                    .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse(null));
        }
        for (String id : PORT_MODS) {
            Optional<ModContainer> mod = FabricLoader.getInstance().getModContainer(id);
            mods.addProperty(id, mod.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse(null));
            if (mod.isEmpty()) failures.add("port mod not loaded: " + id);
        }
        report.add("mods", mods);

        Map<String, Map<String, Integer>> counts = new TreeMap<>();
        for (String ns : NAMESPACES) counts.put(ns, new LinkedHashMap<>());
        tally(counts, "items", Registry.ITEM.keySet());
        tally(counts, "blocks", Registry.BLOCK.keySet());
        tally(counts, "blockEntities", Registry.BLOCK_ENTITY_TYPE.keySet());
        tally(counts, "entities", Registry.ENTITY_TYPE.keySet());
        tally(counts, "fluids", Registry.FLUID.keySet());
        tally(counts, "menus", Registry.MENU.keySet());
        tally(counts, "recipeSerializers", Registry.RECIPE_SERIALIZER.keySet());
        tally(counts, "sounds", Registry.SOUND_EVENT.keySet());
        dynamic(server, counts, "biomes", Registry.BIOME_REGISTRY);
        dynamic(server, counts, "dimensionTypes", Registry.DIMENSION_TYPE_REGISTRY);
        dynamic(server, counts, "configuredFeatures", Registry.CONFIGURED_FEATURE_REGISTRY);
        dynamic(server, counts, "placedFeatures", Registry.PLACED_FEATURE_REGISTRY);
        dynamic(server, counts, "structures", Registry.STRUCTURE_REGISTRY);
        tally(counts, "levels", server.levelKeys().stream().map(ResourceKey::location).toList());

        List<ResourceLocation> recipeIds = new ArrayList<>();
        List<ResourceLocation> outputs = new ArrayList<>();
        for (Recipe<?> recipe : server.getRecipeManager().getRecipes()) {
            recipeIds.add(recipe.getId());
            try {
                outputs.add(Registry.ITEM.getKey(recipe.getResultItem().getItem()));
            } catch (RuntimeException e) {
                warnings.add("recipe " + recipe.getId() + " result threw " + e);
            }
        }
        report.addProperty("recipesTotal", recipeIds.size());
        tally(counts, "recipes", recipeIds);
        tally(counts, "recipesProducing", outputs);

        List<ResourceLocation> tagsWithOurItems = new ArrayList<>();
        Registry.ITEM.getTagNames().forEach(tag -> {
            for (Holder<Item> holder : Registry.ITEM.getTagOrEmpty(tag)) {
                ResourceLocation item = holder.unwrapKey().map(ResourceKey::location).orElse(null);
                if (ours(item)) {
                    tagsWithOurItems.add(item);
                }
            }
        });
        tally(counts, "itemTagMemberships", tagsWithOurItems);
        tally(counts, "itemTags", Registry.ITEM.getTagNames().map(t -> t.location()).toList());
        tally(counts, "lootTables", server.getLootTables().getIds());
        tally(counts, "advancements", server.getAdvancements().getAllAdvancements().stream().map(a -> a.getId()).toList());

        List<String> missingLoot = new ArrayList<>();
        for (Block block : Registry.BLOCK) {
            ResourceLocation id = Registry.BLOCK.getKey(block);
            ResourceLocation table = block.getLootTable();
            if (ours(id) && !NO_LOOT_TABLE.contains(id.toString()) && !BuiltInLootTables.EMPTY.equals(table)
                    && !server.getLootTables().getIds().contains(table)) {
                missingLoot.add(id.toString());
            }
        }
        if (!missingLoot.isEmpty()) {
            warnings.add(missingLoot.size() + " port block(s) name a loot table that did not load, e.g. "
                    + missingLoot.subList(0, Math.min(8, missingLoot.size())));
        }

        JsonObject namespaces = new JsonObject();
        counts.forEach((ns, map) -> {
            JsonObject entry = new JsonObject();
            map.forEach(entry::addProperty);
            namespaces.add(ns, entry);
            if (map.getOrDefault("items", 0) == 0) failures.add(ns + ": no items registered");
            if (map.getOrDefault("recipes", 0) == 0) failures.add(ns + ": no recipes loaded");
        });
        report.add("namespaces", namespaces);

        report.add("emc", emc(failures, warnings));
        report.add("failures", strings(failures));
        report.add("warnings", strings(warnings));
        return report;
    }

    private static void tally(Map<String, Map<String, Integer>> counts, String what, Iterable<ResourceLocation> ids) {
        for (Map<String, Integer> map : counts.values()) map.putIfAbsent(what, 0);
        for (ResourceLocation id : ids) {
            Map<String, Integer> map = id == null ? null : counts.get(id.getNamespace());
            if (map != null) map.merge(what, 1, Integer::sum);
        }
    }

    private static <T> void dynamic(MinecraftServer server, Map<String, Map<String, Integer>> counts, String what,
                                    ResourceKey<? extends Registry<T>> key) {
        tally(counts, what, server.registryAccess().registry(key).map(r -> List.copyOf(r.keySet())).orElse(List.of()));
    }

    /** Item Alchemy keeps every value in one map keyed by item id; 0 means "no EMC". */
    @SuppressWarnings("unchecked")
    private static JsonObject emc(List<String> failures, List<String> warnings) {
        JsonObject out = new JsonObject();
        if (!FabricLoader.getInstance().isModLoaded("itemalchemy")) {
            out.addProperty("itemalchemy", "absent");
            return out;
        }
        Map<String, Object> map;
        try {
            map = (Map<String, Object>) Class.forName("net.pitan76.itemalchemy.EMCManager").getMethod("getMap").invoke(null);
        } catch (ReflectiveOperationException | ClassCastException e) {
            failures.add("Item Alchemy loaded but EMCManager.getMap() failed: " + e);
            return out;
        }
        Function<String, Long> live = id -> map.get(id) instanceof Number n ? n.longValue() : null;
        out.addProperty("entries", map.size());
        Long skull = live.apply(WITHER_SKULL);
        out.addProperty(WITHER_SKULL, skull);
        if (skull == null || skull != WITHER_SKULL_EMC) {
            failures.add("Item Alchemy " + WITHER_SKULL + " is " + skull + ", expected " + WITHER_SKULL_EMC
                    + " (run scripts/emc.py merge-itemalchemy on the pack config)");
        }
        // Optional: tekxit-probe/emc/<port>.json copies of mods/<port>/emc/values.json.
        Path dir = TekxitProbe.outDir().resolve("emc");
        if (!Files.isDirectory(dir)) {
            warnings.add("no tekxit-probe/emc/*.json expectations; only the wither skull was compared");
            return out;
        }
        JsonObject perFile = new JsonObject();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.json")) {
            for (Path file : files) {
                JsonObject items = JsonParser.parseString(Files.readString(file)).getAsJsonObject().getAsJsonObject("items");
                int compared = 0;
                List<String> wrong = new ArrayList<>();
                for (Map.Entry<String, JsonElement> e : items.entrySet()) {
                    if (!Registry.ITEM.containsKey(new ResourceLocation(e.getKey()))) continue;
                    JsonObject expected = e.getValue().getAsJsonObject();
                    // Same rule as scripts/emc.py merge-itemalchemy: excluded items are written as 0.
                    long want = expected.has("excluded") || !expected.has("itemalchemy") ? 0L
                            : expected.get("itemalchemy").getAsLong();
                    Long got = live.apply(e.getKey());
                    compared++;
                    if (got == null || got != want) wrong.add(e.getKey() + "=" + got + " (want " + want + ")");
                }
                JsonObject result = new JsonObject();
                result.addProperty("compared", compared);
                result.addProperty("mismatches", wrong.size());
                result.add("examples", strings(wrong.subList(0, Math.min(10, wrong.size()))));
                perFile.add(file.getFileName().toString(), result);
                if (!wrong.isEmpty()) {
                    failures.add("Item Alchemy: " + wrong.size() + " of " + compared + " values differ from "
                            + file.getFileName() + ", e.g. " + wrong.subList(0, Math.min(5, wrong.size())));
                }
            }
        } catch (IOException | RuntimeException e) {
            failures.add("cannot read EMC expectations in " + dir + ": " + e);
        }
        out.add("ports", perFile);
        return out;
    }

    public static JsonArray strings(List<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }
}

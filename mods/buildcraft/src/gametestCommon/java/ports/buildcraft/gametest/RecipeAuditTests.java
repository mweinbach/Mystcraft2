package ports.buildcraft.gametest;

import static ports.buildcraft.gametest.BCTests.require;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluids;
import ports.buildcraft.BuildCraft;
import ports.buildcraft.builders.BCBuilders;
import ports.buildcraft.core.BCCore;
import ports.buildcraft.silicon.BCSilicon;
import ports.buildcraft.silicon.plug.FacadeAssemblyRecipe;
import ports.buildcraft.silicon.plug.FacadeSwapRecipe;

/**
 * Survival recipes for the builders machines (BuildCraft 7.1's shapes, adapted to this port's items) and an audit
 * that every registered BuildCraft item and block is craftable or listed in {@code uncraftable.txt}.
 */
public final class RecipeAuditTests {
    /** The allowlist's path, set by both loaders' GameTest runs. */
    static final String UNCRAFTABLE_PROPERTY = "buildcraft.uncraftable";
    private static final Set<String> KINDS = Set.of("creative", "obtained", "block");

    private RecipeAuditTests() {}

    /**
     * BuildCraft 7.1's filler, builder, architect table and blueprint library recipes, the replacer (a BuildCraft 8
     * machine without one) and upstream's commented-out single block schematic recipe each resolve, are the only
     * crafting recipe for their grid, and make one machine (four schematics).
     */
    public static void buildersMachineRecipes(GameTestHelper helper) {
        Item black = Items.BLACK_DYE, yellow = Items.YELLOW_DYE, mark = BCCore.MARKER_VOLUME_ITEM.get(), table = Items.CRAFTING_TABLE;
        Item gold = BCCore.GEAR_GOLD.get(), diamond = BCCore.GEAR_DIAMOND.get(), blueprint = BCBuilders.BLUEPRINT_CLEAN.get();
        Item iron = Items.IRON_INGOT, schematic = BCBuilders.SCHEMATIC_SINGLE_CLEAN.get();
        requireCrafts(helper, "filler", new Item[] {
                black, mark, black,
                yellow, table, yellow,
                gold, Items.CHEST, gold}, BCBuilders.FILLER_ITEM.get(), 1);
        requireCrafts(helper, "builder", new Item[] {
                black, mark, black,
                yellow, table, yellow,
                diamond, Items.CHEST, diamond}, BCBuilders.BUILDER_ITEM.get(), 1);
        requireCrafts(helper, "architect_table", new Item[] {
                black, mark, black,
                yellow, table, yellow,
                diamond, blueprint, diamond}, BCBuilders.ARCHITECT_TABLE_ITEM.get(), 1);
        requireCrafts(helper, "electronic_library", new Item[] {
                iron, BCCore.GEAR_IRON.get(), iron,
                Items.BOOKSHELF, blueprint, Items.BOOKSHELF,
                iron, Items.REDSTONE, iron}, BCBuilders.ELECTRONIC_LIBRARY_ITEM.get(), 1);
        requireCrafts(helper, "replacer", new Item[] {
                black, mark, black,
                schematic, table, schematic,
                gold, blueprint, gold}, BCBuilders.REPLACER_ITEM.get(), 1);
        requireCrafts(helper, "schematic_single", new Item[] {
                null, Items.PAPER, null,
                Items.LAPIS_LAZULI, null, null,
                null, null, Items.PAPER}, schematic, 4);
        helper.succeed();
    }

    /**
     * Every registered buildcraft item is the result of a loaded recipe or listed in {@code uncraftable.txt} with a
     * kind and a reason; every buildcraft block without an item is listed, unless it is a fluid block whose bucket is a
     * buildcraft item; and every listed id exists and is not craftable, so the list cannot go stale.
     */
    public static void everyItemCraftableOrListed(GameTestHelper helper) {
        Map<String, String> listed = readAllowlist(helper);
        Set<Item> craftable = craftableItems(helper);
        List<String> failures = new ArrayList<>();
        Set<String> items = new TreeSet<>();
        for (Map.Entry<net.minecraft.resources.ResourceKey<Item>, Item> entry : Registry.ITEM.entrySet()) {
            ResourceLocation id = entry.getKey().location();
            if (!id.getNamespace().equals(BuildCraft.MOD_ID)) continue;
            items.add(id.getPath());
            String kind = listed.get(id.getPath());
            boolean made = craftable.contains(entry.getValue());
            if (kind == null && !made) failures.add("item " + id + " has no recipe and is not listed in uncraftable.txt");
            if (kind != null && made) failures.add("item " + id + " is craftable; remove it from uncraftable.txt");
            if ("block".equals(kind)) failures.add("item " + id + " is listed as a block with no item");
        }
        Set<String> blocks = new TreeSet<>();
        for (Map.Entry<net.minecraft.resources.ResourceKey<Block>, Block> entry : Registry.BLOCK.entrySet()) {
            ResourceLocation id = entry.getKey().location();
            if (!id.getNamespace().equals(BuildCraft.MOD_ID)) continue;
            blocks.add(id.getPath());
            Block block = entry.getValue();
            if (block.asItem() != Items.AIR) continue;
            Item bucket = block.defaultBlockState().getFluidState().getType().getBucket();
            boolean fluidWithBucket = block.defaultBlockState().getFluidState().getType() != Fluids.EMPTY
                    && Registry.ITEM.getKey(bucket).getNamespace().equals(BuildCraft.MOD_ID);
            String kind = listed.get(id.getPath());
            if (fluidWithBucket) {
                if (kind != null) failures.add("fluid block " + id + " is covered by its bucket; remove it from uncraftable.txt");
            } else if (!"block".equals(kind)) {
                failures.add("block " + id + " has no item and is not listed in uncraftable.txt as a block");
            }
        }
        listed.forEach((path, kind) -> {
            if ("block".equals(kind) ? !blocks.contains(path) : !items.contains(path)) {
                failures.add("uncraftable.txt lists buildcraft:" + path + ", which is not a registered " + ("block".equals(kind) ? "block" : "item"));
            }
        });
        require(helper, failures.isEmpty(), String.join("; ", failures));
        helper.succeed();
    }

    /** Results of every recipe the server loaded whose ingredients each match some item, gametest fixtures aside. */
    private static Set<Item> craftableItems(GameTestHelper helper) {
        Set<Item> craftable = new java.util.HashSet<>();
        List<String> unresolved = new ArrayList<>();
        for (Recipe<?> recipe : helper.getLevel().getRecipeManager().getRecipes()) {
            ResourceLocation id = recipe.getId();
            if (id.getNamespace().equals(BuildCraft.MOD_ID) && id.getPath().startsWith("gametest_")) continue;
            boolean resolves = true;
            for (Ingredient ingredient : recipe.getIngredients()) {
                // A tag-backed ingredient is not isEmpty() even when its tags hold nothing, so check what it matches.
                if (ingredient != Ingredient.EMPTY && ingredient.getItems().length == 0) resolves = false;
            }
            if (!resolves) {
                if (id.getNamespace().equals(BuildCraft.MOD_ID)) unresolved.add(id.toString());
                continue;
            }
            // The facade recipes make a facade of whatever block they are given, so they name no result.
            if (recipe instanceof FacadeAssemblyRecipe || recipe instanceof FacadeSwapRecipe) craftable.add(BCSilicon.FACADE_ITEM.get());
            ItemStack result = recipe.getResultItem();
            if (!result.isEmpty()) craftable.add(result.getItem());
        }
        require(helper, unresolved.isEmpty(), "BuildCraft recipes with an ingredient no item matches: " + unresolved);
        return craftable;
    }

    private static Map<String, String> readAllowlist(GameTestHelper helper) {
        String path = System.getProperty(UNCRAFTABLE_PROPERTY);
        require(helper, path != null, "system property " + UNCRAFTABLE_PROPERTY + " is not set");
        List<String> lines;
        try {
            lines = Files.readAllLines(Path.of(path));
        } catch (IOException e) {
            throw new net.minecraft.gametest.framework.GameTestAssertException("cannot read " + path + ": " + e);
        }
        Map<String, String> listed = new TreeMap<>();
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int hash = line.indexOf('#');
            require(helper, hash > 0 && !line.substring(hash + 1).isBlank(), "uncraftable.txt entry without a reason: " + line);
            String[] parts = line.substring(0, hash).strip().split("\\s+");
            require(helper, parts.length == 2 && KINDS.contains(parts[0]), "uncraftable.txt entry must be '<creative|obtained|block> <id>  # reason': " + line);
            require(helper, listed.put(parts[1], parts[0]) == null, "uncraftable.txt lists " + parts[1] + " twice");
        }
        return listed;
    }

    /** {@code grid} (row by row, null for empty) matches only buildcraft:{@code id} and makes {@code count} {@code result}. */
    private static void requireCrafts(GameTestHelper helper, String id, Item[] grid, Item result, int count) {
        CraftingContainer container = new CraftingContainer(new AbstractContainerMenu(null, -1) {
            @Override public boolean stillValid(Player player) { return true; }
            @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
        }, 3, 3);
        for (int slot = 0; slot < 9; slot++) container.setItem(slot, grid[slot] == null ? ItemStack.EMPTY : new ItemStack(grid[slot]));
        var recipes = helper.getLevel().getRecipeManager();
        require(helper, recipes.byKey(BuildCraft.id(id)).isPresent(), "missing recipe buildcraft:" + id);
        List<CraftingRecipe> matches = recipes.getRecipesFor(RecipeType.CRAFTING, container, helper.getLevel());
        require(helper, matches.size() == 1 && matches.get(0).getId().equals(BuildCraft.id(id)),
                "the buildcraft:" + id + " grid must match only that recipe, matches " + matches.stream().map(Recipe::getId).toList());
        ItemStack made = matches.get(0).assemble(container);
        require(helper, made.is(result) && made.getCount() == count,
                "buildcraft:" + id + " must make " + count + " " + Registry.ITEM.getKey(result) + ", made " + made);
    }
}

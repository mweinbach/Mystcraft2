package dev.icbm.port.materialtest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.tag.TagKey;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

/** One sequential test owns both resource reloads; ordinary tests never see foreign providers. */
public final class MaterialReloadGameTests implements FabricGameTest {
  private static void check(TestContext c, boolean value, String message) {
    if (!value) c.throwGameTestException(message);
  }

  private static Item own(String name) {
    return Registry.ITEM.get(new Identifier("icbm_port", name));
  }

  private static Item foreign(String name) {
    return MaterialFixtureMod.ITEMS.get(name);
  }

  private static boolean tagged(Item item, String id) {
    return new ItemStack(item).isIn(TagKey.of(Registry.ITEM_KEY, new Identifier(id)));
  }

  private static CraftingRecipe recipe(TestContext c, String name) {
    return (CraftingRecipe)
        c.getWorld()
            .getRecipeManager()
            .get(new Identifier("icbm_port", name))
            .orElseThrow(() -> new IllegalStateException("Missing recipe " + name));
  }

  private static boolean has(TestContext c, String name) {
    return c.getWorld().getRecipeManager().get(new Identifier("icbm_port", name)).isPresent();
  }

  private static CraftingInventory grid(TestContext c, Item... items) {
    var player = c.createMockPlayer();
    var grid = new CraftingInventory(new CraftingScreenHandler(0, player.getInventory()), 3, 3);
    for (int i = 0; i < items.length; i++)
      if (items[i] != null) grid.setStack(i, new ItemStack(items[i]));
    return grid;
  }

  private static boolean matches(TestContext c, String id, Item... items) {
    return recipe(c, id).matches(grid(c, items), c.getWorld());
  }

  private static Item[] module(Item steel, Item circuit) {
    return new Item[] {
      null, steel, null, steel, circuit, steel, steel, Items.FLINT_AND_STEEL, steel
    };
  }

  private static Item[] nuclear(Item uranium) {
    Item t = own("thermobaric_explosive");
    return new Item[] {t, uranium, t, uranium, uranium, uranium, t, uranium, t};
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 3000)
  public void foreignMaterialsActivateAndDisappearAcrossRealServerReload(TestContext c) {
    var server = c.getWorld().getServer();
    var packs = server.getDataPackManager();
    packs.scanPacks();
    List<String> baseline = new ArrayList<>(packs.getEnabledNames());
    check(
        c,
        !baseline.contains(MaterialFixtureMod.PACK_NAME),
        "NORMAL fixture pack must start disabled");
    check(
        c,
        packs.hasProfile(MaterialFixtureMod.PACK_NAME),
        "Fixture pack profile must be discoverable");
    for (String pack : new String[] {"missing_circuit", "missing_components"}) {
      String name = MaterialFixtureMod.ID + "/" + pack;
      check(
          c,
          !baseline.contains(name) && packs.hasProfile(name),
          "Override fixture pack must be available and initially disabled: " + name);
    }
    assertBaseline(c);
    AtomicInteger phase = new AtomicInteger(0);
    AtomicReference<Throwable> failure = new AtomicReference<>();
    List<String> enabled = new ArrayList<>(baseline);
    enabled.add(MaterialFixtureMod.PACK_NAME);
    reload(c, enabled, phase, failure, 1);
    c.runAtEveryTick(
        () -> {
          if (failure.get() != null)
            c.throwGameTestException("Asynchronous resource reload failed: " + failure.get());
          if (phase.compareAndSet(1, 2)) {
            assertForeignProvider(c);
            reload(c, baseline, phase, failure, 3);
          } else if (phase.compareAndSet(3, 4)) {
            assertBaseline(c);
            reload(c, withPack(baseline, "missing_circuit"), phase, failure, 5);
          } else if (phase.compareAndSet(5, 6)) {
            assertMissingCircuit(c);
            reload(c, withPack(baseline, "missing_components"), phase, failure, 7);
          } else if (phase.compareAndSet(7, 8)) {
            assertMissingComponents(c);
            reload(c, baseline, phase, failure, 9);
          } else if (phase.compareAndSet(9, 10)) {
            assertBaseline(c);
            c.complete();
          }
        });
  }

  private static List<String> withPack(List<String> baseline, String name) {
    List<String> packs = new ArrayList<>(baseline);
    packs.add(MaterialFixtureMod.ID + "/" + name);
    return packs;
  }

  private static void reload(
      TestContext c,
      List<String> packs,
      AtomicInteger phase,
      AtomicReference<Throwable> failure,
      int next) {
    var server = c.getWorld().getServer();
    server
        .reloadResources(packs)
        .whenComplete(
            (unused, error) ->
                server.execute(
                    () -> {
                      failure.set(error);
                      phase.set(next);
                    }));
  }

  private static void assertMissingCircuit(TestContext c) {
    check(
        c,
        !tagged(own("basic_circuit"), "icbm_port:materials/basic_circuits"),
        "Override pack must empty private circuit union");
    check(
        c,
        tagged(own("steel_ingot"), "icbm_port:materials/steel_ingots"),
        "Partial override leaves private steel union populated");
    check(
        c,
        !has(c, "missile_module") && !has(c, "missile_module_vanilla"),
        "Exactly one absent source family must not enable either module recipe");
    check(
        c,
        !has(c, "sam_missile") && has(c, "sam_missile_vanilla"),
        "Absent basic circuit enables only repeater SAM fallback");
    check(
        c,
        matches(
            c,
            "sam_missile_vanilla",
            Items.REPEATER,
            null,
            null,
            own("condensed_explosive"),
            null,
            null,
            own("missile_module")),
        "Repeater SAM fallback retains source vertical recipe");
    check(
        c,
        !has(c, "empty_bomblet") && !has(c, "held_item_missile") && !has(c, "cluster_missile"),
        "Circuit-dependent casing and payload recipes are disabled without basic circuit");
    assertOwnExports(c);
  }

  private static void assertMissingComponents(TestContext c) {
    check(
        c,
        !tagged(own("basic_circuit"), "icbm_port:materials/basic_circuits")
            && !tagged(own("steel_ingot"), "icbm_port:materials/steel_ingots"),
        "Both private component unions must be empty");
    check(
        c,
        !has(c, "missile_module") && has(c, "missile_module_vanilla"),
        "Both absent source families enable only vanilla module fallback");
    check(
        c,
        matches(c, "missile_module_vanilla", module(Items.IRON_INGOT, Items.REDSTONE_BLOCK)),
        "Vanilla module fallback accepts exact iron/redstone-block/flint pattern");
    check(
        c,
        !matches(c, "missile_module_vanilla", module(own("steel_ingot"), Items.REDSTONE_BLOCK)),
        "Vanilla fallback does not silently accept steel for iron");
    check(
        c,
        !has(c, "empty_bomblet") && !has(c, "held_item_missile") && !has(c, "cluster_missile"),
        "Unsupported source recipes stay absent with both components missing");
    check(
        c,
        !has(c, "sam_missile") && has(c, "sam_missile_vanilla"),
        "Missing-components pack retains repeater SAM fallback");
    assertOwnExports(c);
  }

  private static void assertBaseline(TestContext c) {
    check(
        c,
        has(c, "missile_module") && !has(c, "missile_module_vanilla"),
        "Baseline has main module recipe and no stale vanilla fallback");
    check(
        c,
        has(c, "sam_missile") && !has(c, "sam_missile_vanilla"),
        "Baseline has main SAM recipe and no stale repeater fallback");
    check(
        c,
        has(c, "empty_bomblet") && has(c, "held_item_missile") && has(c, "cluster_missile"),
        "Baseline restores component-dependent recipes after overrides");

    check(
        c,
        has(c, "nuclear_explosive") && !has(c, "nuclear_explosive_uranium"),
        "No uranium provider selects vanilla nuclear fallback only");
    check(
        c,
        has(c, "gunpowder_from_components") && !has(c, "gunpowder_from_dusts"),
        "No carbon provider selects coal/charcoal gunpowder fallback only");
    check(
        c,
        !tagged(foreign("steel_ingot"), "icbm_port:materials/steel_ingots"),
        "Disabled provider must leave no old-style steel tag membership");
    check(
        c,
        !tagged(foreign("steel_ingot_nested"), "icbm_port:materials/steel_ingots"),
        "Disabled provider must leave no nested steel tag membership");
    check(
        c,
        !matches(c, "missile_module", module(foreign("steel_ingot"), own("basic_circuit"))),
        "Disabled provider cannot substitute foreign steel, including after reload");
    check(
        c,
        matches(c, "missile_module", module(own("steel_ingot"), own("basic_circuit"))),
        "Own materials remain craftable without external tags");
    check(
        c,
        matches(c, "nuclear_explosive", nuclear(Items.WITHER_SKELETON_SKULL)),
        "Baseline nuclear fallback retains exact skull recipe");
    check(
        c,
        !matches(c, "nuclear_explosive", nuclear(foreign("uranium_ingot"))),
        "Disabled uranium provider cannot match fallback");
    check(
        c,
        matches(
            c,
            "gunpowder_from_components",
            Items.CHARCOAL,
            own("sulfur_dust"),
            own("saltpeter_dust")),
        "Baseline gunpowder supports charcoal and local dusts");
    check(
        c,
        !tagged(foreign("copper_ingot"), "icbm_port:materials/copper_ingots"),
        "Disabled provider removes nested copper membership");
    check(
        c,
        !tagged(foreign("sulfur_dust"), "icbm_port:materials/sulfur_dusts"),
        "Disabled provider removes foreign sulfur membership");
    check(
        c,
        !tagged(foreign("uranium_ingot"), "icbm_port:materials/uranium_ingots"),
        "Disabled provider removes uranium membership");
    check(
        c,
        !matches(
            c,
            "gunpowder_from_components",
            Items.CHARCOAL,
            foreign("sulfur_dust"),
            foreign("saltpeter_dust")),
        "Fallback ingredients must not retain foreign dusts after provider removal");
    assertOwnExports(c);
  }

  private static void assertOwnExports(TestContext c) {
    int expectedCount = 1;
    for (String id : new String[] {"consume_old_style_steel", "consume_nested_steel"}) {
      CraftingRecipe external =
          (CraftingRecipe)
              c.getWorld()
                  .getRecipeManager()
                  .get(new Identifier(MaterialFixtureMod.ID, id))
                  .orElseThrow();
      CraftingInventory input = grid(c, own("steel_ingot"));
      check(
          c,
          external.matches(input, c.getWorld()),
          "External recipe can consume own steel via exported common tag: " + id);
      ItemStack result = external.craft(input);
      check(
          c,
          result.isOf(foreign("unrelated")) && result.getCount() == expectedCount++,
          "External common-tag recipe crafts expected output: " + id);
    }

    for (String id : new String[] {"c:steel_ingots", "c:ingots/steel"})
      check(c, tagged(own("steel_ingot"), id), "Own steel exported to " + id);
    for (String id : new String[] {"c:steel_plates", "c:plates/steel"})
      check(c, tagged(own("steel_plate"), id), "Own steel plate exported to " + id);
    for (String id : new String[] {"c:iron_plates", "c:plates/iron"}) {
      check(c, tagged(own("iron_plate"), id), "Actual iron plate exported to " + id);
      check(c, !tagged(Items.IRON_INGOT, id), "Iron ingots must never masquerade as plates");
    }
  }

  private static void assertForeignProvider(TestContext c) {
    check(
        c,
        !has(c, "nuclear_explosive") && has(c, "nuclear_explosive_uranium"),
        "Live uranium tag selects provider recipe and suppresses vanilla fallback");
    check(
        c,
        !has(c, "gunpowder_from_components") && has(c, "gunpowder_from_dusts"),
        "Live carbon tag selects dust recipe and suppresses coal fallback");
    for (String steel : new String[] {"steel_ingot", "steel_ingot_nested"}) {
      check(
          c,
          tagged(foreign(steel), "icbm_port:materials/steel_ingots"),
          "Private union includes " + steel);
      check(
          c,
          matches(c, "missile_module", module(foreign(steel), foreign("basic_circuit"))),
          "Ore-dictionary module accepts foreign steel/circuit from " + steel);
    }
    check(
        c,
        matches(
            c,
            "basic_circuit",
            Items.REDSTONE,
            Items.CLAY_BALL,
            Items.REDSTONE,
            foreign("copper_ingot"),
            foreign("copper_ingot"),
            foreign("copper_ingot"),
            Items.OAK_PLANKS,
            Items.OAK_PLANKS,
            Items.OAK_PLANKS),
        "Nested c copper tag substitutes in basic circuit");
    check(
        c,
        matches(
            c,
            "launcher_base_tier_1",
            foreign("steel_ingot"),
            foreign("basic_circuit"),
            foreign("steel_ingot"),
            foreign("steel_plate"),
            foreign("steel_plate"),
            foreign("steel_plate")),
        "Forge steel plate provider substitutes in launcher recipe");
    Item wire = foreign("copper_wire");
    Item copper = foreign("copper_ingot");
    check(
        c,
        matches(
            c,
            "advanced_circuit",
            wire,
            copper,
            wire,
            Items.REDSTONE_TORCH,
            foreign("basic_circuit"),
            Items.REDSTONE_TORCH,
            wire,
            copper,
            wire),
        "Source ore-dictionary wire and circuit slots accept foreign counterparts");
    check(
        c,
        matches(
            c,
            "advanced_circuit",
            wire,
            copper,
            wire,
            Items.REDSTONE_TORCH,
            own("basic_circuit"),
            Items.REDSTONE_TORCH,
            wire,
            copper,
            wire),
        "Advanced circuit accepts own basic circuit with foreign copper and wire");
    check(
        c,
        tagged(foreign("copper_wire"), "icbm_port:materials/copper_wires"),
        "Fixture wire is a valid dictionary counterpart");
    check(
        c,
        !matches(
            c,
            "emp_explosive",
            Items.REDSTONE,
            own("battery"),
            Items.REDSTONE,
            wire,
            own("repulsive_explosive"),
            wire,
            wire,
            wire,
            wire),
        "Source direct metadata copper-wire ingredients must reject foreign tagged wire");
    Item exactWire = own("copper_wire");
    check(
        c,
        matches(
            c,
            "emp_explosive",
            Items.REDSTONE,
            own("battery"),
            Items.REDSTONE,
            exactWire,
            own("repulsive_explosive"),
            exactWire,
            exactWire,
            exactWire,
            exactWire),
        "Source direct EMP recipe still accepts exact copper-wire item");
    check(
        c,
        !matches(c, "missile_module", module(foreign("unrelated"), foreign("basic_circuit"))),
        "Untagged fake ingot must not match material union");
    var gasGrid =
        grid(
            c,
            foreign("sulfur_dust"),
            foreign("sulfur_dust"),
            foreign("sulfur_dust"),
            Items.WATER_BUCKET,
            own("shrapnel_explosive"),
            Items.WATER_BUCKET,
            foreign("sulfur_dust"),
            foreign("sulfur_dust"),
            foreign("sulfur_dust"));
    var gas = recipe(c, "debilitation_explosive");
    check(c, gas.matches(gasGrid, c.getWorld()), "Foreign sulfur must substitute in gas recipe");
    check(c, gas.craft(gasGrid).getCount() == 3, "Gas recipe preserves source output count3");
    var remainder = gas.getRemainder(gasGrid);
    check(
        c,
        remainder.get(3).isOf(Items.BUCKET) && remainder.get(5).isOf(Items.BUCKET),
        "Both water bucket containers survive material substitution");
    check(
        c,
        matches(c, "nuclear_explosive_uranium", nuclear(foreign("uranium_ingot"))),
        "Provider nuclear recipe accepts tagged uranium");
    check(
        c,
        !matches(c, "nuclear_explosive_uranium", nuclear(Items.WITHER_SKELETON_SKULL)),
        "Active provider recipe rejects fallback skulls");
    check(
        c,
        matches(
            c,
            "gunpowder_from_dusts",
            foreign("carbon_dust"),
            foreign("sulfur_dust"),
            foreign("saltpeter_dust")),
        "Provider dust gunpowder accepts all tagged foreign dusts");
    check(
        c,
        !matches(
            c,
            "gunpowder_from_dusts",
            Items.CHARCOAL,
            foreign("sulfur_dust"),
            foreign("saltpeter_dust")),
        "Provider dust recipe does not silently retain coal fallback");
    assertOwnExports(c);
  }
}

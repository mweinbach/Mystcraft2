package dev.icbm.port.tekxittest;

import dev.icbm.port.delivery.CarrierData;
import dev.icbm.port.delivery.ClusterPlan;
import dev.icbm.port.energy.EnergyBuffer;
import dev.icbm.port.energy.EnergyContent;
import dev.icbm.port.payload.PayloadType;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeType;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.tag.TagKey;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import team.reborn.energy.api.EnergyStorage;

/**
 * Assertions run against actual pack registrations and transformed recipes, never fake providers.
 */
public final class TekxitCompatibilityGameTests implements FabricGameTest {
  private static void check(TestContext c, boolean value, String message) {
    if (!value) c.throwGameTestException("Tekxit integration: " + message);
  }

  private static void requirePack(TestContext c) {
    for (String id : new String[] {"techreborn", "indrev", "create", "ad_astra", "almostunified"})
      check(
          c, FabricLoader.getInstance().isModLoaded(id), "Required actual pack mod missing: " + id);
  }

  private static Item item(TestContext c, String id) {
    Identifier key = new Identifier(id);
    check(c, Registry.ITEM.containsId(key), "Expected actual registered item missing: " + id);
    return Registry.ITEM.get(key);
  }

  private static Item own(TestContext c, String id) {
    return item(c, "icbm_port:" + id);
  }

  private static CraftingInventory grid(TestContext c, ItemStack... stacks) {
    var player = c.createMockPlayer();
    var inventory =
        new CraftingInventory(new CraftingScreenHandler(0, player.getInventory()), 3, 3);
    for (int i = 0; i < stacks.length; i++) inventory.setStack(i, stacks[i]);
    return inventory;
  }

  private static CraftingInventory gridItems(TestContext c, Item... items) {
    ItemStack[] stacks = new ItemStack[items.length];
    for (int i = 0; i < items.length; i++)
      stacks[i] = items[i] == null ? ItemStack.EMPTY : new ItemStack(items[i]);
    return grid(c, stacks);
  }

  private static CraftingRecipe recipe(TestContext c, String id) {
    var result = c.getWorld().getRecipeManager().get(new Identifier(id));
    check(c, result.isPresent(), "Expected recipe missing after pack transformation: " + id);
    check(c, result.get() instanceof CraftingRecipe, "Expected crafting recipe: " + id);
    return (CraftingRecipe) result.get();
  }

  private static boolean matches(TestContext c, String id, Item... items) {
    return recipe(c, id).matches(gridItems(c, items), c.getWorld());
  }

  private static boolean has(TestContext c, String id) {
    return c.getWorld().getRecipeManager().get(new Identifier(id)).isPresent();
  }

  private static boolean in(Item item, String tag) {
    return new ItemStack(item).isIn(TagKey.of(Registry.ITEM_KEY, new Identifier(tag)));
  }

  private static boolean populated(String tag) {
    return Registry.ITEM
        .getEntryList(TagKey.of(Registry.ITEM_KEY, new Identifier(tag)))
        .map(list -> list.size() > 0)
        .orElse(false);
  }

  private static Item[] module(Item steel, Item circuit) {
    return new Item[] {
      null, steel, null, steel, circuit, steel, steel, Items.FLINT_AND_STEEL, steel
    };
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void actualProviderMaterialsMatchIcbmRecipes(TestContext c) {
    requirePack(c);
    for (String id :
        new String[] {"techreborn:steel_ingot", "indrev:steel_ingot", "ad_astra:steel_ingot"}) {
      Item steel = item(c, id);
      check(c, in(steel, "icbm_port:materials/steel_ingots"), "Private steel union lacks " + id);
      check(
          c,
          matches(c, "icbm_port:missile_module", module(steel, own(c, "basic_circuit"))),
          "Module rejects real provider " + id);
    }
    for (String id :
        new String[] {"techreborn:steel_plate", "indrev:steel_plate", "ad_astra:steel_plate"}) {
      Item plate = item(c, id);
      check(
          c,
          matches(
              c,
              "icbm_port:launcher_base_tier_1",
              item(c, "techreborn:steel_ingot"),
              own(c, "basic_circuit"),
              item(c, "techreborn:steel_ingot"),
              plate,
              plate,
              plate),
          "Launcher rejects real plate provider " + id);
    }
    Item sheet = item(c, "create:iron_sheet");
    check(
        c,
        in(sheet, "icbm_port:materials/iron_plates"),
        "Create iron sheet absent from iron-plate union");
    check(
        c,
        !in(Items.IRON_INGOT, "icbm_port:materials/iron_plates"),
        "Iron ingot incorrectly masquerades as iron plate");
    check(
        c,
        in(item(c, "techreborn:saltpeter_dust"), "icbm_port:materials/saltpeter_dusts"),
        "Actual Tech Reborn saltpeter is not imported");
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void reciprocalActualRecipesConsumeIcbmExports(TestContext c) {
    requirePack(c);
    Item steel = own(c, "steel_ingot"), plate = own(c, "steel_plate");
    var nuggets = gridItems(c, steel);
    var nuggetMatch =
        c.getWorld().getRecipeManager().getFirstMatch(RecipeType.CRAFTING, nuggets, c.getWorld());
    check(
        c,
        nuggetMatch.isPresent(),
        "No surviving pack steel-nugget recipe accepts exported ICBM steel");
    var nuggetRecipe = nuggetMatch.get();
    ItemStack nuggetOutput = nuggetRecipe.craft(nuggets);
    check(
        c,
        !nuggetRecipe.getId().getNamespace().equals("icbm_port"),
        "Reciprocal steel crafting must use another provider's recipe: " + nuggetRecipe.getId());
    boolean commonNugget = in(nuggetOutput.getItem(), "c:steel_nuggets");
    // Mythic Metals publishes a steel-ingot tag but no steel-nugget tag. AU can
    // generalize that recipe's input without replacing its untagged nugget output.
    boolean untaggedMythic = nuggetOutput.isOf(item(c, "mythicmetals:steel_nugget"));
    org.slf4j.LoggerFactory.getLogger("ICBM-Tekxit-Test")
        .info(
            "Reciprocal steel recipe={} output={} count={} commonSteelNugget={}",
            nuggetRecipe.getId(),
            Registry.ITEM.getId(nuggetOutput.getItem()),
            nuggetOutput.getCount(),
            commonNugget);
    check(
        c,
        nuggetOutput.getCount() == 9 && (commonNugget || untaggedMythic),
        "Surviving external recipe must craft nine actual pack steel nuggets (common-tagged or"
            + " untagged Mythic Metals): "
            + nuggetRecipe.getId()
            + " -> "
            + Registry.ITEM.getId(nuggetOutput.getItem()));
    check(
        c,
        matches(
            c,
            "indrev:shaped/tank",
            plate,
            plate,
            plate,
            Items.GLASS,
            Items.BUCKET,
            Items.GLASS,
            plate,
            plate,
            plate),
        "Industrial Revolution tank rejects ICBM plates");
    check(
        c,
        matches(
            c,
            "create:crafting/curiosities/minecart_coupling",
            null,
            null,
            item(c, "create:andesite_alloy"),
            null,
            own(c, "iron_plate"),
            null,
            item(c, "create:andesite_alloy")),
        "Create coupling rejects exported ICBM iron plate");
    check(
        c,
        matches(
            c,
            "ad_astra:recipes/nasa_workbench",
            Items.REDSTONE_TORCH,
            Items.LEVER,
            Items.REDSTONE_TORCH,
            plate,
            Items.CRAFTING_TABLE,
            plate,
            Items.REDSTONE_BLOCK,
            item(c, "ad_astra:steel_block"),
            Items.REDSTONE_BLOCK),
        "Ad Astra platform tag rejects ICBM plate export");
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void actualDustProvidersSelectConditionsAndKeepBucketRemainders(TestContext c) {
    requirePack(c);
    // With the Mekanism port installed, gunpowder_from_dusts is deliberately off (see docs/RECIPES.md): Mekanism fills
    // the dust tags and converts sulfur and gunpowder one-to-one, so neither ICBM gunpowder recipe may load.
    boolean mekanism = FabricLoader.getInstance().isModLoaded("mekanism");
    check(
        c,
        has(c, "icbm_port:gunpowder_from_dusts") != mekanism
            && !has(c, "icbm_port:gunpowder_from_components"),
        mekanism
            ? "With Mekanism, neither ICBM gunpowder recipe may load"
            : "Actual coal/charcoal providers must select only dust gunpowder recipe");
    if (!mekanism)
      for (String sulfur : new String[] {"techreborn:sulfur_dust", "indrev:sulfur_dust"})
        for (String carbon :
            new String[] {"techreborn:coal_dust", "techreborn:charcoal_dust", "indrev:coal_dust"})
          check(
              c,
              matches(
                  c,
                  "icbm_port:gunpowder_from_dusts",
                  item(c, carbon),
                  item(c, sulfur),
                  item(c, "techreborn:saltpeter_dust")),
              "Real dust combination rejected: " + carbon + " / " + sulfur);
    Item sulfur = item(c, "techreborn:sulfur_dust");
    var input =
        gridItems(
            c,
            sulfur,
            sulfur,
            sulfur,
            Items.WATER_BUCKET,
            own(c, "shrapnel_explosive"),
            Items.WATER_BUCKET,
            sulfur,
            sulfur,
            sulfur);
    var gas = recipe(c, "icbm_port:debilitation_explosive");
    check(
        c,
        gas.matches(input, c.getWorld()) && gas.craft(input).getCount() == 3,
        "Real sulfur gas crafting must preserve output count3");
    var remainder = gas.getRemainder(input);
    check(
        c,
        remainder.get(3).isOf(Items.BUCKET) && remainder.get(5).isOf(Items.BUCKET),
        "Real pack transformation loses water bucket remainders");
    boolean uranium =
        populated("icbm_port:materials/uranium_ingots")
            || populated("icbm_port:materials/uranium_235_pellets");
    check(
        c,
        has(c, "icbm_port:nuclear_explosive_uranium") == uranium
            && has(c, "icbm_port:nuclear_explosive") != uranium,
        "Nuclear conditions disagree with actual uranium provider tags");
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void almostUnifiedOutputsAndDirectWireFollowPackPolicy(TestContext c) {
    requirePack(c);
    Item steel = item(c, "techreborn:steel_ingot");
    var input = gridItems(c, steel, steel, null, steel, steel);
    var plateRecipe = recipe(c, "icbm_port:steel_plate");
    check(
        c,
        plateRecipe.matches(input, c.getWorld()),
        "Steel plate recipe no longer matches actual steel");
    ItemStack plate = plateRecipe.craft(input);
    check(
        c,
        plate.isOf(item(c, "techreborn:steel_plate")) && plate.getCount() == 4,
        "Pack Almost Unified priority must redirect ICBM steel plates to Tech Reborn while"
            + " retaining count4");
    var ironRecipe = recipe(c, "icbm_port:iron_plate");
    var ironInput =
        gridItems(c, Items.IRON_INGOT, Items.IRON_INGOT, null, Items.IRON_INGOT, Items.IRON_INGOT);
    check(
        c,
        ironRecipe.matches(ironInput, c.getWorld())
            && ironRecipe.craft(ironInput).isOf(item(c, "create:iron_sheet")),
        "Pack Almost Unified priority must select Create iron sheets");
    var smelting = c.getWorld().getRecipeManager().get(new Identifier("icbm_port:steel_ingot"));
    check(
        c,
        smelting.isPresent() && smelting.get().getOutput().isOf(steel),
        "Almost Unified must redirect clump smelting output to preferred Tech Reborn steel");
    var emp = recipe(c, "icbm_port:emp_explosive");
    var wire = own(c, "copper_wire");
    check(
        c,
        emp.matches(
            gridItems(
                c,
                Items.REDSTONE,
                own(c, "battery"),
                Items.REDSTONE,
                wire,
                own(c, "repulsive_explosive"),
                wire,
                wire,
                wire,
                wire),
            c.getWorld()),
        "Pack transformation breaks exact EMP wire crafting");
    check(
        c,
        emp.getIngredients().stream()
            .anyMatch(i -> i.toJson().toString().contains("c:copper_wires")),
        "Actual Almost Unified should broaden source direct copper wire to configured common wire"
            + " tag");
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void realForeignBatteriesTransferTransactionallyToIcbm(TestContext c) {
    requirePack(c);
    for (String id : new String[] {"techreborn:red_cell_battery", "indrev:battery"}) {
      var inventory = new SimpleInventory(new ItemStack(item(c, id)));
      var context =
          ContainerItemContext.ofSingleSlot(InventoryStorage.of(inventory, null).getSlot(0));
      EnergyStorage storage = context.find(EnergyStorage.ITEM);
      check(c, storage != null, "Actual foreign battery has no standard energy lookup: " + id);
      long before = storage.getAmount();
      try (Transaction tx = Transaction.openOuter()) {
        check(c, storage.insert(256, tx) > 0, "Foreign battery refuses charge: " + id);
      }
      check(
          c,
          context.find(EnergyStorage.ITEM).getAmount() == before,
          "Aborted external battery charge changed stored energy: " + id);
      try (Transaction tx = Transaction.openOuter()) {
        storage.insert(256, tx);
        tx.commit();
      }
      long charged = context.find(EnergyStorage.ITEM).getAmount();
      EnergyBuffer target = new EnergyBuffer(4096, 4096, 4096, () -> {});
      long moved = EnergyContent.chargeFromBattery(inventory, 0, target, 256);
      long after = context.find(EnergyStorage.ITEM).getAmount();
      check(
          c,
          moved > 0 && target.getAmount() == moved && charged - after == moved,
          "Foreign battery transfer must conserve energy: " + id);
    }
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void fullPackRetainsNbtCraftingAndExactCarrierRemainder(TestContext c) {
    requirePack(c);
    var cluster =
        grid(
            c,
            new ItemStack(own(c, "cluster_missile")),
            new ItemStack(own(c, "smoke_bomblet"), 16),
            new ItemStack(own(c, "incendiary_bomblet"), 16));
    var matched =
        c.getWorld().getRecipeManager().getFirstMatch(RecipeType.CRAFTING, cluster, c.getWorld());
    check(
        c,
        matched.isPresent()
            && matched.get().getId().equals(new Identifier("icbm_port:cluster_loading")),
        "Full pack must retain special cluster recipe matching");
    check(
        c,
        ClusterPlan.fromStack(matched.get().craft(cluster))
            .payloads()
            .equals(List.of(PayloadType.SMOKE, PayloadType.INCENDIARY)),
        "Mixed payload NBT must survive pack recipe transformation");
    ItemStack cargo = new ItemStack(item(c, "techreborn:steel_ingot"));
    cargo.getOrCreateNbt().putString("TekxitTestMarker", "preserve");
    ItemStack carrier = new ItemStack(own(c, "cargo_parachute"));
    CarrierData.setCargo(carrier, cargo);
    var input = grid(c, carrier);
    var unload =
        c.getWorld().getRecipeManager().getFirstMatch(RecipeType.CRAFTING, input, c.getWorld());
    check(
        c,
        unload.isPresent()
            && unload.get().getId().equals(new Identifier("icbm_port:carrier_unloading")),
        "Full pack must retain carrier decrafting recipe");
    ItemStack output = unload.get().craft(input);
    check(
        c,
        output.isOf(cargo.getItem())
            && output.hasNbt()
            && "preserve".equals(output.getNbt().getString("TekxitTestMarker")),
        "Foreign cargo NBT must survive carrier decrafting");
    var remainder = unload.get().getRemainder(input);
    check(
        c,
        remainder.get(0).isOf(carrier.getItem())
            && remainder.get(0).getCount() == 1
            && CarrierData.cargo(remainder.get(0)).isEmpty(),
        "Carrier decrafting must return exactly one empty shell");
    c.complete();
  }
}

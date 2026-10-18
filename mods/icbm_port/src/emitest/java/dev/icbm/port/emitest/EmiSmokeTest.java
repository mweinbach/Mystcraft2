package dev.icbm.port.emitest;

import com.google.gson.GsonBuilder;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeManager;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.runtime.EmiReloadManager;
import dev.icbm.port.delivery.*;
import dev.icbm.port.integration.JeiRecipeDisplays;
import dev.icbm.port.payload.PayloadType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.*;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Development-only checks against the real baked client EMI registry; never modifies a world. */
public final class EmiSmokeTest implements ClientModInitializer {
  private static final Logger LOG = LoggerFactory.getLogger("ICBM-EMI-Smoke");
  private static final int INITIAL_DELAY = 100, STABLE_TICKS = 20;
  private final int timeout =
      Math.max(600, Math.min(12000, Integer.getInteger("icbm.emiSmokeTimeoutTicks", 6000)));
  private boolean finished = true;
  private int worldTicks, readyTicks, assertions, runNumber;
  private String trigger = "none";

  @Override
  public void onInitializeClient() {
    ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    ClientCommandRegistrationCallback.EVENT.register(
        (dispatcher, registryAccess) ->
            dispatcher.register(
                ClientCommandManager.literal("icbm_emi_smoke")
                    .executes(
                        context -> {
                          arm("client_command");
                          context
                              .getSource()
                              .sendFeedback(
                                  Text.literal(
                                      "ICBM EMI smoke run "
                                          + runNumber
                                          + " armed: waiting at least 100 world ticks and a stable"
                                          + " baked EMI registry."));
                          return 1;
                        })));
    if (Boolean.getBoolean("icbm.emiSmokeTest")) arm("system_property");
  }

  private void arm(String trigger) {
    this.trigger = trigger;
    runNumber++;
    finished = false;
    worldTicks = 0;
    readyTicks = 0;
    assertions = 0;
    LOG.info(
        "[ICBM_EMI_SMOKE] ARMED run={} trigger={}: title-screen time does not count toward timeout",
        runNumber,
        trigger);
  }

  private void tick(MinecraftClient client) {
    if (finished) return;
    if (client.world == null || client.player == null) {
      worldTicks = 0;
      readyTicks = 0;
      return;
    }
    worldTicks++;
    try {
      EmiRecipeManager manager = EmiApi.getRecipeManager();
      boolean ready =
          EmiReloadManager.isLoaded() && manager != null && !manager.getRecipes().isEmpty();
      readyTicks = ready ? readyTicks + 1 : 0;
      if (worldTicks >= INITIAL_DELAY && readyTicks >= STABLE_TICKS) {
        finished = true;
        Map<String, Object> details = verify(manager);
        finish(client, true, details, null);
      } else if (worldTicks >= timeout) {
        finished = true;
        throw new AssertionError(
            "EMI did not reach a baked, stable recipe registry within "
                + timeout
                + " in-world ticks; status="
                + EmiReloadManager.getStatus());
      }
    } catch (Throwable failure) {
      finished = true;
      finish(
          client,
          false,
          Map.of("worldTicks", worldTicks, "assertionsBeforeFailure", assertions),
          failure);
    }
  }

  private Map<String, Object> verify(EmiRecipeManager manager) {
    List<EmiRecipe> recipes = List.copyOf(manager.getRecipes());
    Map<Identifier, Integer> counts = new HashMap<>();
    for (EmiRecipe recipe : recipes)
      if (recipe.getId() != null) counts.merge(recipe.getId(), 1, Integer::sum);
    int nativeCount = 0;
    for (var entry : counts.entrySet()) {
      Identifier id = entry.getKey();
      if (!id.getNamespace().equals("icbm_port")) continue;
      require(!id.getPath().startsWith("jei/"), "JEI/JEMI duplicate display remains: " + id);
      if (id.getPath().startsWith("emi/")) {
        require(entry.getValue() == 1, "Duplicate native recipe ID: " + id);
        nativeCount++;
      }
    }
    require(nativeCount > 4, "Native ICBM EMI recipe catalog was not registered");

    // Representative real recipe-manager entries: all six load/unload paths for two distinct cargo.
    for (Item cargo : List.of(Items.IRON_PICKAXE, Items.STONE)) {
      List<CraftingRecipe> examples = JeiRecipeDisplays.forCargo(cargo.getDefaultStack());
      require(
          examples.size() == 6,
          "Smoke fixture cargo is excluded by client delivery config: " + cargo);
      for (CraftingRecipe expected : examples) verifyDisplay(manager, expected);
    }
    for (var entry : DeliveryContent.BOMBLETS.entrySet()) {
      EmiRecipe recipe = recipe(manager, "cluster/" + entry.getKey().id());
      require(
          recipe.getInputs().size() == 2, "Cluster loading input count changed: " + recipe.getId());
      require(
          input(recipe, 0).getItemStack().isOf(DeliveryContent.CLUSTER_MISSILE),
          "Cluster casing input missing");
      require(
          input(recipe, 1).getItemStack().isOf(entry.getValue()),
          "Cluster bomblet ingredient mismatched");
      require(
          ClusterPlan.fromStack(output(recipe)).payloads().equals(List.of(entry.getKey())),
          "Cluster output lost manifest: " + recipe.getId());
    }
    EmiRecipe mixed = recipe(manager, "cluster/mixed");
    require(
        ClusterPlan.fromStack(output(mixed))
            .payloads()
            .equals(List.of(PayloadType.CONDENSED, PayloadType.INCENDIARY)),
        "Mixed cluster output lost ordered NBT payloads");

    for (var entry : infoItems().entrySet()) {
      Identifier expected = new Identifier("icbm_port", "emi/info/" + entry.getKey());
      EmiRecipe info = manager.getRecipe(expected);
      require(
          info != null && info.getCategory().equals(VanillaEmiRecipeCategories.INFO),
          "Missing native information page: " + expected);
      long matching =
          recipes.stream()
              .filter(r -> r.getCategory().equals(VanillaEmiRecipeCategories.INFO))
              .filter(
                  r ->
                      r.getInputs().stream()
                          .flatMap(i -> i.getEmiStacks().stream())
                          .anyMatch(s -> s.getItemStack().isOf(entry.getValue())))
              .count();
      require(
          matching == 1,
          "Information page duplicated (including JEI bridge) for "
              + entry.getKey()
              + ": "
              + matching);
    }
    verifyComparisons();
    verifyIndexLookups(manager);
    verifyDefaultPotionDisplays(manager);
    return Map.of(
        "assertions",
        assertions,
        "totalEmiRecipes",
        recipes.size(),
        "nativeIcbmDisplays",
        nativeCount,
        "informationPages",
        4,
        "jeiInstalled",
        FabricLoader.getInstance().isModLoaded("jei"),
        "worldTicks",
        worldTicks,
        "emiVersion",
        FabricLoader.getInstance()
            .getModContainer("emi")
            .orElseThrow()
            .getMetadata()
            .getVersion()
            .getFriendlyString());
  }

  private void verifyDisplay(EmiRecipeManager manager, CraftingRecipe expected) {
    String path = expected.getId().getPath().substring("jei/".length());
    EmiRecipe actual = recipe(manager, path);
    require(
        actual.getInputs().size() == expected.getIngredients().size(),
        "Input count differs: " + actual.getId());
    for (int i = 0; i < actual.getInputs().size(); i++) {
      ItemStack expectedInput = expected.getIngredients().get(i).getMatchingStacks()[0];
      require(
          ItemStack.areEqual(input(actual, i).getItemStack(), expectedInput),
          "Input NBT/count differs: " + actual.getId() + " slot " + i);
      ItemStack remainder =
          expected instanceof JeiRecipeDisplays.DisplayRecipe display && i == 0
              ? display.displayRemainder()
              : ItemStack.EMPTY;
      require(
          ItemStack.areEqual(input(actual, i).getRemainder().getItemStack(), remainder),
          "Empty casing remainder differs: " + actual.getId());
    }
    require(
        ItemStack.areEqual(output(actual), expected.getOutput()),
        "Loaded output NBT/count differs: " + actual.getId());
    if (actual.getId().getPath().contains("/unload/")) {
      EmiStack returnedEmpty = input(actual, 0).getRemainder();
      Identifier loadId =
          new Identifier("icbm_port", actual.getId().getPath().replace("/unload/", "/load/"));
      EmiRecipe loading = manager.getRecipe(loadId);
      require(
          loading != null && !returnedEmpty.isEmpty(),
          "Unloading does not return a reloadable casing");
      indexed(manager.getRecipesByInput(returnedEmpty), loading, "returned empty-casing input");
    }
  }

  private void verifyDefaultPotionDisplays(EmiRecipeManager manager) {
    for (Item potion : List.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION)) {
      var id = net.minecraft.util.registry.Registry.ITEM.getId(potion);
      for (String family : List.of("held", "parachute", "balloon")) {
        EmiRecipe load = recipe(manager, family + "/load/minecraft/" + id.getPath());
        EmiRecipe unload = recipe(manager, family + "/unload/minecraft/" + id.getPath());
        for (EmiStack stack : List.of(input(load, 1), unload.getOutputs().get(0))) {
          require(
              stack.getKey() == potion
                  && stack.getNbt() != null
                  && stack.getNbt().getString("Potion").equals("minecraft:water"),
              "Default potion NBT lost: " + load.getId());
        }
        var holderNbt = load.getOutputs().get(0).getNbt();
        String cargoKey = family.equals("held") ? "HeldItem" : "CarrierCargo";
        require(
            holderNbt != null
                && holderNbt
                    .getCompound(cargoKey)
                    .getCompound("tag")
                    .getString("Potion")
                    .equals("minecraft:water"),
            "Stored default potion NBT lost: " + load.getId());
        indexed(
            manager.getRecipesByOutput(unload.getOutputs().get(0)),
            unload,
            "default potion output");
      }
    }
    int potions = 0;
    for (EmiStack stack : EmiApi.getIndexStacks()) {
      // Custom PotionItem subclasses may implement unrelated drinks without vanilla Potion NBT.
      // ManaPotionItem in Tekxit explicitly returns a plain stack and uses its own mana logic.
      if (stack.getKey() != Items.POTION
          && stack.getKey() != Items.SPLASH_POTION
          && stack.getKey() != Items.LINGERING_POTION) continue;
      potions++;
      var nbt = stack.getNbt();
      Identifier potionId = nbt == null ? null : Identifier.tryParse(nbt.getString("Potion"));
      boolean basePotion =
          potionId != null
              && net.minecraft.util.registry.Registry.POTION.containsId(potionId)
              && net.minecraft.util.registry.Registry.POTION.get(potionId)
                  != net.minecraft.potion.Potions.EMPTY;
      boolean customEffects =
          nbt != null
              && !nbt.getList("CustomPotionEffects", net.minecraft.nbt.NbtElement.COMPOUND_TYPE)
                  .isEmpty();
      require(
          basePotion || customEffects,
          "Global EMI index contains a subtype-less empty potion: " + stack.getId());
    }
    require(potions > 0, "Global EMI potion index is unexpectedly empty");
    LOG.info(
        "[ICBM_EMI_SMOKE] Validated {} global indexed potion variants and all default-potion cargo"
            + " displays",
        potions);
  }

  private void verifyIndexLookups(EmiRecipeManager manager) {
    EmiStack emptyCluster = EmiStack.of(DeliveryContent.CLUSTER_MISSILE);
    for (var entry : DeliveryContent.BOMBLETS.entrySet()) {
      EmiRecipe cluster = recipe(manager, "cluster/" + entry.getKey().id());
      indexed(manager.getRecipesByInput(emptyCluster), cluster, "empty-cluster input");
      indexed(manager.getRecipesByOutput(cluster.getOutputs().get(0)), cluster, "cluster output");
    }
    EmiRecipe mixed = recipe(manager, "cluster/mixed");
    indexed(manager.getRecipesByInput(emptyCluster), mixed, "empty-cluster mixed input");
    indexed(manager.getRecipesByOutput(mixed.getOutputs().get(0)), mixed, "mixed-cluster output");

    // These actual Tekxit items previously mutated their enclosing payload key during comparison.
    for (String pattern : List.of("dragon_pattern", "crusader_cross_pattern")) {
      Identifier itemId = new Identifier("magistuarmory", pattern);
      if (!net.minecraft.util.registry.Registry.ITEM.containsId(itemId)) continue;
      for (String family : List.of("held", "parachute", "balloon")) {
        EmiRecipe load = recipe(manager, family + "/load/magistuarmory/" + pattern);
        EmiRecipe unload = recipe(manager, family + "/unload/magistuarmory/" + pattern);
        EmiStack loaded = load.getOutputs().get(0);
        net.minecraft.nbt.NbtCompound before = loaded.getNbt().copy();
        String identity = JeiRecipeDisplays.subtype((Item) loaded.getKey(), loaded.getNbt());
        for (int repeat = 0; repeat < 32; repeat++) {
          require(
              identity.equals(JeiRecipeDisplays.subtype((Item) loaded.getKey(), loaded.getNbt())),
              "Payload subtype changed while hashing " + load.getId());
          indexed(manager.getRecipesByOutput(loaded), load, "pattern loaded output");
          indexed(manager.getRecipesByInput(loaded), unload, "pattern loaded input");
        }
        require(
            before.equals(loaded.getNbt()),
            "Subtype lookup mutated live payload NBT: " + load.getId());
        indexed(
            manager.getRecipesByInput(input(unload, 0).getRemainder()),
            load,
            "pattern emptied-casing reload input");
        LOG.info("[ICBM_EMI_SMOKE] Indexed pattern regression passed {}", load.getId());
      }
    }
  }

  private void indexed(List<EmiRecipe> indexed, EmiRecipe expected, String direction) {
    require(
        indexed.stream().anyMatch(recipe -> Objects.equals(recipe.getId(), expected.getId())),
        "Recipe present by ID but missing from " + direction + " index: " + expected.getId());
  }

  private void verifyComparisons() {
    for (Item holder :
        List.of(
            DeliveryContent.HELD_ITEM_MISSILE,
            DeliveryContent.CARGO_PARACHUTE,
            DeliveryContent.CARGO_BALLOON)) {
      ItemStack empty = new ItemStack(holder), returnedEmpty = empty.copy();
      ItemStack first = empty.copy(), second = empty.copy();
      if (holder == DeliveryContent.HELD_ITEM_MISSILE) {
        HeldItemMissileItem.setCargo(returnedEmpty, ItemStack.EMPTY);
        HeldItemMissileItem.setCargo(first, new ItemStack(Items.IRON_PICKAXE));
        HeldItemMissileItem.setCargo(second, new ItemStack(Items.STONE));
      } else {
        CarrierData.setCargo(returnedEmpty, ItemStack.EMPTY);
        CarrierData.setCargo(first, new ItemStack(Items.IRON_PICKAXE));
        CarrierData.setCargo(second, new ItemStack(Items.STONE));
      }
      require(
          EmiStack.of(empty).isEqual(EmiStack.of(returnedEmpty)),
          "Empty container normalization failed: " + holder);
      require(
          !EmiStack.of(first).isEqual(EmiStack.of(second)),
          "Cargo identities collapsed: " + holder);
      require(
          !EmiStack.of(first).isEqual(EmiStack.of(empty)),
          "Loaded and empty identities collapsed: " + holder);
      ItemStack mode = first.copy();
      mode.getOrCreateNbt()
          .putString(
              holder == DeliveryContent.HELD_ITEM_MISSILE ? "HeldAction" : "CarrierMode",
              holder == DeliveryContent.HELD_ITEM_MISSILE ? "SECONDARY" : "BLOCK");
      require(
          !EmiStack.of(first).isEqual(EmiStack.of(mode)),
          "Action/deployment modes collapsed: " + holder);
      ItemStack explicitlyDefault = first.copy();
      explicitlyDefault
          .getOrCreateNbt()
          .putString(
              holder == DeliveryContent.HELD_ITEM_MISSILE ? "HeldAction" : "CarrierMode",
              holder == DeliveryContent.HELD_ITEM_MISSILE ? "PRIMARY_FIRST" : "ITEM");
      require(
          EmiStack.of(first).isEqual(EmiStack.of(explicitlyDefault)),
          "Implicit/explicit default mode normalization failed: " + holder);
    }
    ItemStack first = new ItemStack(DeliveryContent.CLUSTER_MISSILE),
        second = first.copy(),
        reversed = first.copy();
    new ClusterPlan(List.of(PayloadType.CONDENSED, PayloadType.INCENDIARY)).writeTo(first);
    new ClusterPlan(List.of(PayloadType.CONDENSED)).writeTo(second);
    new ClusterPlan(List.of(PayloadType.INCENDIARY, PayloadType.CONDENSED)).writeTo(reversed);
    require(!EmiStack.of(first).isEqual(EmiStack.of(second)), "Cluster manifests collapsed");
    require(!EmiStack.of(first).isEqual(EmiStack.of(reversed)), "Cluster loading order collapsed");
    ItemStack explicitEmpty = new ItemStack(DeliveryContent.CLUSTER_MISSILE);
    ClusterPlan.EMPTY.writeTo(explicitEmpty);
    require(
        EmiStack.of(new ItemStack(DeliveryContent.CLUSTER_MISSILE))
            .isEqual(EmiStack.of(explicitEmpty)),
        "Empty cluster normalization failed");
  }

  private static Map<String, Item> infoItems() {
    return Map.of(
        "cluster",
        DeliveryContent.CLUSTER_MISSILE,
        "held_item",
        DeliveryContent.HELD_ITEM_MISSILE,
        "parachute",
        DeliveryContent.CARGO_PARACHUTE,
        "balloon",
        DeliveryContent.CARGO_BALLOON);
  }

  private EmiRecipe recipe(EmiRecipeManager manager, String suffix) {
    Identifier id = new Identifier("icbm_port", "emi/" + suffix);
    EmiRecipe recipe = manager.getRecipe(id);
    require(recipe != null, "Missing native dynamic recipe: " + id);
    return recipe;
  }

  private static EmiStack input(EmiRecipe recipe, int index) {
    List<EmiStack> stacks = recipe.getInputs().get(index).getEmiStacks();
    if (stacks.size() != 1)
      throw new AssertionError("Expected one concrete NBT ingredient: " + recipe.getId());
    return stacks.get(0);
  }

  private static ItemStack output(EmiRecipe recipe) {
    if (recipe.getOutputs().size() != 1)
      throw new AssertionError("Expected one concrete output: " + recipe.getId());
    return recipe.getOutputs().get(0).getItemStack();
  }

  private void require(boolean condition, String message) {
    assertions++;
    if (!condition) throw new AssertionError(message);
  }

  private void finish(
      MinecraftClient client, boolean passed, Map<String, Object> details, Throwable failure) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("result", passed ? "PASS" : "FAIL");
    result.put("at", Instant.now().toString());
    result.put("runNumber", runNumber);
    result.put("trigger", trigger);
    result.putAll(details);
    if (failure != null) result.put("failure", failure.toString());
    Path file = client.runDirectory.toPath().resolve("icbm-emi-smoke-result.json");
    try {
      Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(result) + "\n");
    } catch (Exception writeFailure) {
      LOG.error("[ICBM_EMI_SMOKE] Cannot write result " + file, writeFailure);
    }
    if (passed) LOG.info("[ICBM_EMI_SMOKE] PASS {} result={}", result, file);
    else LOG.error("[ICBM_EMI_SMOKE] FAIL result=" + file + " details=" + result, failure);
    // Deliberately retain the client/world so the operator can continue visual checks.
  }
}

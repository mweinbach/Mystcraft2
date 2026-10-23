package dev.icbm.port.integration;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.nio.file.Files;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.server.MinecraftServer;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;

/** Optional-mod gates: Mekanism recipe alternatives and the REI viewer entrypoint. */
public final class IntegrationGameTests implements FabricGameTest {
  private static final List<String> MEKANISM_PAYLOADS = List.of("nuclear", "antimatter", "redmatter");
  private static final String REI_PLUGIN = "dev.icbm.port.integration.IcbmReiPlugin";

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void mekanismAlternativesFollowModPresence(TestContext c) {
    MinecraftServer server = c.getWorld().getServer();
    boolean mekanism = FabricLoader.getInstance().isModLoaded("mekanism");
    for (String payload : MEKANISM_PAYLOADS) {
      Identifier gated = new Identifier("icbm_port", payload + "_explosive_mekanism");
      Identifier recipeFile = new Identifier("icbm_port", "recipes/" + gated.getPath() + ".json");
      // The data must ship; absence below must come from its load condition, not a missing file.
      JsonObject json = readJson(c, server, recipeFile);
      if (!json.toString().contains("mekanism:alloy_atomic"))
        c.throwGameTestException(gated + " must use Mekanism atomic alloy");
      if (!payload.equals("nuclear") && ResourceConditions.objectMatchesConditions(json) != mekanism)
        c.throwGameTestException(gated + " condition must follow Mekanism presence");
      if (server.getRecipeManager().get(gated).isPresent() != mekanism)
        c.throwGameTestException(gated + (mekanism ? " missing with" : " loaded without") + " Mekanism");
      Identifier unlock = new Identifier("icbm_port", "recipes/" + gated.getPath());
      if ((server.getAdvancementLoader().get(unlock) != null) != mekanism)
        c.throwGameTestException(unlock + " unlock must share the recipe's Mekanism gate");
      // Upstream vanilla fallbacks load exactly when atomic alloy is unavailable.
      Identifier base = new Identifier("icbm_port", payload + "_explosive");
      if (server.getRecipeManager().get(base).isPresent() == mekanism)
        c.throwGameTestException(base + " fallback must load only without Mekanism");
    }
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void reiEntrypointShipsAndMirrorsSharedDisplays(TestContext c) {
    var container = FabricLoader.getInstance().getModContainer("icbm_port").orElseThrow();
    // Never touch IcbmReiPlugin.class here: its REI client interface is stripped on servers.
    if (container.findPath(REI_PLUGIN.replace('.', '/') + ".class").isEmpty())
      c.throwGameTestException("REI plugin class is not packaged");
    try (Reader reader = Files.newBufferedReader(container.findPath("fabric.mod.json").orElseThrow())) {
      JsonObject metadata = JsonParser.parseReader(reader).getAsJsonObject();
      var entrypoints = metadata.getAsJsonObject("entrypoints").getAsJsonArray("rei_client");
      if (entrypoints == null || !entrypoints.toString().contains(REI_PLUGIN))
        c.throwGameTestException("rei_client entrypoint does not name IcbmReiPlugin");
      if (!metadata.getAsJsonObject("suggests").has("roughlyenoughitems"))
        c.throwGameTestException("fabric.mod.json must suggest REI");
    } catch (java.io.IOException e) {
      c.throwGameTestException("Cannot read icbm_port fabric.mod.json: " + e);
    }
    // The client entrypoint never runs on a dedicated server; check the shared conversion instead.
    if (FabricLoader.getInstance().isModLoaded("roughlyenoughitems")) ReiCheck.run(c);
    c.complete();
  }

  private static JsonObject readJson(TestContext c, MinecraftServer server, Identifier id) {
    var resource = server.getResourceManager().getResource(id);
    if (resource.isEmpty()) c.throwGameTestException("Missing shipped data " + id);
    try (Reader reader = resource.get().getReader()) {
      return JsonParser.parseReader(reader).getAsJsonObject();
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Separate class: only resolved when REI is present, so REI types never load otherwise. */
  private static final class ReiCheck {
    static void run(TestContext c) {
      List<CraftingRecipe> shared = JeiRecipeDisplays.all();
      var displays = IcbmReiDisplays.all();
      if (shared.isEmpty() || displays.size() != shared.size())
        c.throwGameTestException("REI must show every shared display: " + displays.size());
      for (int i = 0; i < shared.size(); i++) {
        CraftingRecipe source = shared.get(i);
        var display = displays.get(i);
        ItemStack output =
            (ItemStack) display.getOutputEntries().get(0).get(0).getValue();
        if (!ItemStack.areEqual(output, source.getOutput()))
          c.throwGameTestException("REI output lost NBT for " + source.getId());
        if (display.getInputEntries().size() != source.getIngredients().size())
          c.throwGameTestException("REI inputs differ for " + source.getId());
        for (int j = 0; j < source.getIngredients().size(); j++) {
          ItemStack expected = source.getIngredients().get(j).getMatchingStacks()[0];
          ItemStack actual = (ItemStack) display.getInputEntries().get(j).get(0).getValue();
          if (!ItemStack.areEqual(actual, expected))
            c.throwGameTestException("REI input lost NBT for " + source.getId());
        }
        if (!display.getDisplayLocation().orElseThrow().getPath().startsWith("rei/"))
          c.throwGameTestException("REI display needs an icbm_port:rei/ id");
      }
    }
  }
}

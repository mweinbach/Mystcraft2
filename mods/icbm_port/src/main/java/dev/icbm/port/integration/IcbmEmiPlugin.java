package dev.icbm.port.integration;

import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiCraftingRecipe;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.stack.Comparison;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.icbm.port.delivery.DeliveryContent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Optional native EMI adapter, loaded only through EMI's client plugin entrypoint. */
public final class IcbmEmiPlugin implements EmiPlugin {
  private static final Comparison PAYLOAD_COMPARISON =
      Comparison.compareData(
          stack ->
              stack.getKey() instanceof Item item
                  ? JeiRecipeDisplays.subtype(item, stack.getNbt())
                  : "");

  private static EmiStack payloadStack(ItemStack stack) {
    return pin(EmiStack.of(stack));
  }

  private static EmiStack pin(EmiStack stack) {
    Object item = stack.getKey();
    if (item == DeliveryContent.CLUSTER_MISSILE
        || item == DeliveryContent.HELD_ITEM_MISSILE
        || item == DeliveryContent.CARGO_PARACHUTE
        || item == DeliveryContent.CARGO_BALLOON) stack.comparison(PAYLOAD_COMPARISON);
    return stack;
  }

  @Override
  public void register(EmiRegistry registry) {
    for (Item item : JeiRecipeDisplays.payloadItems()) {
      registry.setDefaultComparison(item, PAYLOAD_COMPARISON);
    }

    // The server's special recipes cannot be inferred from empty static ingredient/output lists.
    // Reuse the same real payload encodings and casing remainders as the optional JEI adapter.
    for (CraftingRecipe display : JeiRecipeDisplays.all()) {
      registry.addRecipe(craftingDisplay(display));
    }
    for (JeiRecipeDisplays.InfoPage page : JeiRecipeDisplays.infoPages()) {
      info(registry, page.id(), page.item(), page.text());
    }
  }

  /** Public for verification without starting a screen; preserves NBT and crafting remainders. */
  public static EmiCraftingRecipe craftingDisplay(CraftingRecipe display) {
    List<EmiIngredient> inputs = new ArrayList<>();
    display
        .getIngredients()
        .forEach(
            ingredient -> {
              // Explicit stack conversion prevents NBT-bearing loaded casings becoming item-only
              // tags.
              List<EmiStack> alternatives =
                  Arrays.stream(ingredient.getMatchingStacks())
                      .map(IcbmEmiPlugin::payloadStack)
                      .toList();
              inputs.add(EmiIngredient.of(alternatives));
            });
    if (display instanceof JeiRecipeDisplays.DisplayRecipe payloadDisplay && !inputs.isEmpty()) {
      ItemStack remainder = payloadDisplay.displayRemainder();
      if (!remainder.isEmpty()) {
        // All current unloading displays have one loaded container input; it is returned empty.
        for (EmiStack stack : inputs.get(0).getEmiStacks()) {
          stack.setRemainder(payloadStack(remainder.copy()));
        }
      }
    }
    String path = display.getId().getPath();
    if (path.startsWith("jei/")) path = path.substring(4);
    return new EmiCraftingRecipe(
        inputs,
        payloadStack(display.getOutput().copy()),
        new Identifier("icbm_port", "emi/" + path),
        true);
  }

  private static void info(EmiRegistry registry, String id, Item item, String text) {
    registry.addRecipe(
        new EmiInfoRecipe(
            List.of(payloadStack(new ItemStack(item))),
            List.of(Text.literal(text)),
            new Identifier("icbm_port", "emi/info/" + id)));
  }
}

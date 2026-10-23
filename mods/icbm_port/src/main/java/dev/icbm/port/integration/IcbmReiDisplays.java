package dev.icbm.port.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.plugin.common.displays.crafting.DefaultCustomShapelessDisplay;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.util.Identifier;

/**
 * Converts the shared {@link JeiRecipeDisplays} model into REI crafting displays.
 *
 * <p>Uses only REI's common API so a dedicated server GameTest can check the conversion; the
 * client-only {@link IcbmReiPlugin} adds tooltips and registers the result.
 */
public final class IcbmReiDisplays {
  private IcbmReiDisplays() {}

  public static List<DefaultCustomShapelessDisplay> all() {
    List<DefaultCustomShapelessDisplay> displays = new ArrayList<>();
    for (CraftingRecipe display : JeiRecipeDisplays.all()) displays.add(craftingDisplay(display));
    return List.copyOf(displays);
  }

  /** Preserves input/output NBT; every alternative keeps its exact payload stack. */
  public static DefaultCustomShapelessDisplay craftingDisplay(CraftingRecipe display) {
    List<EntryIngredient> inputs = new ArrayList<>();
    for (Ingredient ingredient : display.getIngredients()) {
      List<EntryStack<ItemStack>> alternatives = new ArrayList<>();
      for (ItemStack stack : ingredient.getMatchingStacks())
        alternatives.add(EntryStacks.of(stack.copy()));
      inputs.add(EntryIngredient.of(alternatives));
    }
    Identifier id = location(display);
    // DefaultCraftingDisplay derives its location from a backing recipe, and these display-only
    // recipes are deliberately absent from the server recipe manager.
    return new DefaultCustomShapelessDisplay(
        null, inputs, List.of(EntryIngredient.of(EntryStacks.of(display.getOutput().copy())))) {
      @Override
      public Optional<Identifier> getDisplayLocation() {
        return Optional.of(id);
      }
    };
  }

  /** The empty casing/carrier returned by an unloading display, or empty. */
  public static ItemStack remainder(CraftingRecipe display) {
    return display instanceof JeiRecipeDisplays.DisplayRecipe payload
        ? payload.displayRemainder()
        : ItemStack.EMPTY;
  }

  public static Identifier location(CraftingRecipe display) {
    String path = display.getId().getPath();
    if (path.startsWith("jei/")) path = path.substring(4);
    return new Identifier("icbm_port", "rei/" + path);
  }
}

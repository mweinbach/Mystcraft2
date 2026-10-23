package dev.icbm.port.integration;

import java.util.ArrayList;
import java.util.List;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.entry.comparison.ItemComparatorRegistry;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.plugin.client.BuiltinClientPlugin;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.text.Text;

/**
 * Optional native REI adapter, loaded only through REI's {@code rei_client} entrypoint. Shows the
 * same dynamic held/cargo/cluster crafting examples, NBT subtypes and usage pages as the EMI and
 * JEI adapters, all built by {@link JeiRecipeDisplays}.
 */
public final class IcbmReiPlugin implements REIClientPlugin {
  @Override
  public void registerItemComparators(ItemComparatorRegistry registry) {
    // Same raw-NBT identity as EMI/JEI; never constructs nested stacks or runs item hooks.
    registry.register(
        (context, stack) -> JeiRecipeDisplays.subtype(stack).hashCode(),
        JeiRecipeDisplays.payloadItems().toArray(Item[]::new));
  }

  @Override
  public void registerDisplays(DisplayRegistry registry) {
    // Special server recipes have no static ingredients, so REI's vanilla filler skips them.
    for (CraftingRecipe recipe : JeiRecipeDisplays.all()) {
      var display = IcbmReiDisplays.craftingDisplay(recipe);
      ItemStack remainder = IcbmReiDisplays.remainder(recipe);
      // All current unloading displays have one loaded container input; it is returned empty.
      if (!remainder.isEmpty())
        for (EntryStack<?> input : display.getInputEntries().get(0))
          input.tooltip(
              Text.translatable("tooltip.icbm_port.viewer_remainder", remainder.getName()));
      registry.add(display);
    }
    for (JeiRecipeDisplays.InfoPage page : JeiRecipeDisplays.infoPages()) {
      BuiltinClientPlugin.getInstance()
          .registerInformation(
              EntryStacks.of(page.item()),
              page.item().getName(),
              lines -> {
                List<Text> result = new ArrayList<>(lines);
                result.add(Text.literal(page.text()));
                return result;
              });
    }
  }
}

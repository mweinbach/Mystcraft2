package dev.icbm.port.integration;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Optional Fabric JEI integration. Loaded only through JEI's own plugin entrypoint. */
@JeiPlugin
public final class IcbmJeiPlugin implements IModPlugin {
  @Override
  public Identifier getPluginUid() {
    return new Identifier("icbm_port", "payload_crafting");
  }

  @Override
  public void registerItemSubtypes(ISubtypeRegistration registration) {
    // EMI's native plugin owns these when both viewers are installed; JEMI must not duplicate them.
    if (FabricLoader.getInstance().isModLoaded("emi")) return;
    for (var item : JeiRecipeDisplays.payloadItems())
      registration.registerSubtypeInterpreter(
          item, (stack, context) -> JeiRecipeDisplays.subtype(stack));
  }

  @Override
  public void registerRecipes(IRecipeRegistration registration) {
    if (FabricLoader.getInstance().isModLoaded("emi")) return;
    registration.addRecipes(RecipeTypes.CRAFTING, JeiRecipeDisplays.all());
    for (JeiRecipeDisplays.InfoPage page : JeiRecipeDisplays.infoPages())
      registration.addItemStackInfo(new ItemStack(page.item()), Text.literal(page.text()));
  }
}

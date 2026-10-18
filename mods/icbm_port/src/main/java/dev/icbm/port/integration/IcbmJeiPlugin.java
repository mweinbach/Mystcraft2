package dev.icbm.port.integration;

import dev.icbm.port.delivery.DeliveryContent;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
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
    if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("emi")) return;
    for (var item :
        java.util.List.of(
            DeliveryContent.CLUSTER_MISSILE,
            DeliveryContent.HELD_ITEM_MISSILE,
            DeliveryContent.CARGO_PARACHUTE,
            DeliveryContent.CARGO_BALLOON))
      registration.registerSubtypeInterpreter(
          item, (stack, context) -> JeiRecipeDisplays.subtype(stack));
  }

  @Override
  public void registerRecipes(IRecipeRegistration registration) {
    if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("emi")) return;
    registration.addRecipes(RecipeTypes.CRAFTING, JeiRecipeDisplays.all());
    registration.addItemStackInfo(
        new ItemStack(DeliveryContent.CLUSTER_MISSILE),
        Text.literal(
            "Craft an empty cluster carrier with tier-one bomblets to load a mixed payload. Add"
                + " more bomblets in later crafts, up to 64 total. An empty carrier cannot"
                + " launch."));
    registration.addItemStackInfo(
        new ItemStack(DeliveryContent.HELD_ITEM_MISSILE),
        Text.literal(
            "Craft the empty casing with one permitted item. Craft a loaded casing alone to"
                + " retrieve its item and keep the empty casing. Sneak-use cycles primary/secondary"
                + " action order. Both handheld and block launchers accept loaded missiles."));
    for (var carrier :
        java.util.List.of(DeliveryContent.CARGO_PARACHUTE, DeliveryContent.CARGO_BALLOON))
      registration.addItemStackInfo(
          new ItemStack(carrier),
          Text.literal(
              "Craft with one permitted item to store cargo; craft the loaded carrier alone to"
                  + " retrieve it and retain the empty carrier. Sneak-use cycles"
                  + " ITEM/BLOCK/ENTITY/PROJECTILE mode. Use on an entity to attach a passenger."
                  + " Server cargo restrictions apply."));
  }
}

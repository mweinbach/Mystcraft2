package dev.icbm.port.delivery;

import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;

/** A single inserted item, preserving NBT; already-loaded holders cannot overwrite cargo. */
public final class CarrierLoadingRecipe extends SpecialCraftingRecipe {
  public CarrierLoadingRecipe(Identifier id) {
    super(id);
  }

  @Override
  public boolean matches(CraftingInventory inventory, World world) {
    return !craft(inventory).isEmpty();
  }

  @Override
  public ItemStack craft(CraftingInventory inventory) {
    ItemStack missile = ItemStack.EMPTY, cargo = ItemStack.EMPTY;
    for (int i = 0; i < inventory.size(); i++) {
      ItemStack stack = inventory.getStack(i);
      if (stack.isEmpty()) continue;
      if (CarrierData.isCarrier(stack)) {
        if (!missile.isEmpty() || !CarrierData.cargo(stack).isEmpty()) return ItemStack.EMPTY;
        missile = stack;
      } else {
        if (!cargo.isEmpty() || !CarrierData.allowed(stack)) return ItemStack.EMPTY;
        cargo = stack;
      }
    }
    if (missile.isEmpty() || cargo.isEmpty()) return ItemStack.EMPTY;
    ItemStack result = missile.copy();
    result.setCount(1);
    CarrierData.setCargo(result, cargo);
    return result;
  }

  @Override
  public boolean fits(int width, int height) {
    return width * height >= 2;
  }

  @Override
  public RecipeSerializer<?> getSerializer() {
    return DeliveryContent.CARRIER_LOADING;
  }

  @Override
  public DefaultedList<ItemStack> getRemainder(CraftingInventory inventory) {
    return DefaultedList.ofSize(inventory.size(), ItemStack.EMPTY);
  }
}

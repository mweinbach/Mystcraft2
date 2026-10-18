package dev.icbm.port.delivery;

import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;

/** A single inserted item, preserving NBT; already-loaded holders cannot overwrite cargo. */
public final class HeldItemLoadingRecipe extends SpecialCraftingRecipe {
  public HeldItemLoadingRecipe(Identifier id) {
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
      if (stack.isOf(DeliveryContent.HELD_ITEM_MISSILE)) {
        if (!missile.isEmpty() || !HeldItemMissileItem.cargo(stack).isEmpty())
          return ItemStack.EMPTY;
        missile = stack;
      } else {
        if (!cargo.isEmpty() || !HeldItemMissileItem.allowed(stack)) return ItemStack.EMPTY;
        cargo = stack;
      }
    }
    if (missile.isEmpty() || cargo.isEmpty()) return ItemStack.EMPTY;
    ItemStack result = missile.copy();
    result.setCount(1);
    HeldItemMissileItem.setCargo(result, cargo);
    return result;
  }

  @Override
  public boolean fits(int width, int height) {
    return width * height >= 2;
  }

  @Override
  public RecipeSerializer<?> getSerializer() {
    return DeliveryContent.HELD_ITEM_LOADING;
  }

  @Override
  public DefaultedList<ItemStack> getRemainder(CraftingInventory inventory) {
    return DefaultedList.ofSize(inventory.size(), ItemStack.EMPTY);
  }
}

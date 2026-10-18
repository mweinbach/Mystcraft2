package dev.icbm.port.delivery;

import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;

/** Decrafting returns cargo as output and exactly one emptied missile as remainder. */
public final class HeldItemUnloadingRecipe extends SpecialCraftingRecipe {
  public HeldItemUnloadingRecipe(Identifier id) {
    super(id);
  }

  private int slot(CraftingInventory inventory) {
    int found = -1;
    for (int i = 0; i < inventory.size(); i++)
      if (!inventory.getStack(i).isEmpty()) {
        ItemStack stack = inventory.getStack(i);
        if (found >= 0
            || !stack.isOf(DeliveryContent.HELD_ITEM_MISSILE)
            || stack.getCount() != 1
            || HeldItemMissileItem.cargo(stack).isEmpty()) return -1;
        found = i;
      }
    return found;
  }

  @Override
  public boolean matches(CraftingInventory inventory, World world) {
    return slot(inventory) >= 0;
  }

  @Override
  public ItemStack craft(CraftingInventory inventory) {
    int slot = slot(inventory);
    return slot < 0 ? ItemStack.EMPTY : HeldItemMissileItem.cargo(inventory.getStack(slot));
  }

  @Override
  public DefaultedList<ItemStack> getRemainder(CraftingInventory inventory) {
    var result = DefaultedList.ofSize(inventory.size(), ItemStack.EMPTY);
    int slot = slot(inventory);
    if (slot >= 0) {
      ItemStack empty = inventory.getStack(slot).copy();
      empty.setCount(1);
      HeldItemMissileItem.setCargo(empty, ItemStack.EMPTY);
      result.set(slot, empty);
    }
    return result;
  }

  @Override
  public boolean fits(int width, int height) {
    return width * height >= 1;
  }

  @Override
  public RecipeSerializer<?> getSerializer() {
    return DeliveryContent.HELD_ITEM_UNLOADING;
  }
}

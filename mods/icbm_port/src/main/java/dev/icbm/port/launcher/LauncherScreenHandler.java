package dev.icbm.port.launcher;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

public final class LauncherScreenHandler extends ScreenHandler {
  public final Inventory inventory;
  public final PropertyDelegate properties;

  public LauncherScreenHandler(int id, PlayerInventory player) {
    this(id, player, new SimpleInventory(2), new ArrayPropertyDelegate(LauncherBlockEntity.PROPERTY_COUNT));
  }

  public LauncherScreenHandler(
      int id, PlayerInventory player, Inventory inventory, PropertyDelegate properties) {
    super(LauncherContent.SCREEN, id);
    this.inventory = inventory;
    this.properties = properties;
    addSlot(
        new Slot(inventory, 0, 26, 28) {
          public boolean canInsert(ItemStack stack) {
            return inventory.isValid(0, stack);
          }
        });
    addSlot(
        new Slot(inventory, 1, 116, 28) {
          public boolean canInsert(ItemStack stack) {
            return inventory.isValid(1, stack);
          }
        });
    for (int row = 0; row < 3; row++)
      for (int col = 0; col < 9; col++)
        addSlot(new Slot(player, col + row * 9 + 9, 8 + col * 18, 163 + row * 18));
    for (int col = 0; col < 9; col++) addSlot(new Slot(player, col, 8 + col * 18, 221));
    addProperties(properties);
  }

  public boolean canUse(PlayerEntity player) {
    return inventory.canPlayerUse(player);
  }

  public ItemStack transferSlot(PlayerEntity player, int index) {
    Slot slot = slots.get(index);
    if (!slot.hasStack()) return ItemStack.EMPTY;
    ItemStack stack = slot.getStack(), copy = stack.copy();
    if (index < 2) {
      if (!insertItem(stack, 2, 38, true)) return ItemStack.EMPTY;
    } else {
      int destination = inventory.isValid(0, stack) ? 0 : inventory.isValid(1, stack) ? 1 : -1;
      if (destination < 0 || !insertItem(stack, destination, destination + 1, false))
        return ItemStack.EMPTY;
    }
    if (stack.isEmpty()) slot.setStack(ItemStack.EMPTY);
    else slot.markDirty();
    return copy;
  }
}

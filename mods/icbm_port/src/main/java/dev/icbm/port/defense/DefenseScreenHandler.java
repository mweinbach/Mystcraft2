package dev.icbm.port.defense;

import dev.icbm.port.energy.EnergyContent;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.Slot;

public final class DefenseScreenHandler extends ScreenHandler {
  private final Inventory inventory;
  private final DefenseMachineBlockEntity machine;
  private final PropertyDelegate properties;

  public DefenseScreenHandler(int syncId, PlayerInventory playerInventory) {
    this(syncId, playerInventory, new SimpleInventory(1), null, new ArrayPropertyDelegate(15));
  }

  DefenseScreenHandler(
      int syncId, PlayerInventory playerInventory, DefenseMachineBlockEntity machine) {
    this(syncId, playerInventory, machine, machine, machine.properties());
  }

  private DefenseScreenHandler(
      int syncId,
      PlayerInventory playerInventory,
      Inventory inventory,
      DefenseMachineBlockEntity machine,
      PropertyDelegate properties) {
    super(DefenseContent.MACHINE_SCREEN, syncId);
    this.inventory = inventory;
    this.machine = machine;
    this.properties = properties;
    addProperties(properties);
    addSlot(
        new Slot(inventory, 0, 228, 20) {
          @Override
          public boolean canInsert(ItemStack stack) {
            return EnergyContent.isBattery(stack);
          }
        });
    for (int row = 0; row < 3; row++)
      for (int col = 0; col < 9; col++)
        addSlot(new Slot(playerInventory, col + row * 9 + 9, 48 + col * 18, 140 + row * 18));
    for (int col = 0; col < 9; col++) addSlot(new Slot(playerInventory, col, 48 + col * 18, 198));
  }

  public int value(int property) {
    return properties.get(property);
  }

  @Override
  public boolean canUse(PlayerEntity player) {
    return inventory.canPlayerUse(player);
  }

  @Override
  public boolean onButtonClick(PlayerEntity player, int button) {
    return machine != null
        && canUse(player)
        && !player.isSpectator()
        && button >= 0
        && button <= 12
        && ((button != 2 && button != 3) || machine.kind() == DefenseMachineBlock.Kind.RADAR)
        && (button != 10 || machine.kind() == DefenseMachineBlock.Kind.EMP)
        && (button != 12 || machine.kind() == DefenseMachineBlock.Kind.SAM)
        && machine.configure(button);
  }

  @Override
  public ItemStack transferSlot(PlayerEntity player, int index) {
    if (!canUse(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
    Slot slot = slots.get(index);
    if (!slot.hasStack()) return ItemStack.EMPTY;
    ItemStack stack = slot.getStack(), original = stack.copy();
    if (index == 0) {
      if (!insertItem(stack, 1, 37, true)) return ItemStack.EMPTY;
    } else {
      if (!EnergyContent.isBattery(stack) || !insertItem(stack, 0, 1, false))
        return ItemStack.EMPTY;
    }
    if (stack.isEmpty()) slot.setStack(ItemStack.EMPTY);
    else slot.markDirty();
    return original;
  }
}

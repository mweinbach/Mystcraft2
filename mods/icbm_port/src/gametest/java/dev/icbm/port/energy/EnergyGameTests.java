package dev.icbm.port.energy;

import dev.icbm.port.IcbmPort;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.registry.Registry;

public final class EnergyGameTests implements FabricGameTest {
  private static void check(TestContext c, boolean value, String message) {
    if (!value) c.throwGameTestException(message);
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void transactionRollbackAndExactActionCost(TestContext c) {
    int[] commits = {0};
    EnergyBuffer buffer = new EnergyBuffer(1000, 500, 300, () -> commits[0]++);
    try (Transaction tx = Transaction.openOuter()) {
      check(c, buffer.insert(700, tx) == 500, "Input limit must apply");
    }
    check(
        c, buffer.getAmount() == 0 && commits[0] == 0, "Aborted transfer must not mutate or dirty");
    try (Transaction tx = Transaction.openOuter()) {
      buffer.insert(500, tx);
      tx.commit();
    }
    check(c, buffer.getAmount() == 500 && commits[0] == 1, "Commit must persist exactly once");
    check(
        c, !buffer.consume(501) && buffer.getAmount() == 500, "Failed action must conserve energy");
    check(
        c,
        buffer.consume(500) && buffer.getAmount() == 0,
        "Machine action must not be limited to transfer rate");
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void batteryTransferConservesEnergyAndReloadClamps(TestContext c) {
    ItemStack battery = new ItemStack(Registry.ITEM.get(IcbmPort.id("battery")));
    ((BatteryItem) battery.getItem()).setStoredEnergy(battery, 10000);
    SimpleInventory inventory = new SimpleInventory(battery);
    EnergyBuffer buffer = new EnergyBuffer(1750, 10000, 10000, () -> {});
    long moved = EnergyContent.chargeFromBattery(inventory, 0, buffer, 10000);
    check(
        c,
        moved == 1750 && buffer.getAmount() == 1750,
        "Battery transfer must respect remaining capacity");
    check(
        c,
        ((BatteryItem) battery.getItem()).getStoredEnergy(inventory.getStack(0)) == 8250,
        "Battery must lose only transferred energy");
    NbtCompound saved = new NbtCompound();
    buffer.writeNbt(saved);
    saved.putLong("Energy", Long.MAX_VALUE);
    buffer.readNbt(saved);
    check(c, buffer.getAmount() == 1750, "Saved overflow must clamp to capacity");
    saved.putInt("Energy", 600);
    buffer.readNbt(saved);
    check(c, buffer.getAmount() == 600, "Legacy integer energy must load");
    c.complete();
  }
}

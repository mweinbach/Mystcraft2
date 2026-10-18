package dev.icbm.port.energy;

import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import team.reborn.energy.api.EnergyStorage;
import team.reborn.energy.api.EnergyStorageUtil;

public final class EnergyContent {
  private static boolean initialized;

  private EnergyContent() {}

  public static void register() {
    if (initialized) return;
    initialized = true;
    try {
      EnergyConfig.reload();
    } catch (java.io.IOException e) {
      org.slf4j.LoggerFactory.getLogger("icbm_port")
          .error("Retaining battery defaults: {}", e.getMessage());
    }
    net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register(
        (dispatcher, access, environment) ->
            dispatcher.register(
                net.minecraft.server.command.CommandManager.literal("icbm")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(
                        net.minecraft.server.command.CommandManager.literal("energy")
                            .then(
                                net.minecraft.server.command.CommandManager.literal("reload")
                                    .executes(
                                        context -> {
                                          try {
                                            EnergyConfig.reload();
                                            context
                                                .getSource()
                                                .sendFeedback(
                                                    net.minecraft.text.Text.literal(
                                                        "Battery configuration reloaded"),
                                                    true);
                                            return 1;
                                          } catch (java.io.IOException e) {
                                            context
                                                .getSource()
                                                .sendError(
                                                    net.minecraft.text.Text.literal(
                                                        e.getMessage()));
                                            return 0;
                                          }
                                        })))));

    EnergyStorage.SIDED.registerFallback(
        (world, pos, state, entity, side) ->
            !world.isClient
                    && entity instanceof EnergyMachine machine
                    && machine.acceptsEnergy(side)
                ? machine.energyBuffer()
                : null);
  }

  public static boolean isBattery(ItemStack stack) {
    return !stack.isEmpty() && EnergyStorageUtil.isEnergyStorage(stack);
  }

  public static long chargeFromBattery(
      Inventory inventory, int slot, EnergyBuffer target, long limit) {
    if (slot < 0 || slot >= inventory.size() || limit < 0) return 0;
    var context =
        ContainerItemContext.ofSingleSlot(InventoryStorage.of(inventory, null).getSlot(slot));
    return EnergyStorageUtil.move(context.find(EnergyStorage.ITEM), target, limit, null);
  }
}

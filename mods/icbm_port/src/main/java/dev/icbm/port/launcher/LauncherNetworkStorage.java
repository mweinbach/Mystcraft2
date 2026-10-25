package dev.icbm.port.launcher;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import team.reborn.energy.api.EnergyStorage;

/**
 * Network-wide energy and item views exposed by launcher frames, supports, connectors and screens.
 * Adapted from MIT ICBM-Classic {@code content/blocks/launcher/network/NetworkEnergyStorage.java}
 * and {@code NetworkInventory.java}: energy is insert-only and split across the connected
 * launchers ({@code floor(left / launchersLeft) + 1} each, in discovery order), and the item view is
 * every connected launcher's slots combined. The network is rediscovered on each lookup through
 * the bounded {@link LauncherNetwork#connected} walk, so breaking a connector splits the views
 * immediately and nothing is cached across ticks.
 */
public final class LauncherNetworkStorage {
  private LauncherNetworkStorage() {}

  public static List<LauncherBlockEntity> launchers(World world, BlockPos origin) {
    List<LauncherBlockEntity> launchers = new ArrayList<>();
    for (BlockPos pos : LauncherNetwork.connected(world, origin))
      if (world.getBlockEntity(pos) instanceof LauncherBlockEntity launcher) launchers.add(launcher);
    return launchers;
  }

  /** {@code EnergyStorage.SIDED} provider for launcher component blocks. */
  public static EnergyStorage energy(World world, BlockPos pos) {
    List<LauncherBlockEntity> launchers = launchers(world, pos);
    return launchers.isEmpty() ? null : new NetworkEnergy(launchers);
  }

  /** {@code ItemStorage.SIDED} provider for launcher component blocks. */
  public static Storage<ItemVariant> items(World world, BlockPos pos) {
    List<LauncherBlockEntity> launchers = launchers(world, pos);
    if (launchers.isEmpty()) return null;
    List<Storage<ItemVariant>> parts = new ArrayList<>();
    for (LauncherBlockEntity launcher : launchers) parts.add(InventoryStorage.of(launcher, null));
    return new CombinedStorage<>(parts);
  }

  private record NetworkEnergy(List<LauncherBlockEntity> launchers) implements EnergyStorage {
    @Override
    public boolean supportsInsertion() {
      return true;
    }

    @Override
    public long insert(long maxAmount, TransactionContext transaction) {
      StoragePreconditions.notNegative(maxAmount);
      long received = 0;
      int index = 0;
      for (LauncherBlockEntity launcher : launchers) {
        long launchersLeft = launchers.size() - index++;
        long left = maxAmount - received;
        if (left <= 0) break;
        long give = Math.min(left, left / launchersLeft + 1);
        received += launcher.energyBuffer().insert(give, transaction);
      }
      return received;
    }

    @Override
    public boolean supportsExtraction() {
      return false;
    }

    @Override
    public long extract(long maxAmount, TransactionContext transaction) {
      return 0;
    }

    @Override
    public long getAmount() {
      return launchers.stream().mapToLong(l -> l.energyBuffer().getAmount()).sum();
    }

    @Override
    public long getCapacity() {
      return launchers.stream().mapToLong(l -> l.energyBuffer().getCapacity()).sum();
    }
  }
}

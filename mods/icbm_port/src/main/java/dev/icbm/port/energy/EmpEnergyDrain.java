package dev.icbm.port.energy;

import dev.icbm.port.payload.PayloadConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.PlayerInventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import team.reborn.energy.api.EnergyStorage;

/**
 * Upstream EMP blasts also emptied energy devices. This drains other mods' Team Reborn Energy
 * blocks ({@link EnergyStorage#SIDED}) and items held in player and entity inventories ({@link
 * EnergyStorage#ITEM}) inside the blast sphere, all in one transaction.
 *
 * <p>Work is bounded: only loaded chunks are inspected, the nearest {@code maxBlocks} block
 * entities and nearest {@code maxEntities} inventory holders are examined, and each storage gets at
 * most {@link #EXTRACTIONS_PER_STORAGE} extraction calls. ICBM machines expose an insert-only view,
 * so this never drains them; EMP fields disable them instead.
 */
public final class EmpEnergyDrain {
  /** Calls per storage, so rate-limited storages still empty without unbounded loops. */
  public static final int EXTRACTIONS_PER_STORAGE = 16;

  private static final Direction[] SIDES = {
    null,
    Direction.DOWN,
    Direction.UP,
    Direction.NORTH,
    Direction.SOUTH,
    Direction.WEST,
    Direction.EAST
  };

  /** Registered in {@code Payloads.EMP_HANDLERS}; uses the EMP blast's radius and live config. */
  public static final dev.icbm.port.payload.Payloads.EmpHandler PAYLOAD_HANDLER =
      (world, center, radius) -> drain(world, center, radius, PayloadConfig.empEnergyDrain());

  /** Storages that lost energy and the total energy removed. */
  public record Result(int blocks, int items, long energy) {
    public static final Result NONE = new Result(0, 0, 0);
  }

  private EmpEnergyDrain() {}

  public static Result drain(
      ServerWorld world, Vec3d center, double radius, PayloadConfig.EmpEnergyDrain settings) {
    if (!settings.enabled()
        || !Double.isFinite(radius)
        || radius <= 0
        || !Double.isFinite(center.x)
        || !Double.isFinite(center.y)
        || !Double.isFinite(center.z)) return Result.NONE;
    double bounded = Math.min(512, radius);
    List<BlockEntity> blocks = blockEntities(world, center, bounded, settings.maxBlocks());
    List<Entity> holders = holders(world, center, bounded, settings.maxEntities());
    int drainedBlocks = 0, drainedItems = 0;
    long energy = 0;
    try (Transaction transaction = Transaction.openOuter()) {
      for (BlockEntity blockEntity : blocks) {
        long moved = drain(blockStorage(world, blockEntity), transaction);
        if (moved > 0) {
          drainedBlocks++;
          energy += moved;
        }
      }
      for (Entity holder : holders) {
        for (ContainerItemContext context : itemContexts(holder)) {
          long moved = drain(context.find(EnergyStorage.ITEM), transaction);
          if (moved > 0) {
            drainedItems++;
            energy += moved;
          }
        }
      }
      transaction.commit();
    }
    return new Result(drainedBlocks, drainedItems, energy);
  }

  private static long drain(EnergyStorage storage, TransactionContext transaction) {
    if (storage == null || !storage.supportsExtraction()) return 0;
    long total = 0;
    for (int i = 0; i < EXTRACTIONS_PER_STORAGE && storage.getAmount() > 0; i++) {
      long moved = storage.extract(storage.getAmount(), transaction);
      if (moved <= 0) break;
      total += moved;
    }
    return total;
  }

  /** The unsided view first, then the first side that allows extraction. */
  private static EnergyStorage blockStorage(ServerWorld world, BlockEntity blockEntity) {
    if (blockEntity.isRemoved()) return null;
    for (Direction side : SIDES) {
      EnergyStorage storage =
          EnergyStorage.SIDED.find(
              world, blockEntity.getPos(), blockEntity.getCachedState(), blockEntity, side);
      if (storage != null && storage.supportsExtraction()) return storage;
    }
    return null;
  }

  private static List<BlockEntity> blockEntities(
      ServerWorld world, Vec3d center, double radius, int limit) {
    if (limit <= 0) return List.of();
    List<BlockEntity> found = new ArrayList<>();
    int minX = MathHelper.floor(center.x - radius) >> 4,
        maxX = MathHelper.floor(center.x + radius) >> 4,
        minZ = MathHelper.floor(center.z - radius) >> 4,
        maxZ = MathHelper.floor(center.z + radius) >> 4;
    for (int x = minX; x <= maxX; x++) {
      for (int z = minZ; z <= maxZ; z++) {
        // Never loads or generates chunks.
        WorldChunk chunk = world.getChunkManager().getWorldChunk(x, z, false);
        if (chunk == null) continue;
        for (BlockEntity blockEntity : chunk.getBlockEntities().values())
          if (!blockEntity.isRemoved() && distance(blockEntity, center) <= radius * radius)
            found.add(blockEntity);
      }
    }
    found.sort(Comparator.comparingDouble(blockEntity -> distance(blockEntity, center)));
    return found.size() > limit ? List.copyOf(found.subList(0, limit)) : found;
  }

  private static double distance(BlockEntity blockEntity, Vec3d center) {
    return Vec3d.ofCenter(blockEntity.getPos()).squaredDistanceTo(center);
  }

  private static List<Entity> holders(ServerWorld world, Vec3d center, double radius, int limit) {
    if (limit <= 0) return List.of();
    List<Entity> found =
        new ArrayList<>(
            world.getOtherEntities(
                null,
                new Box(center, center).expand(radius),
                entity ->
                    !entity.isRemoved()
                        && entity.squaredDistanceTo(center) <= radius * radius
                        && (entity instanceof PlayerEntity player
                            ? !player.isSpectator()
                            : entity instanceof Inventory)));
    found.sort(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(center)));
    return found.size() > limit ? List.copyOf(found.subList(0, limit)) : found;
  }

  private static List<ContainerItemContext> itemContexts(Entity holder) {
    List<ContainerItemContext> contexts = new ArrayList<>();
    if (holder instanceof PlayerEntity player) {
      for (SingleSlotStorage<ItemVariant> slot : PlayerInventoryStorage.of(player).getSlots())
        if (!slot.isResourceBlank()) contexts.add(ContainerItemContext.ofPlayerSlot(player, slot));
    } else if (holder instanceof Inventory inventory) {
      for (SingleSlotStorage<ItemVariant> slot : InventoryStorage.of(inventory, null).getSlots())
        if (!slot.isResourceBlank()) contexts.add(ContainerItemContext.ofSingleSlot(slot));
    }
    return contexts;
  }
}

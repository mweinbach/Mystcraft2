package dev.icbm.port.payload;

import dev.icbm.port.entity.MissileEntity;
import dev.icbm.port.entity.PrimedExplosiveEntity;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.explosion.Explosion;
import org.jetbrains.annotations.Nullable;
import team.reborn.energy.api.EnergyStorage;

/**
 * Adapted from MIT ICBM-Classic {@code content/actions/emp/ActionEmpArea} with the EMP receivers of
 * {@code lib/capability/emp} and {@code content/missile/entity/CapabilityEmpMissile}. In the
 * {@code [-size, size)} cube: block entity inventories lose the energy in their items; and in the
 * {@code ±size} entity box creepers are struck as if by lightning (becoming charged), primed
 * explosives fizzle out without detonating, dropped items lose their energy, and each missile has a
 * 50% chance to be destroyed in a power-1 explosion, else an 80% chance its engine dies (it falls
 * and still detonates on impact), else a 10% chance its payload goes off. The registered {@link
 * Payloads#EMP_HANDLERS} then drain energy blocks and player/entity inventories ({@code
 * energy/EmpEnergyDrain}) and raise the defence EMP field (machines, SAMs and surviving missiles).
 *
 * <p>Port differences: upstream's empty-by-default block swap list is not ported; block entities
 * are found per loaded chunk (nearest {@link #MAX_BLOCK_ENTITIES}) instead of probing every cube
 * position; at most {@link BlastSupport#MAX_ENTITIES} entities are affected; a missile whose payload
 * the pulse triggers is removed (upstream left it flying); large smoke is a bounded sample on the
 * centre layer.
 */
final class EmpBlast {
  static final int MAX_BLOCK_ENTITIES = 1024;
  static final int EXTRACTIONS = 16;
  /** Upstream {@code ConfigBlast.emp.missiles} defaults. */
  static final float MISSILE_KILL = .5f, ENGINE_FULL_BLOW = 1f, ENGINE_KILL = .8f, TRIGGER = .1f;

  static final float ENGINE_FULL_BLOW_STRENGTH = 1f;

  private EmpBlast() {}

  static boolean detonate(
      ServerWorld world,
      Vec3d center,
      int size,
      @Nullable Entity source,
      dev.icbm.port.api.ActionSource attribution) {
    BlockPos origin = new BlockPos(center);
    tiles(world, origin, size);
    entities(world, origin, size);
    for (var handler : Payloads.EMP_HANDLERS) handler.accept(world, center);
    world.playSound(
        null, center.x, center.y, center.z, SoundEvents.BLOCK_BEACON_DEACTIVATE,
        SoundCategory.BLOCKS, 4f,
        (1f + (world.random.nextFloat() - world.random.nextFloat()) * .2f) * .7f);
    return true;
  }

  private static void tiles(ServerWorld world, BlockPos origin, int size) {
    for (int i = 0; i < Math.min(256, size * 4); i++) {
      double x = origin.getX() + world.random.nextInt(2 * size + 1) - size + .5;
      double z = origin.getZ() + world.random.nextInt(2 * size + 1) - size + .5;
      world.spawnParticles(ParticleTypes.LARGE_SMOKE, x, origin.getY() + .5, z, 1, 0, 0, 0, 0);
    }
    List<BlockEntity> found = new ArrayList<>();
    for (int cx = (origin.getX() - size) >> 4; cx <= (origin.getX() + size - 1) >> 4; cx++)
      for (int cz = (origin.getZ() - size) >> 4; cz <= (origin.getZ() + size - 1) >> 4; cz++) {
        WorldChunk chunk = world.getChunkManager().getWorldChunk(cx, cz, false);
        if (chunk == null) continue;
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
          BlockPos p = blockEntity.getPos().subtract(origin);
          if (!blockEntity.isRemoved()
              && blockEntity instanceof Inventory
              && p.getX() >= -size && p.getX() < size
              && p.getY() >= -size && p.getY() < size
              && p.getZ() >= -size && p.getZ() < size) found.add(blockEntity);
        }
      }
    found.sort(
        java.util.Comparator.comparingDouble(be -> be.getPos().getSquaredDistance(origin)));
    for (int i = 0; i < Math.min(found.size(), MAX_BLOCK_ENTITIES); i++)
      drain((Inventory) found.get(i));
  }

  private static void entities(ServerWorld world, BlockPos origin, int size) {
    Vec3d c = Vec3d.of(origin);
    Box box = new Box(origin.add(-size, -size, -size), origin.add(size, size, size));
    for (Entity entity : BlastSupport.entities(world, box, c, e -> true)) {
      if (entity instanceof CreeperEntity creeper) {
        LightningEntity lightning = EntityType.LIGHTNING_BOLT.create(world);
        if (lightning != null) {
          lightning.refreshPositionAfterTeleport(creeper.getPos());
          lightning.setCosmetic(true);
          creeper.onStruckByLightning(world, lightning);
        }
      } else if (entity instanceof PrimedExplosiveEntity) entity.discard();
      else if (entity instanceof ItemEntity item) drain(item);
      else if (entity instanceof MissileEntity missile) missile(world, missile);
    }
  }

  private static void missile(ServerWorld world, MissileEntity missile) {
    Vec3d at = missile.getPos();
    if (world.random.nextFloat() <= MISSILE_KILL) {
      missile.discard();
      if (world.random.nextFloat() <= ENGINE_FULL_BLOW)
        world.createExplosion(
            missile, at.x, at.y, at.z, ENGINE_FULL_BLOW_STRENGTH, false,
            Explosion.DestructionType.DESTROY);
    } else if (world.random.nextFloat() <= ENGINE_KILL) {
      // Upstream DeadFlightLogic: no more thrust or guidance; it falls and hits what it hits.
      // A permanent engine failure, not a temporary EMP disable: impact still detonates it.
      missile.killEngine();
    } else if (world.random.nextFloat() <= TRIGGER) {
      missile.discard();
      Payloads.detonate(world, at, missile.getPayload(), missile);
    }
  }

  /** Drains the energy items of an inventory (upstream {@code CapabilityEmpInventory}). */
  private static void drain(Inventory inventory) {
    try (Transaction transaction = Transaction.openOuter()) {
      for (SingleSlotStorage<ItemVariant> slot : InventoryStorage.of(inventory, null).getSlots())
        if (!slot.isResourceBlank())
          drain(ContainerItemContext.ofSingleSlot(slot).find(EnergyStorage.ITEM), transaction);
      transaction.commit();
    }
  }

  private static void drain(ItemEntity item) {
    if (item.getStack().isEmpty()) return;
    SimpleInventory holder = new SimpleInventory(item.getStack().copy());
    drain(holder);
    if (!net.minecraft.item.ItemStack.areEqual(holder.getStack(0), item.getStack()))
      item.setStack(holder.getStack(0));
  }

  private static void drain(
      @Nullable EnergyStorage storage,
      net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext transaction) {
    if (storage == null || !storage.supportsExtraction()) return;
    for (int i = 0; i < EXTRACTIONS && storage.getAmount() > 0; i++)
      if (storage.extract(storage.getAmount(), transaction) <= 0) break;
  }
}

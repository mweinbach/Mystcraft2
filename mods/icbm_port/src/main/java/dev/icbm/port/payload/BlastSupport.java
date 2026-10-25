package dev.icbm.port.payload;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.explosion.Explosion;
import org.jetbrains.annotations.Nullable;

/** Shared world helpers for the ported blasts; every method runs on the server thread. */
final class BlastSupport {
  /** Entities one blast effect may touch, nearest first (the queue's long-standing cap). */
  static final int MAX_ENTITIES = 256;

  private BlastSupport() {}

  static boolean loaded(ServerWorld world, BlockPos pos) {
    return !world.isOutOfHeightLimit(pos)
        && world.getWorldBorder().contains(pos)
        && world.isChunkLoaded(pos);
  }

  /** Upstream {@code Blast.getBlastRadius()}: never below three. */
  static float blastRadius(double size) {
    return (float) Math.max(3, size);
  }

  /** An unexploded vanilla explosion used only as context for drops, damage and chain priming. */
  static Explosion context(ServerWorld world, @Nullable Entity source, Vec3d center, float power) {
    return new Explosion(
        world,
        source,
        center.x,
        center.y,
        center.z,
        power,
        false,
        Explosion.DestructionType.DESTROY);
  }

  static @Nullable LivingEntity owner(ServerWorld world, Payloads.Job job) {
    Entity resolved = job.source.resolve(world);
    return resolved instanceof LivingEntity living ? living : null;
  }

  /** Loaded entities in the box, nearest to {@code center} first, capped at {@link #MAX_ENTITIES}. */
  static List<Entity> entities(
      ServerWorld world, Box box, Vec3d center, Predicate<Entity> predicate) {
    List<Entity> found =
        new ArrayList<>(
            world.getOtherEntities(null, box, e -> e.isAlive() && predicate.test(e)));
    found.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(center)));
    return found.size() > MAX_ENTITIES ? found.subList(0, MAX_ENTITIES) : found;
  }

  /**
   * Adapted from MIT ICBM-Classic {@code content/blast/Blast.doDamageEntities}: entities inside
   * {@code radius} take {@code (s^2 + s) / 2 * 8 * power + 1} explosion damage, where {@code s} is
   * the distance falloff times the vanilla exposure (block density), and are pushed away by
   * {@code s}.
   */
  static int damageEntities(
      ServerWorld world,
      Vec3d center,
      double radius,
      double power,
      boolean destroyItems,
      @Nullable LivingEntity owner) {
    if (radius <= 0) return 0;
    double reach = radius * 2 + 1;
    int hit = 0;
    for (Entity entity :
        entities(
            world,
            new Box(center, center).expand(reach),
            center,
            e -> destroyItems || !(e instanceof ItemEntity))) {
      double distance = entity.getPos().distanceTo(center) / radius;
      if (distance > 1) continue;
      Vec3d delta = entity.getPos().subtract(center);
      double magnitude = delta.length();
      Vec3d direction = magnitude < 1e-6 ? Vec3d.ZERO : delta.multiply(1 / magnitude);
      double exposure = Explosion.getExposure(center, entity);
      double scale = (1 - distance) * exposure;
      double damage = (scale * scale + scale) / 2 * 8 * power + 1;
      entity.damage(
          DamageSource.explosion(owner), (float) Math.min(Integer.MAX_VALUE, (int) damage));
      push(entity, direction.multiply(scale));
      hit++;
    }
    return hit;
  }

  /** Adds velocity and makes sure players receive it. */
  static void push(Entity entity, Vec3d velocity) {
    if (!Double.isFinite(velocity.x + velocity.y + velocity.z)) return;
    entity.addVelocity(velocity.x, velocity.y, velocity.z);
    entity.velocityModified = true;
    if (entity instanceof ServerPlayerEntity player)
      player.networkHandler.sendPacket(
          new net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket(player));
  }

  static boolean creative(Entity entity) {
    return entity instanceof PlayerEntity player && (player.isCreative() || player.isSpectator());
  }

  /**
   * Upstream {@code BlastTNT.doDestroyBlocks}/{@code Block.onBlockExploded}: drops (with the
   * explosion survival chance of {@code 1 / dropRadius} when {@code dropRadius > 0}), clears the
   * block and lets it react to the explosion, so explosive blocks chain-prime.
   */
  static boolean destroy(
      ServerWorld world, BlockPos pos, Explosion explosion, float dropRadius, int flags) {
    if (!loaded(world, pos)) return false;
    BlockState state = world.getBlockState(pos);
    if (state.isAir() || state.getHardness(world, pos) < 0) return false;
    if (dropRadius > 0 && state.getBlock().shouldDropItemsOnExplosion(explosion)) {
      BlockEntity blockEntity = state.hasBlockEntity() ? world.getBlockEntity(pos) : null;
      LootContext.Builder loot =
          new LootContext.Builder(world)
              .random(world.random)
              .parameter(LootContextParameters.ORIGIN, Vec3d.ofCenter(pos))
              .parameter(LootContextParameters.TOOL, ItemStack.EMPTY)
              .optionalParameter(LootContextParameters.BLOCK_ENTITY, blockEntity)
              .optionalParameter(LootContextParameters.THIS_ENTITY, explosion.getCausingEntity())
              .parameter(LootContextParameters.EXPLOSION_RADIUS, dropRadius);
      state.onStacksDropped(world, pos, ItemStack.EMPTY, true);
      for (ItemStack stack : state.getDroppedStacks(loot))
        net.minecraft.block.Block.dropStack(world, pos, stack);
    }
    world.setBlockState(pos, Blocks.AIR.getDefaultState(), flags);
    state.getBlock().onDestroyedByExplosion(world, pos, explosion);
    return true;
  }

  /** Deterministic per-index jitter in [0, 1), stable across save/reload. */
  static double jitter(long seed, long index) {
    long h = seed * 0x9E3779B97F4A7C15L + index * 0xC2B2AE3D27D4EB4FL;
    h ^= h >>> 33;
    h *= 0xff51afd7ed558ccdL;
    h ^= h >>> 33;
    return (h >>> 11) * 0x1.0p-53;
  }

  static long seed(Vec3d center, PayloadType type) {
    return Double.doubleToLongBits(center.x) * 31
        ^ Double.doubleToLongBits(center.y) * 17
        ^ Double.doubleToLongBits(center.z)
        ^ type.ordinal();
  }
}

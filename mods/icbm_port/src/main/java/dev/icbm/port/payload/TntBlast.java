package dev.icbm.port.payload;

import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.explosion.Explosion;
import org.jetbrains.annotations.Nullable;

/**
 * Adapted from MIT ICBM-Classic {@code content/blast/BlastTNT} (condensed, repulsive and attractive
 * explosives): 16 rays per cube-face axis march from the centre in 0.3-block steps, losing {@code
 * (resistance + 0.3) * 0.3} in each non-air block and 0.225 per step; positions reached with energy
 * left are destroyed with a {@code 1 / blastRadius} drop chance after entities are damaged
 * (condensed) or pushed (repulsive/attractive).
 *
 * <p>The payload's {@code energy} setting scales the ray energy ({@link #energyScale}); at its
 * default the rays start with upstream's {@code blastRadius * (0.7..1.3)}, and the scaled energy
 * never exceeds {@link PayloadType#MAX_RADIUS}.
 *
 * <p>Port differences: collection is time-sliced on the server thread (normally finishing in the
 * detonation tick), unloaded chunks stop a ray instead of loading, and at most {@link
 * BlastSupport#MAX_ENTITIES} entities are affected.
 */
final class TntBlast implements BlastWork {
  static final int RAYS_PER_AXIS = 16;
  /** Upstream {@code pushEntities(12, blastRadius * 4)}. */
  static final float PUSH_RADIUS = 12;

  private final PayloadType type;
  private final int size;
  private int ray, destroyed;
  private boolean effects;
  private final LongLinkedOpenHashSet affected = new LongLinkedOpenHashSet();
  private long[] order;

  TntBlast(PayloadType type, int size) {
    this.type = type;
    this.size = size;
  }

  /** Unit direction of cube-surface ray {@code index} out of {@code n^3}, or null if interior. */
  static @Nullable Vec3d cubeRay(int n, int index) {
    int xs = index % n, ys = (index / n) % n, zs = index / (n * n);
    if (!(xs == 0 || xs == n - 1 || ys == 0 || ys == n - 1 || zs == 0 || zs == n - 1)) return null;
    double x = xs / (n - 1f) * 2f - 1f, y = ys / (n - 1f) * 2f - 1f, z = zs / (n - 1f) * 2f - 1f;
    double length = Math.sqrt(x * x + y * y + z * z);
    return new Vec3d(x / length, y / length, z / length);
  }

  @Override
  public boolean tick(ServerWorld world, Payloads.Job job, int budget) {
    float blastRadius = BlastSupport.blastRadius(size);
    float rayEnergy = Math.min(PayloadType.MAX_RADIUS, blastRadius * energyScale(type));
    int total = RAYS_PER_AXIS * RAYS_PER_AXIS * RAYS_PER_AXIS, units = 0;
    while (ray < total && (units < budget || BlastBudget.hasTime(world))) {
      Vec3d direction = cubeRay(RAYS_PER_AXIS, ray++);
      if (direction == null) continue;
      units += march(world, job.center, direction, rayEnergy);
    }
    if (ray < total) return false;
    Entity source = job.source.resolve(world);
    Explosion explosion = BlastSupport.context(world, source, job.center, blastRadius);
    if (!effects) {
      effects = true;
      Vec3d c = job.center;
      world.spawnParticles(
          size >= 2 ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.EXPLOSION,
          c.x, c.y, c.z, 1, 1, 0, 0, 1);
      world.playSound(
          null, c.x, c.y, c.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 4f,
          (1f + (world.random.nextFloat() - world.random.nextFloat()) * .2f) * .7f);
      if (type == PayloadType.CONDENSED) {
        if (!job.suppressEntityDamage)
          BlastSupport.damageEntities(
              world, c, blastRadius, PayloadConfig.forType(type).entityDamage(), false,
              BlastSupport.owner(world, job));
      } else push(world, c, blastRadius * 4, type == PayloadType.ATTRACTIVE);
    }
    if (order == null) order = affected.toLongArray();
    for (int i = 0; destroyed < order.length && (i < budget || BlastBudget.hasTime(world)); i++)
      BlastSupport.destroy(world, BlockPos.fromLong(order[destroyed++]), explosion, blastRadius, 3);
    return destroyed >= order.length;
  }

  /**
   * The payload's configured {@code energy} relative to its default (6 condensed, 2 repulsive and
   * attractive), so the default matches upstream's ray energy of the blast radius.
   */
  static float energyScale(PayloadType type) {
    double base = PayloadDefaults.forType(type).energy();
    return base > 0 ? (float) (PayloadConfig.forType(type).energy() / base) : 1f;
  }

  private int march(ServerWorld world, Vec3d center, Vec3d step, float rayEnergy) {
    float energy = rayEnergy * (.7f + world.random.nextFloat() * .6f);
    double x = center.x, y = center.y, z = center.z;
    int steps = 0;
    BlockPos.Mutable pos = new BlockPos.Mutable();
    for (; energy > 0; energy -= .3f * .75f, steps++) {
      pos.set(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z));
      if (!BlastSupport.loaded(world, pos)) break;
      BlockState state = world.getBlockState(pos);
      if (!state.isAir()) {
        energy -= (state.getBlock().getBlastResistance() + .3f) * .3f;
        if (energy > 0) affected.add(pos.asLong());
      }
      x += step.x * .3;
      y += step.y * .3;
      z += step.z * .3;
    }
    return steps + 1;
  }

  /** Upstream {@code BlastTNT.pushEntities}; players get half the force. */
  private static void push(ServerWorld world, Vec3d center, float force, boolean attract) {
    for (Entity entity :
        BlastSupport.entities(
            world, new Box(center, center).expand(PUSH_RADIUS + 1), center, e -> true)) {
      double scale = entity.getPos().distanceTo(center) / PUSH_RADIUS;
      if (scale > 1) continue;
      Vec3d delta = entity.getPos().subtract(center);
      if (delta.lengthSquared() < 1e-12) continue;
      Vec3d direction = delta.normalize();
      double player = entity instanceof PlayerEntity ? .5 : 1;
      BlastSupport.push(
          entity,
          attract
              ? direction.multiply(-scale * force * player)
              : direction.multiply((1 - scale) * force * player));
    }
  }

  @Override
  public NbtCompound write() {
    NbtCompound nbt = new NbtCompound();
    nbt.putInt("ray", ray);
    nbt.putInt("destroyed", destroyed);
    nbt.putBoolean("effects", effects);
    nbt.putLongArray("affected", order != null ? order : affected.toLongArray());
    return nbt;
  }

  static TntBlast read(PayloadType type, NbtCompound nbt, int radius) {
    TntBlast blast = new TntBlast(type, radius);
    blast.ray = Math.max(0, nbt.getInt("ray"));
    blast.effects = nbt.getBoolean("effects");
    long[] saved = nbt.getLongArray("affected");
    for (long pos : saved) blast.affected.add(pos);
    if (blast.ray >= RAYS_PER_AXIS * RAYS_PER_AXIS * RAYS_PER_AXIS) {
      blast.order = blast.affected.toLongArray();
      blast.destroyed = Math.min(blast.order.length, Math.max(0, nbt.getInt("destroyed")));
    }
    return blast;
  }
}

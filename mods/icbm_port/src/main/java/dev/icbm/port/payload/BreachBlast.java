package dev.icbm.port.payload;

import dev.icbm.port.entity.PrimedExplosiveEntity;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.explosion.Explosion;
import org.jetbrains.annotations.Nullable;

/**
 * Adapted from MIT ICBM-Classic {@code content/blast/BlastBreach} (on top of {@code BlastTNT}). A
 * {@code (2w + 1)^2} bundle of lines ({@code w} = configured radius) runs {@link #DEPTH} blocks in
 * the blast direction. Each line starts with {@code energy * (1 - (h + v) * 0.25)}; a breakable
 * block whose blast resistance is below the remaining energy is collected and costs that much,
 * air keeps 65% of it, and an unbreakable block ends the line. Then, as {@code BlastTNT}, entities
 * within 3 blocks take damage and the collected blocks are destroyed with a 1/3 drop chance.
 *
 * <p>The direction is the placed block's facing reversed (into the surface it was placed on), or
 * the travel direction of a missile, grenade or other carrier. Port differences: upstream guessed
 * a carrier's direction from its rotation, here its velocity (or a living carrier's look) is used
 * and down is the fallback; the placed block is removed before its blast instead of during it.
 */
final class BreachBlast implements BlastWork {
  /** Upstream {@code ConfigBlast.breaching.depth}. */
  static final int DEPTH = 7;
  /** Upstream {@code ConfigBlast.breaching.energyDistanceScale}. */
  static final float ENERGY_DISTANCE_SCALE = .25f;
  /** Upstream {@code ConfigBlast.breaching.energyCostDistance}. */
  static final float ENERGY_COST_DISTANCE = .35f;

  private final int width;
  private final Direction direction;
  private long[] order;
  private int destroyed;

  private BreachBlast(int width, Direction direction) {
    this.width = width;
    this.direction = direction;
  }

  static BreachBlast create(ServerWorld world, Vec3d center, int size, @Nullable Entity source) {
    return new BreachBlast(size, direction(source));
  }

  static Direction direction(@Nullable Entity source) {
    if (source instanceof PrimedExplosiveEntity primed && primed.getBlastDirection() != null)
      return primed.getBlastDirection();
    if (source == null) return Direction.DOWN;
    Vec3d v = source.getVelocity();
    double horizontal = Math.sqrt(v.x * v.x + v.z * v.z);
    if (v.lengthSquared() > 1e-6) {
      if (Math.abs(v.y) >= horizontal) return v.y < 0 ? Direction.DOWN : Direction.UP;
      return Direction.getFacing(v.x, 0, v.z);
    }
    if (source instanceof LivingEntity living) {
      if (living.getPitch() > 45) return Direction.DOWN;
      if (living.getPitch() < -45) return Direction.UP;
      return living.getHorizontalFacing();
    }
    return Direction.DOWN;
  }

  @Override
  public boolean tick(ServerWorld world, Payloads.Job job, int budget) {
    Vec3d c = job.center;
    PayloadConfig.Settings settings = PayloadConfig.forType(PayloadType.BREACHING);
    float blastRadius = BlastSupport.blastRadius(width);
    if (order == null) {
      order = collect(world, c, (float) settings.energy());
      world.playSound(
          null, c.x, c.y, c.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 5f,
          (1f + (world.random.nextFloat() - world.random.nextFloat()) * .2f) * .7f);
      world.spawnParticles(
          width >= 2 ? ParticleTypes.EXPLOSION_EMITTER : ParticleTypes.EXPLOSION,
          c.x, c.y, c.z, 1, 1, 0, 0, 1);
      world.playSound(
          null, c.x, c.y, c.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 4f,
          (1f + (world.random.nextFloat() - world.random.nextFloat()) * .2f) * .7f);
      if (!job.suppressEntityDamage)
        BlastSupport.damageEntities(
            world, c, blastRadius, settings.entityDamage(), false, BlastSupport.owner(world, job));
    }
    Explosion explosion = BlastSupport.context(world, job.source.resolve(world), c, blastRadius);
    for (int i = 0; destroyed < order.length && (i < budget || BlastBudget.hasTime(world)); i++)
      BlastSupport.destroy(world, BlockPos.fromLong(order[destroyed++]), explosion, blastRadius, 3);
    return destroyed >= order.length;
  }

  private long[] collect(ServerWorld world, Vec3d c, float energy) {
    LongArrayList found = new LongArrayList();
    int cx = MathHelper.floor(c.x), cy = MathHelper.floor(c.y), cz = MathHelper.floor(c.z);
    for (int h = -width; h <= width; h++)
      for (int w = -width; w <= width; w++) {
        float remaining = energy - energy * (h + w) * ENERGY_DISTANCE_SCALE;
        for (int depth = 0; depth < DEPTH && remaining > 0; depth++) {
          int x = cx + direction.getOffsetX() * depth,
              y = cy + direction.getOffsetY() * depth,
              z = cz + direction.getOffsetZ() * depth;
          switch (direction.getAxis()) {
            case Y -> {
              x += h;
              z += w;
            }
            case X -> {
              y += h;
              z += w;
            }
            case Z -> {
              y += h;
              x += w;
            }
          }
          BlockPos pos = new BlockPos(x, y, z);
          if (!BlastSupport.loaded(world, pos)) break;
          BlockState state = world.getBlockState(pos);
          if (!state.isAir()) {
            if (state.getHardness(world, pos) < 0) break;
            float cost = state.getBlock().getBlastResistance();
            if (cost < remaining) {
              remaining -= cost;
              // Lines never share a position, so no de-duplication is needed.
              found.add(pos.asLong());
            }
          } else remaining *= 1 - ENERGY_COST_DISTANCE;
        }
      }
    return found.toLongArray();
  }

  @Override
  public NbtCompound write() {
    NbtCompound nbt = new NbtCompound();
    nbt.putInt("width", width);
    nbt.putString("direction", direction.getName());
    if (order != null) {
      long[] remaining = new long[order.length - destroyed];
      System.arraycopy(order, destroyed, remaining, 0, remaining.length);
      nbt.putLongArray("remaining", remaining);
    }
    return nbt;
  }

  static BreachBlast read(NbtCompound nbt, int radius) {
    Direction direction = Direction.byName(nbt.getString("direction"));
    BreachBlast blast =
        new BreachBlast(
            nbt.contains("width") ? Math.max(0, Math.min(radius, nbt.getInt("width"))) : radius,
            direction == null ? Direction.DOWN : direction);
    if (nbt.contains("remaining", 12)) blast.order = nbt.getLongArray("remaining");
    return blast;
  }
}

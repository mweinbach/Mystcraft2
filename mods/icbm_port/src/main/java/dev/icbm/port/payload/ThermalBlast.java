package dev.icbm.port.payload;

import dev.icbm.port.exposure.ExposureContent;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.explosion.Explosion;

/**
 * Adapted from MIT ICBM-Classic {@code content/blast/threaded/BlastThermobaric} and {@code
 * BlastNuclear} (with {@code BlastThreaded}). Rays at {@code ceil(pi r)} pitch slices and twice as
 * many yaw slices of {@code pi / steps} march in half-block steps from the centre with {@code
 * energy * (0.5..1)} power, losing 1.125 per step and, once per entered block, the block's blast
 * resistance (0.25 for liquids); breakable blocks reached with power left are collected. The
 * collected blocks are cleared nearest first without drops (letting explosive blocks chain-prime).
 * Entities within 50 blocks take {@link BlastSupport#damageEntities} damage of power {@code energy *
 * entityDamage} when the blast starts and again once collection finishes; nuclear blasts then add
 * radioactive block swaps (rot, radius 80) and mutation (radius 80).
 *
 * <p>Port differences: upstream computed the rays on a worker thread. Here every world read stays
 * on the server thread, time-sliced by {@link BlastBudget}; collection restarts after a reload
 * (it only reads the world) and the sorted removal list is saved with its cursor. Removal is
 * sorted by whole squared distance (bucket sort) instead of an exact comparator sort.
 */
final class ThermalBlast implements BlastWork {
  /** Upstream {@code ConfigThermobaric/ConfigNuclear.entityDamageScale}. */
  static final double DAMAGE_RADIUS = 50;
  /** Upstream {@code ConfigNuclear.rotScale} and {@code mutationScale}. */
  static final double NUCLEAR_AFTERMATH_RADIUS = 80;

  private final PayloadType type;
  private final int size;
  private int phase, ray, destroyed;
  private LongOpenHashSet seen = new LongOpenHashSet();
  private LongArrayList edits = new LongArrayList();
  private long[] order;

  ThermalBlast(PayloadType type, int size) {
    this.type = type;
    this.size = size;
  }

  private int radius() {
    return (int) BlastSupport.blastRadius(size);
  }

  @Override
  public boolean tick(ServerWorld world, Payloads.Job job, int budget) {
    PayloadConfig.Settings settings = PayloadConfig.forType(type);
    Vec3d c = job.center;
    if (phase == 0) {
      phase = 1;
      pulse(world, job, settings);
      sound(world, c, 7f);
    }
    if (phase == 1) {
      int radius = radius(), steps = (int) Math.ceil(Math.PI * radius);
      int total = 2 * steps * steps, units = 0;
      while (ray < total && (units < budget || BlastBudget.hasTime(world))) {
        int yawSlice = ray / steps, pitchSlice = ray % steps;
        ray++;
        units += march(world, c, radius, settings.energy(), steps, yawSlice, pitchSlice);
      }
      if (ray < total) return false;
      order = sortByDistance(edits.toLongArray(), c, radius);
      edits = null;
      seen = null;
      phase = 2;
      pulse(world, job, settings);
      if (type == PayloadType.NUCLEAR) {
        ExposureContent.contaminate(world, c, NUCLEAR_AFTERMATH_RADIUS);
        Payloads.mutate(world, c, NUCLEAR_AFTERMATH_RADIUS);
      }
      sound(world, c, 10f);
    }
    Explosion explosion =
        BlastSupport.context(world, job.source.resolve(world), c, (float) radius());
    for (int i = 0; destroyed < order.length && (i < budget || BlastBudget.hasTime(world)); i++)
      BlastSupport.destroy(world, BlockPos.fromLong(order[destroyed++]), explosion, 0, 3);
    return destroyed >= order.length;
  }

  private void pulse(ServerWorld world, Payloads.Job job, PayloadConfig.Settings settings) {
    if (job.suppressEntityDamage) return;
    BlastSupport.damageEntities(
        world,
        job.center,
        DAMAGE_RADIUS,
        settings.energy() * settings.entityDamage(),
        false,
        BlastSupport.owner(world, job));
  }

  private static void sound(ServerWorld world, Vec3d c, float volume) {
    world.playSound(
        null, c.x, c.y, c.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, volume,
        (1f + (world.random.nextFloat() - world.random.nextFloat()) * .2f) * .7f);
  }

  private int march(
      ServerWorld world,
      Vec3d c,
      int radius,
      double energy,
      int steps,
      int yawSlice,
      int pitchSlice) {
    double power = energy - energy * world.random.nextFloat() / 2;
    double yaw = Math.PI / steps * yawSlice, pitch = Math.PI / steps * pitchSlice;
    double dx = Math.sin(pitch) * Math.cos(yaw) * .5,
        dy = Math.cos(pitch) * .5,
        dz = Math.sin(pitch) * Math.sin(yaw) * .5;
    double x = c.x, y = c.y, z = c.z;
    long previous = Long.MIN_VALUE;
    int count = 0;
    BlockPos.Mutable pos = new BlockPos.Mutable();
    while (c.squaredDistanceTo(x, y, z) <= (double) radius * radius && power > 0) {
      count++;
      power -= .3f * .75f * 5;
      pos.set(Math.floor(x), Math.floor(y), Math.floor(z));
      long packed = pos.asLong();
      if (packed != previous) {
        // Upstream's unloaded check skips without advancing, which ends the ray.
        if (!BlastSupport.loaded(world, pos)) break;
        BlockState state = world.getBlockState(pos);
        if (!state.isAir()) {
          power -= state.getMaterial().isLiquid() ? .25f : state.getBlock().getBlastResistance();
          if (state.getHardness(world, pos) >= 0 && power > 0 && seen.add(packed))
            edits.add(packed);
        }
      }
      previous = packed;
      x += dx;
      y += dy;
      z += dz;
    }
    return count + 1;
  }

  /** Counting sort by whole squared distance from the centre, nearest first. */
  static long[] sortByDistance(long[] positions, Vec3d c, int radius) {
    int buckets = (radius + 2) * (radius + 2) * 3 + 1;
    int[] counts = new int[buckets + 1];
    int[] keys = new int[positions.length];
    for (int i = 0; i < positions.length; i++) {
      BlockPos p = BlockPos.fromLong(positions[i]);
      keys[i] =
          (int) Math.min(buckets - 1, Math.max(0, c.squaredDistanceTo(p.getX(), p.getY(), p.getZ())));
      counts[keys[i] + 1]++;
    }
    for (int i = 1; i <= buckets; i++) counts[i] += counts[i - 1];
    long[] sorted = new long[positions.length];
    for (int i = 0; i < positions.length; i++) sorted[counts[keys[i]]++] = positions[i];
    return sorted;
  }

  @Override
  public NbtCompound write() {
    NbtCompound nbt = new NbtCompound();
    // Collection only reads the world, so it restarts after a reload instead of saving its set.
    nbt.putInt("phase", phase == 2 ? 2 : phase);
    if (phase == 2) {
      long[] remaining = new long[order.length - destroyed];
      System.arraycopy(order, destroyed, remaining, 0, remaining.length);
      nbt.putLongArray("remaining", remaining);
    }
    return nbt;
  }

  static ThermalBlast read(PayloadType type, NbtCompound nbt, int radius) {
    ThermalBlast blast = new ThermalBlast(type, radius);
    blast.phase = Math.max(0, Math.min(2, nbt.getInt("phase")));
    if (blast.phase == 2) {
      blast.order = nbt.getLongArray("remaining");
      blast.edits = null;
      blast.seen = null;
    }
    return blast;
  }
}

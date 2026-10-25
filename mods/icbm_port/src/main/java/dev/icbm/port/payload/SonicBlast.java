package dev.icbm.port.payload;

import dev.icbm.port.block.CondensedExplosiveBlock;
import dev.icbm.port.simulation.FlyingBlockEntity;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Adapted from MIT ICBM-Classic {@code content/blast/BlastSonic} with its {@code
 * thread/ThreadLargeExplosion} ray pass (energy {@code 2 * radius}). Rays at {@code ceil(pi r)}
 * steps of yaw (twice round) and pitch march in half-block steps, losing 1.125 per step and the
 * block resistance (0.25 for fluids) every step inside a breakable block. On every fourth tick the
 * found blocks within {@code max(1, ticks)} of the centre are removed: explosive blocks are primed,
 * one in ten becomes a flying block, the rest are cleared without drops. Every tick, non-creative
 * entities within {@code 2 * ticks} are thrown outward and upward. The blast ends after {@code
 * radius} ticks, or at a removal pass with nothing left.
 *
 * <p>Port differences: the ray pass is time-sliced on the server thread instead of a worker thread
 * (upstream also waits for its thread), unloaded chunks stop a ray, at most {@link
 * BlastSupport#MAX_ENTITIES} entities are thrown per tick.
 */
final class SonicBlast implements BlastWork {
  private final int size;
  private int ray;
  private boolean started;
  private final LongArrayList results = new LongArrayList();
  private final it.unimi.dsi.fastutil.longs.LongOpenHashSet seen =
      new it.unimi.dsi.fastutil.longs.LongOpenHashSet();

  SonicBlast(int size) {
    this.size = size;
  }

  private int radius() {
    return (int) BlastSupport.blastRadius(size);
  }

  @Override
  public boolean tick(ServerWorld world, Payloads.Job job, int budget) {
    Vec3d c = job.center;
    int radius = radius();
    // Upstream doExplode(callCount++): the argument is the previous count, the field the new one.
    int calls = job.age, call = calls - 1;
    if (!started) {
      started = true;
      world.playSound(
          null, c.x, c.y, c.z, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.BLOCKS, 4f,
          (1f + (world.random.nextFloat() - world.random.nextFloat()) * .2f) * .7f);
    }
    int steps = (int) Math.ceil(Math.PI * radius), total = 2 * steps * steps, units = 0;
    while (ray < total && (units < budget || BlastBudget.hasTime(world))) {
      int phi = ray / steps, theta = ray % steps;
      ray++;
      units += march(world, c, radius, radius * 2, steps, phi, theta);
    }
    if (call % 4 == 0 && ray >= total) {
      if (results.isEmpty()) return true;
      int reach = Math.max(1, calls);
      for (int i = results.size() - 1; i >= 0; i--) {
        BlockPos pos = BlockPos.fromLong(results.getLong(i));
        if (c.distanceTo(Vec3d.of(pos)) > reach) continue;
        results.removeLong(i);
        if (!BlastSupport.loaded(world, pos)) continue;
        BlockState state = world.getBlockState(pos);
        if (state.isAir() || state.getHardness(world, pos) < 0) continue;
        if (state.getBlock() instanceof CondensedExplosiveBlock explosive)
          explosive.prime(world, pos, explosive.getPayload().blockFuseTicks());
        else if (world.random.nextFloat() < .1f
            && FlyingBlockEntity.capture(world, pos, Vec3d.ZERO, 0) != null) continue;
        world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
      }
    }
    int reach = 2 * calls;
    for (Entity entity :
        BlastSupport.entities(
            world, new Box(c, c).expand(reach), c, e -> !BlastSupport.creative(e))) {
      Vec3d delta = entity.getPos().subtract(c);
      double distance = delta.length();
      if (distance < 1e-6) continue;
      Vec3d d = delta.multiply(Math.max(0, 1 - distance / radius) * 3 / distance);
      BlastSupport.push(
          entity,
          new Vec3d(
              d.x * world.random.nextFloat() * .2,
              Math.abs(d.y * world.random.nextFloat()),
              d.z * world.random.nextFloat() * .2));
    }
    return calls > radius;
  }

  private int march(
      ServerWorld world, Vec3d c, int radius, double energy, int steps, int phi, int theta) {
    double power = energy - energy * world.random.nextFloat() / 2;
    double yaw = Math.PI * 2 / steps * phi, pitch = Math.PI / steps * theta;
    double dx = Math.sin(pitch) * Math.cos(yaw) * .5,
        dy = Math.cos(pitch) * .5,
        dz = Math.sin(pitch) * Math.sin(yaw) * .5;
    double x = c.x, y = c.y, z = c.z;
    int count = 0;
    BlockPos.Mutable pos = new BlockPos.Mutable();
    while (c.squaredDistanceTo(x, y, z) <= (double) radius * radius && power > 0) {
      count++;
      power -= .3f * .75f * 5;
      pos.set(Math.floor(x), Math.floor(y), Math.floor(z));
      // Upstream's unloaded check skips without advancing, which ends the ray.
      if (!BlastSupport.loaded(world, pos)) break;
      BlockState state = world.getBlockState(pos);
      if (!state.isAir() && state.getHardness(world, pos) >= 0) {
        power -=
            state.getBlock() instanceof net.minecraft.block.FluidBlock
                ? .25f
                : state.getBlock().getBlastResistance();
        if (power > 0 && seen.add(pos.asLong())) results.add(pos.asLong());
      }
      x += dx;
      y += dy;
      z += dz;
    }
    return count + 1;
  }

  @Override
  public NbtCompound write() {
    NbtCompound nbt = new NbtCompound();
    nbt.putInt("ray", ray);
    nbt.putBoolean("started", started);
    nbt.putLongArray("results", results.toLongArray());
    return nbt;
  }

  static SonicBlast read(NbtCompound nbt, int radius) {
    SonicBlast blast = new SonicBlast(radius);
    blast.ray = Math.max(0, nbt.getInt("ray"));
    blast.started = nbt.getBoolean("started");
    for (long pos : nbt.getLongArray("results")) if (blast.seen.add(pos)) blast.results.add(pos);
    return blast;
  }
}

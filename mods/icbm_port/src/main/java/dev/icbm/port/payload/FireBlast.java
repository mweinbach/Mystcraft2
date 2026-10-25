package dev.icbm.port.payload;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Adapted from MIT ICBM-Classic {@code content/blast/BlastFire} (incendiary): {@code radius} rays
 * per cube-face axis march in 0.3-block steps with {@code radius * (0.7..1.3)} energy, losing
 * {@code (resistance + 0.3) * 0.3} in non-air blocks and 0.225 per step. While energy remains, a
 * position at distance {@code d} is lit when {@code radius - random * d > 0.55 d}: replaceable or
 * air blocks become fire where fire can stand, and ice is melted to air. Entities are not set
 * alight directly (upstream does not either; the fire does that).
 *
 * <p>Port difference: rays are time-sliced over ticks on the server thread; unloaded chunks stop a
 * ray.
 */
final class FireBlast implements BlastWork {
  private final int size;
  private int ray;

  FireBlast(int size) {
    this.size = size;
  }

  @Override
  public boolean tick(ServerWorld world, Payloads.Job job, int budget) {
    int radius = (int) BlastSupport.blastRadius(size);
    int total = radius * radius * radius, units = 0;
    while (ray < total && (units < budget || BlastBudget.hasTime(world))) {
      Vec3d direction = TntBlast.cubeRay(radius, ray++);
      if (direction != null) units += march(world, job.center, direction, radius);
    }
    if (ray < total) return false;
    Vec3d c = job.center;
    world.playSound(
        null, c.x + .5, c.y + .5, c.z + .5, SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.BLOCKS,
        4f, 1f + (world.random.nextFloat() - world.random.nextFloat()) * .2f);
    return true;
  }

  private static int march(ServerWorld world, Vec3d center, Vec3d step, int radius) {
    float energy = radius * (.7f + world.random.nextFloat() * .6f);
    double x = center.x, y = center.y, z = center.z;
    int steps = 0;
    BlockPos.Mutable pos = new BlockPos.Mutable();
    for (; energy > 0; energy -= .3f * .75f, steps++) {
      pos.set(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z));
      if (!BlastSupport.loaded(world, pos)) break;
      // Upstream measures to the block corner.
      double distance = center.distanceTo(Vec3d.of(pos));
      BlockState state = world.getBlockState(pos);
      if (!state.isAir()) energy -= (state.getBlock().getBlastResistance() + .3f) * .3f;
      if (energy > 0) {
        double chance = radius - world.random.nextDouble() * distance;
        if (chance > distance * .55) {
          BlockState fire = Blocks.FIRE.getDefaultState();
          if ((state.isAir() || state.getMaterial().isReplaceable())
              && fire.canPlaceAt(world, pos)) world.setBlockState(pos, fire, 3);
          else if (state.isOf(Blocks.ICE)) world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
        }
      }
      x += step.x * .3;
      y += step.y * .3;
      z += step.z * .3;
    }
    return steps + 1;
  }

  @Override
  public NbtCompound write() {
    NbtCompound nbt = new NbtCompound();
    nbt.putInt("ray", ray);
    return nbt;
  }

  static FireBlast read(NbtCompound nbt, int radius) {
    FireBlast blast = new FireBlast(radius);
    blast.ray = Math.max(0, nbt.getInt("ray"));
    return blast;
  }
}

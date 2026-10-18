package dev.icbm.port.payload;

import java.util.*;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;

/**
 * Adapted from MIT BlastTNT.calculateDamage and BlastThermobaric.doRun. Collection precedes
 * removal, preserving shielding regardless of ray traversal order.
 */
final class RayWork {
  private static final int MAX_EDITS = 65536;
  private final boolean condensed;
  private final int radius;
  private final double initialEnergy;
  private int ray, step, edit;
  private double energy;
  private final LinkedHashSet<Long> affected = new LinkedHashSet<>();
  private List<Long> ordered;

  RayWork(boolean condensed, int radius, double energy) {
    this.condensed = condensed;
    this.radius = radius;
    this.initialEnergy = energy;
  }

  int rayCount() {
    int slices = Math.max(1, (int) Math.ceil(Math.PI * radius));
    return condensed ? 4096 : 2 * slices * slices;
  }

  boolean tick(ServerWorld world, Vec3d center, Entity source, int budget) {
    int spent = 0;
    while (ray < rayCount() && spent < budget) {
      spent++;
      int axis = 16;
      int x = ray % axis, y = ray / axis % axis, z = ray / (axis * axis);
      if (condensed
          && x != 0
          && x != axis - 1
          && y != 0
          && y != axis - 1
          && z != 0
          && z != axis - 1) {
        nextRay();
        continue;
      }
      Vec3d direction;
      if (condensed)
        direction = new Vec3d(x / 15.0 * 2 - 1, y / 15.0 * 2 - 1, z / 15.0 * 2 - 1).normalize();
      else {
        int slices = Math.max(1, (int) Math.ceil(Math.PI * radius));
        double yaw = Math.PI / slices * (ray / slices), pitch = Math.PI / slices * (ray % slices);
        direction =
            new Vec3d(
                Math.sin(pitch) * Math.cos(yaw), Math.cos(pitch), Math.sin(pitch) * Math.sin(yaw));
      }
      if (step == 0) {
        // Deterministic per-ray jitter remains identical after save/reload.
        double random = ((ray * 1103515245L + 12345) & 0xffff) / 65535.0;
        energy = initialEnergy * (condensed ? .7 + random * .6 : 1 - random * .5);
      }
      double stride = condensed ? .3 : .5;
      double distance = step * stride;
      if (energy <= 0 || distance > radius * (condensed ? 2 : 1) || step > 1024) {
        nextRay();
        continue;
      }
      BlockPos pos = new BlockPos(center.add(direction.multiply(distance)));
      if (world.isOutOfHeightLimit(pos)
          || !world.getWorldBorder().contains(pos)
          || !world.isChunkLoaded(pos)) {
        nextRay();
        continue;
      }
      BlockState state = world.getBlockState(pos);
      if (state.getHardness(world, pos) < 0) {
        nextRay();
        continue;
      }
      boolean enteringBlock =
          step == 0
              || !pos.equals(new BlockPos(center.add(direction.multiply((step - 1) * stride))));
      if (!state.isAir() && (condensed || enteringBlock)) {
        double resistance =
            !condensed && !state.getFluidState().isEmpty()
                ? .25
                : state.getBlock().getBlastResistance();
        energy -= condensed ? (resistance + .3) * .3 : resistance;
        if (energy > 0 && affected.size() < MAX_EDITS) affected.add(pos.asLong());
      }
      energy -= condensed ? .3 * .75 : .3 * .75 * 5;
      step++;
    }
    if (ray < rayCount()) return false;
    if (ordered == null) ordered = new ArrayList<>(affected);
    while (edit < ordered.size() && spent++ < budget) {
      BlockPos pos = BlockPos.fromLong(ordered.get(edit++));
      if (!world.isChunkLoaded(pos)
          || world.isOutOfHeightLimit(pos)
          || !world.getWorldBorder().contains(pos)) continue;
      BlockState block = world.getBlockState(pos);
      if (block.getHardness(world, pos) >= 0 && !block.isAir())
        world.breakBlock(pos, false, source);
    }
    return edit >= ordered.size();
  }

  private void nextRay() {
    ray++;
    step = 0;
    energy = 0;
  }

  NbtCompound write() {
    NbtCompound n = new NbtCompound();
    n.putBoolean("condensed", condensed);
    n.putInt("radius", radius);
    n.putDouble("initialEnergy", initialEnergy);
    n.putInt("ray", ray);
    n.putInt("step", step);
    n.putDouble("energy", energy);
    n.putInt("edit", edit);
    n.putLongArray("affected", affected.stream().mapToLong(Long::longValue).toArray());
    return n;
  }

  static RayWork read(NbtCompound n) {
    double power = n.getDouble("initialEnergy");
    if (!Double.isFinite(power)) power = 0;
    RayWork work =
        new RayWork(
            n.getBoolean("condensed"),
            Math.max(0, Math.min(80, n.getInt("radius"))),
            Math.max(0, Math.min(20000, power)));
    work.ray = Math.max(0, Math.min(work.rayCount(), n.getInt("ray")));
    work.step = Math.max(0, Math.min(1024, n.getInt("step")));
    work.energy = n.getDouble("energy");
    if (!Double.isFinite(work.energy)) work.energy = 0;
    work.energy = Math.max(0, Math.min(26000, work.energy));
    long[] positions = n.getLongArray("affected");
    for (int i = 0; i < Math.min(MAX_EDITS, positions.length); i++) work.affected.add(positions[i]);
    work.edit = Math.max(0, Math.min(work.affected.size(), n.getInt("edit")));
    return work;
  }
}

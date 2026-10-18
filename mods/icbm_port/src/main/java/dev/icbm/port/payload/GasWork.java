package dev.icbm.port.payload;

import java.util.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;

/** Six-neighbor diffusion adapted from upstream BlastGasBase's directional collision test. */
final class GasWork {
  private static final int MAX_CELLS = 65536;
  private final Set<Long> affected = new HashSet<>();
  private final Set<Long> visited = new HashSet<>();
  private final Deque<Long> frontier = new ArrayDeque<>();

  GasWork(BlockPos center) {
    affected.add(center.asLong());
    visited.add(center.asLong());
    frontier.add(center.asLong());
  }

  boolean contains(BlockPos pos) {
    return affected.contains(pos.asLong());
  }

  void tick(ServerWorld world, Vec3d center, int radius, int budget) {
    for (int checked = 0;
        checked + 6 <= budget && !frontier.isEmpty() && visited.size() < MAX_CELLS;
        checked += 6) {
      BlockPos edge = BlockPos.fromLong(frontier.removeFirst());
      for (Direction direction : Direction.values()) {
        BlockPos pos = edge.offset(direction);
        if (Vec3d.ofCenter(pos).squaredDistanceTo(center) > radius * radius
            || !world.isChunkLoaded(pos)
            || world.isOutOfHeightLimit(pos)
            || !world.getWorldBorder().contains(pos)
            || !visited.add(pos.asLong())) continue;
        var shape = world.getBlockState(pos).getCollisionShape(world, pos);
        boolean pass = shape.isEmpty();
        if (!pass) {
          Box b = shape.getBoundingBox();
          boolean x = b.minX <= 0 && b.maxX >= 1,
              y = b.minY <= 0 && b.maxY >= 1,
              z = b.minZ <= 0 && b.maxZ >= 1;
          pass =
              switch (direction.getAxis()) {
                case X -> !(y && z);
                case Y -> !(x && z);
                case Z -> !(x && y);
              };
        }
        if (pass) {
          affected.add(pos.asLong());
          frontier.addLast(pos.asLong());
        }
      }
    }
  }

  NbtCompound write() {
    NbtCompound n = new NbtCompound();
    n.putLongArray("affected", affected.stream().mapToLong(Long::longValue).toArray());
    n.putLongArray("visited", visited.stream().mapToLong(Long::longValue).toArray());
    n.putLongArray("frontier", frontier.stream().mapToLong(Long::longValue).toArray());
    return n;
  }

  static GasWork read(NbtCompound n, BlockPos center) {
    GasWork work = new GasWork(center);
    work.affected.clear();
    work.visited.clear();
    work.frontier.clear();
    for (long value : n.getLongArray("affected")) {
      if (work.affected.size() >= MAX_CELLS) break;
      work.affected.add(value);
    }
    for (long value : n.getLongArray("visited")) {
      if (work.visited.size() >= MAX_CELLS) break;
      work.visited.add(value);
    }
    for (long value : n.getLongArray("frontier")) {
      if (work.frontier.size() >= MAX_CELLS) break;
      if (work.affected.contains(value)) work.frontier.addLast(value);
    }
    return work;
  }
}

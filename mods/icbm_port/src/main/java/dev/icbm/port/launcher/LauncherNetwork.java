package dev.icbm.port.launcher;

import dev.icbm.port.block.LauncherBlock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/** Bounded loaded-chunk traversal of physically touching launcher components. */
public final class LauncherNetwork {
  public static List<BlockPos> connected(World world, BlockPos origin) {
    ArrayDeque<BlockPos> queue = new ArrayDeque<>();
    HashSet<BlockPos> seen = new HashSet<>();
    List<BlockPos> result = new ArrayList<>();
    queue.add(origin);
    seen.add(origin);
    while (!queue.isEmpty() && seen.size() <= 128) {
      BlockPos pos = queue.removeFirst();
      if (!world.isChunkLoaded(pos)) continue;
      Block block = world.getBlockState(pos).getBlock();
      if (!(block instanceof LauncherBlock || block instanceof LauncherComponentBlock)) continue;
      result.add(pos);
      for (Direction direction : Direction.values()) {
        BlockPos next = pos.offset(direction);
        if (next.getSquaredDistance(origin) <= 32 * 32 && world.isChunkLoaded(next)) {
          Block neighbor = world.getBlockState(next).getBlock();
          if ((neighbor instanceof LauncherBlock || neighbor instanceof LauncherComponentBlock)
              && seen.size() < 128
              && seen.add(next)) queue.addLast(next);
        }
      }
    }
    return result;
  }

  /**
   * Upstream {@code TileLauncherScreen#fireAllLaunchers}: fire every launcher in the network as one
   * salvo, each with the salvo size as its inaccuracy launcher count. Returns how many accepted.
   */
  public static int fireAll(net.minecraft.server.world.ServerWorld world, BlockPos origin) {
    List<LauncherBlockEntity> launchers = LauncherNetworkStorage.launchers(world, origin);
    int accepted = 0;
    for (LauncherBlockEntity launcher : launchers)
      if (launcher.launcherBlock().fire(world, launcher.getPos(), launchers.size()).accepted())
        accepted++;
    return accepted;
  }

  public static boolean canUse(World world, BlockPos launcher, PlayerEntity player) {
    if (player.squaredDistanceTo(launcher.getX() + .5, launcher.getY() + .5, launcher.getZ() + .5)
        <= 64) return true;
    if (player.squaredDistanceTo(launcher.getX() + .5, launcher.getY() + .5, launcher.getZ() + .5)
        > 40 * 40) return false;
    return connected(world, launcher).stream()
        .anyMatch(
            pos ->
                world.getBlockState(pos).isOf(LauncherContent.SCREEN_BLOCK)
                    && player.squaredDistanceTo(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5)
                        <= 64);
  }

  public static boolean hasFrame(World world, BlockPos launcher) {
    int required =
        world.getBlockState(launcher).getBlock() instanceof LauncherBlock block
            ? Math.max(1, block.tier)
            : 1;
    int count = 0;
    for (Direction direction : Direction.Type.HORIZONTAL)
      if (world.getBlockState(launcher.offset(direction)).isOf(LauncherContent.FRAME)) count++;
    return count >= required;
  }
}

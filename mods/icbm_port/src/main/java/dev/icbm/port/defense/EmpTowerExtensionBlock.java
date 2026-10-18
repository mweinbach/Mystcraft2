package dev.icbm.port.defense;

import net.minecraft.block.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Connected vertical boosters dynamically attach to the first base below them. */
public final class EmpTowerExtensionBlock extends Block {
  EmpTowerExtensionBlock() {
    super(Settings.of(Material.METAL).strength(4, 20).requiresTool());
  }

  public static BlockPos findBase(World world, BlockPos pos) {
    for (int height = 1; height <= 17; height++) {
      BlockPos below = pos.down(height);
      if (!world.isChunkLoaded(below)) return null;
      Block block = world.getBlockState(below).getBlock();
      if (block == DefenseContent.EMP_TOWER) return below;
      if (block != DefenseContent.EMP_TOWER_EXTENSION) return null;
    }
    return null;
  }

  @Override
  public void neighborUpdate(
      BlockState state,
      World world,
      BlockPos pos,
      Block sourceBlock,
      BlockPos sourcePos,
      boolean notify) {
    if (!world.isClient && findBase(world, pos) == null) world.breakBlock(pos, true);
  }

  @Override
  public void onBlockAdded(
      BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
    if (!world.isClient && findBase(world, pos) == null) world.breakBlock(pos, true);
  }
}

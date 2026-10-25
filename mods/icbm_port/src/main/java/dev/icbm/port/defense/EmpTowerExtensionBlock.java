package dev.icbm.port.defense;

import net.minecraft.block.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * EMP tower booster cell. Adapted from MIT ICBM-Classic {@code content/blocks/emptower/
 * BlockEmpTower.java} / {@code TileEMPTower#updateStructure}: boosters are passive column cells that
 * the base below counts on demand; a cell without a base is simply inert (upstream clears the host
 * reference instead of breaking the block), so nothing is torn down here.
 */
public final class EmpTowerExtensionBlock extends Block {
  EmpTowerExtensionBlock() {
    super(Settings.of(Material.METAL).strength(4, 20).requiresTool());
  }

  /**
   * The EMP tower base this cell currently boosts, walking down through tower and booster cells
   * (bounded by {@link DefenseMachineBlockEntity#EMP_MAX_COLUMN}); {@code null} when inert.
   */
  public static BlockPos findBase(World world, BlockPos pos) {
    BlockPos base = null;
    for (int height = 1; height <= DefenseMachineBlockEntity.EMP_MAX_COLUMN; height++) {
      BlockPos below = pos.down(height);
      if (!world.isChunkLoaded(below)) break;
      BlockState state = world.getBlockState(below);
      if (state.isOf(DefenseContent.EMP_TOWER)) base = below;
      else if (!state.isOf(DefenseContent.EMP_TOWER_EXTENSION)) break;
    }
    return base;
  }
}

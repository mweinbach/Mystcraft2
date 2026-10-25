package dev.icbm.port.block;

import dev.icbm.port.entity.PrimedExplosiveEntity;
import dev.icbm.port.payload.PayloadType;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Breaching charge that remembers the face it was placed on. Adapted from MIT ICBM-Classic {@code
 * content/blocks/explosive/BlockExplosive} (rotation from the clicked face) and {@code
 * TileEntityExplosive.trigger}, which aims the breaching blast opposite that facing, into the
 * surface behind the charge.
 */
public final class BreachingExplosiveBlock extends CondensedExplosiveBlock {
  public static final DirectionProperty FACING = Properties.FACING;

  public BreachingExplosiveBlock(Settings settings) {
    super(settings, PayloadType.BREACHING);
    setDefaultState(getStateManager().getDefaultState().with(FACING, net.minecraft.util.math.Direction.UP));
  }

  @Override
  protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
    builder.add(FACING);
  }

  @Override
  public BlockState getPlacementState(ItemPlacementContext context) {
    return getDefaultState().with(FACING, context.getSide());
  }

  @Override
  public BlockState rotate(BlockState state, BlockRotation rotation) {
    return state.with(FACING, rotation.rotate(state.get(FACING)));
  }

  @Override
  public BlockState mirror(BlockState state, BlockMirror mirror) {
    return state.rotate(mirror.getRotation(state.get(FACING)));
  }

  @Override
  protected void configurePrimed(World world, BlockPos pos, PrimedExplosiveEntity entity) {
    BlockState state = world.getBlockState(pos);
    if (state.isOf(this)) entity.setBlastDirection(state.get(FACING).getOpposite());
  }
}

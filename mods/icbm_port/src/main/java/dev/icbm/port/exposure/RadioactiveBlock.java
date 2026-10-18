package dev.icbm.port.exposure;

import net.minecraft.block.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/** Source dirt/stone radiation blocks: scheduled decay and probabilistic nearby effects. */
public final class RadioactiveBlock extends Block {
  private final Block clean;

  RadioactiveBlock(Block clean) {
    super(AbstractBlock.Settings.of(Material.STONE).strength(.5f).ticksRandomly());
    this.clean = clean;
  }

  @Override
  public void onBlockAdded(
      BlockState state, World world, BlockPos pos, BlockState old, boolean notify) {
    if (!world.isClient
        && !ExposureContent.config.randomTickOnly
        && ExposureContent.config.decayDelay > 0)
      world.createAndScheduleBlockTick(pos, this, Math.max(1, ExposureContent.config.decayDelay));
  }

  @Override
  public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
    if (ExposureContent.config.decayDelay <= 0) return;
    decay(world, pos, random);
    if (world.getBlockState(pos).isOf(this) && !ExposureContent.config.randomTickOnly)
      world.createAndScheduleBlockTick(
          pos, this, 1 + random.nextInt(Math.max(1, ExposureContent.config.decayDelay)));
  }

  @Override
  public void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
    if (ExposureContent.config.decayDelay <= 0) return;
    decay(world, pos, random);
  }

  public void decay(ServerWorld world, BlockPos pos, Random random) {
    if (random.nextFloat() < ExposureContent.config.decayChance)
      world.setBlockState(pos, clean.getDefaultState(), 3);
    ExposureContent.radiate(world, pos);
  }
}

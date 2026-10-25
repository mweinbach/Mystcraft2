package dev.icbm.port.exposure;

import net.minecraft.block.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * Radioactive dirt/stone, adapted from upstream ICBM-Classic {@code
 * icbm/classic/content/radioactive/BlockRadioactive.java} (MIT). Rock material (pickaxe required
 * to harvest either variant), hardness and blast resistance copied from the clean block, a random
 * tick starts a self-rescheduling 1..decayDelay tick chain that stops once the block decays, and
 * a 1-in-12 client display tick plays a quiet vanilla click in place of upstream's own sound.
 */
public final class RadioactiveBlock extends Block {
  /** Upstream {@code icbmclassic:radioactive_block}; port death message key is my own wording. */
  public static final DamageSource DAMAGE_SOURCE = new DamageSource("icbm_port.radioactive_block");

  private final Block clean;

  RadioactiveBlock(Block clean, float hardness, float resistance) {
    super(
        AbstractBlock.Settings.of(Material.STONE)
            .strength(hardness, resistance)
            .requiresTool()
            .ticksRandomly());
    this.clean = clean;
  }

  public Block clean() {
    return clean;
  }

  @Override
  public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
    tickDecay(world, pos, random);
  }

  @Override
  public void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
    tickDecay(world, pos, random);
  }

  /** Upstream updateTick: decay/radiate, then reschedule unless decayed or random-tick-only. */
  private void tickDecay(ServerWorld world, BlockPos pos, Random random) {
    if (ExposureContent.config.decayDelay <= 0) return;
    decay(world, pos, random);
    if (world.getBlockState(pos).isOf(this)
        && !ExposureContent.config.randomTickOnly
        && !world.getBlockTickScheduler().isQueued(pos, this))
      world.createAndScheduleBlockTick(
          pos, this, 1 + random.nextInt(Math.max(1, ExposureContent.config.decayDelay)));
  }

  public void decay(ServerWorld world, BlockPos pos, Random random) {
    if (random.nextFloat() < ExposureContent.config.decayChance)
      world.setBlockState(pos, clean.getDefaultState(), 3);
    ExposureContent.radiate(world, pos);
  }

  @Override
  public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
    if (random.nextInt(12) == 0)
      world.playSound(
          pos.getX() + .5,
          pos.getY() + .5,
          pos.getZ() + .5,
          SoundEvents.BLOCK_NOTE_BLOCK_HAT,
          SoundCategory.BLOCKS,
          .25f + random.nextFloat() * .1f,
          1.6f + random.nextFloat() * .2f,
          true);
  }
}

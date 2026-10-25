package dev.icbm.port.payload;

import dev.icbm.port.simulation.FlyingBlockEntity;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.Material;
import net.minecraft.block.SnowBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.explosion.Explosion;

/**
 * Adapted from MIT ICBM-Classic {@code content/blast/BlastBeam}, {@code BlastExothermic} and {@code
 * BlastEndothermic}. The blast opens with a fiery vanilla explosion of power 4 and a light beam;
 * the breakable blocks within {@code max(5, r / 10)} are lifted as flying blocks; 100 ticks later
 * every breakable block in the radius {@code r} sphere is collected and transformed (heat: water
 * and ice gone, plants burnt, stone to lava, magma or netherrack, sand to soul sand or netherrack,
 * soil to netherrack, fire scattered; cold: water to ice, fire out, lava to obsidian or snow,
 * magma to stone, netherrack to dirt, soul sand to sand or gravel, soil to ice or snow blocks, snow
 * scattered). Within {@code 0.3 r} every block is transformed, beyond that with chance {@code 1 -
 * d / r}. Completion sets the time of day (18000 hot, 1200 cold) when the daylight cycle runs; the
 * cold blast also poisons, slows and fatigues mobs in the radius cube.
 *
 * <p>Port differences: the sphere scans run time-sliced on the server thread (upstream used worker
 * threads) and the transformation is spread over ticks by {@link BlastBudget}; collection
 * restarts after a reload and the transformation list is saved with its cursor. The beam itself
 * is drawn by whatever registers with {@link BeamEffects}.
 */
final class BeamBlast implements BlastWork {
  /** Upstream {@code BlastBeam.secondThreadTimer}. */
  static final int WAIT_TICKS = 20 * 5;

  private final PayloadType type;
  private final int size;
  private int phase, cursor, timer = WAIT_TICKS, mutated;
  private long beamId;
  private LongArrayList found = new LongArrayList();
  private long[] edits;

  BeamBlast(PayloadType type, int size) {
    this.type = type;
    this.size = size;
  }

  private boolean hot() {
    return type == PayloadType.EXOTHERMIC;
  }

  private int radius() {
    return (int) BlastSupport.blastRadius(size);
  }

  private void beam(ServerWorld world, Vec3d center, float progress, boolean dying) {
    if (hot()) BeamEffects.update(world, center, beamId, .7f, .3f, 0, progress, dying);
    else BeamEffects.update(world, center, beamId, 0, .3f, .7f, progress, dying);
  }

  @Override
  public boolean tick(ServerWorld world, Payloads.Job job, int budget) {
    Vec3d c = job.center;
    if (phase == 0) {
      beamId = world.random.nextLong();
      world.playSound(
          null, c.x, c.y, c.z, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 4f, .8f);
      if (BlastSupport.loaded(world, new BlockPos(c)))
        world.createExplosion(
            job.source.resolve(world), c.x, c.y, c.z, 4f, true, Explosion.DestructionType.DESTROY);
      beam(world, c, .1f, false);
      beam(world, c, .2f, false);
      phase = 1;
    }
    if (phase == 1) {
      if (!scan(world, c, Math.max(5, radius() / 10), budget)) return false;
      beam(world, c, .5f, false);
      // Upstream spawns them with gravity lowered by 0.01 and no push, so they drop back down.
      for (long pos : found.toLongArray()) {
        FlyingBlockEntity block =
            FlyingBlockEntity.capture(world, BlockPos.fromLong(pos), Vec3d.ZERO, 0);
        if (block != null) block.setGravity(block.gravity() - .01);
      }
      found.clear();
      phase = 2;
    }
    if (phase == 2) {
      if (timer-- > 0) return false;
      beam(world, c, .8f, false);
      phase = 3;
    }
    if (phase == 3) {
      if (!scan(world, c, radius(), budget)) return false;
      edits = found.toLongArray();
      found = null;
      beam(world, c, 1f, false);
      phase = 4;
    }
    BlockPos origin = new BlockPos(MathHelper.floor(c.x), MathHelper.floor(c.y), MathHelper.floor(c.z));
    double radius = radius();
    for (int i = 0; mutated < edits.length && (i < budget || BlastBudget.hasTime(world)); i++) {
      BlockPos pos = BlockPos.fromLong(edits[mutated++]);
      if (!BlastSupport.loaded(world, pos)) continue;
      double distance = Math.sqrt(origin.getSquaredDistance(pos));
      if (hot()) heat(world, pos, distance, radius);
      else cool(world, pos, distance, radius);
    }
    if (mutated < edits.length) return false;
    complete(world, c);
    return true;
  }

  /** Collects breakable non-air blocks of the radius {@code r} sphere; true once finished. */
  private boolean scan(ServerWorld world, Vec3d c, int r, int budget) {
    int side = 2 * r + 1, total = side * side * side;
    BlockPos.Mutable pos = new BlockPos.Mutable();
    for (int i = 0; cursor < total && (i < budget || BlastBudget.hasTime(world)); i++, cursor++) {
      int x = cursor % side - r, y = (cursor / side) % side - r, z = cursor / (side * side) - r;
      if (x * x + y * y + z * z > r * r) continue;
      pos.set(MathHelper.floor(c.x + x), MathHelper.floor(c.y + y), MathHelper.floor(c.z + z));
      if (!BlastSupport.loaded(world, pos)) continue;
      BlockState state = world.getBlockState(pos);
      if (!state.isAir() && state.getHardness(world, pos) >= 0) found.add(pos.asLong());
    }
    if (cursor < total) return false;
    cursor = 0;
    return true;
  }

  private static void set(ServerWorld world, BlockPos pos, Block block) {
    world.setBlockState(pos, block.getDefaultState(), 3);
  }

  static void heat(ServerWorld world, BlockPos pos, double distance, double radius) {
    double decay = Math.max(1, radius * .3), ensured = Math.max(1, radius * .1);
    double scale = 1 - distance / radius;
    BlockState state = world.getBlockState(pos);
    Material material = state.getMaterial();
    if (material == Material.WATER || state.isOf(Blocks.ICE)) set(world, pos, Blocks.AIR);
    if (!(distance <= decay || world.random.nextDouble() < scale)) return;
    if (material == Material.LEAVES
        || material == Material.REPLACEABLE_PLANT
        || material == Material.PLANT) {
      if (!material.isReplaceable() || Blocks.FIRE.getDefaultState().canPlaceAt(world, pos))
        set(world, pos, Blocks.AIR);
      else set(world, pos, Blocks.FIRE);
    } else if (material == Material.STONE) {
      if (world.random.nextFloat() > .9f) set(world, pos, Blocks.LAVA);
      else if (world.random.nextBoolean()) set(world, pos.down(), Blocks.MAGMA_BLOCK);
      else if (world.random.nextBoolean() || distance <= ensured) netherrack(world, pos);
    } else if (material == Material.AGGREGATE) {
      if (world.random.nextBoolean()) set(world, pos.down(), Blocks.SOUL_SAND);
      else netherrack(world, pos);
    } else if (material == Material.SOIL || material == Material.SOLID_ORGANIC)
      netherrack(world, pos);
    if (world.random.nextDouble() < scale) fire(world, pos.up(), false);
  }

  private static void netherrack(ServerWorld world, BlockPos pos) {
    set(world, pos, Blocks.NETHERRACK);
    fire(world, pos.up(), true);
  }

  private static void fire(ServerWorld world, BlockPos pos, boolean random) {
    if (random && !world.random.nextBoolean()) return;
    if (!BlastSupport.loaded(world, pos)) return;
    if (world.getBlockState(pos).getMaterial().isReplaceable()
        && Blocks.FIRE.getDefaultState().canPlaceAt(world, pos)) set(world, pos, Blocks.FIRE);
  }

  static void cool(ServerWorld world, BlockPos pos, double distance, double radius) {
    double decay = Math.max(1, radius * .3);
    double scale = 1 - distance / radius;
    if (!(distance <= decay || world.random.nextDouble() < scale)) return;
    BlockState state = world.getBlockState(pos);
    Material material = state.getMaterial();
    if (material == Material.WATER) set(world, pos, Blocks.ICE);
    else if (state.isOf(Blocks.FIRE)) set(world, pos, Blocks.AIR);
    else if (state.isOf(Blocks.LAVA)) {
      int level = state.get(FluidBlock.LEVEL);
      if (level == 0) world.setBlockState(pos, Blocks.OBSIDIAN.getDefaultState());
      else
        world.setBlockState(
            pos,
            Blocks.SNOW.getDefaultState().with(SnowBlock.LAYERS, MathHelper.clamp(level / 2, 1, 8)),
            3);
    } else if (state.isOf(Blocks.MAGMA_BLOCK)) set(world, pos, Blocks.STONE);
    else if (state.isOf(Blocks.NETHERRACK)) set(world, pos, Blocks.DIRT);
    else if (state.isOf(Blocks.SOUL_SAND))
      set(world, pos, world.random.nextBoolean() ? Blocks.SAND : Blocks.GRAVEL);
    else if (material == Material.SOIL || material == Material.SOLID_ORGANIC)
      set(world, pos, world.random.nextBoolean() ? Blocks.ICE : Blocks.SNOW_BLOCK);
    if (world.random.nextDouble() < scale) snow(world, pos.up());
  }

  private static void snow(ServerWorld world, BlockPos pos) {
    if (!BlastSupport.loaded(world, pos)) return;
    BlockState layer = Blocks.SNOW.getDefaultState();
    if (world.getBlockState(pos).getMaterial().isReplaceable()
        && layer.canPlaceAt(world, pos)
        && world.getBlockState(pos.down()).isSideSolidFullSquare(world, pos.down(), Direction.UP))
      world.setBlockState(pos, layer.with(SnowBlock.LAYERS, 1 + world.random.nextInt(7)), 3);
  }

  private void complete(ServerWorld world, Vec3d c) {
    world.playSound(
        null, c.x, c.y, c.z, SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.BLOCKS, 4f, .8f);
    beam(world, c, 0, true);
    if (!hot()) {
      double r = radius();
      for (Entity entity :
          BlastSupport.entities(world, new Box(c, c).expand(r), c, e -> e instanceof MobEntity)) {
        MobEntity mob = (MobEntity) entity;
        mob.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 10 * 20, 2));
        mob.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 120 * 20, 2));
        mob.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 120 * 20, 4));
      }
    }
    if (world.getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE))
      world.setTimeOfDay(hot() ? 18_000 : 1_200);
  }

  @Override
  public NbtCompound write() {
    NbtCompound nbt = new NbtCompound();
    nbt.putLong("beam", beamId);
    // Scans only read the world, so they restart after a reload.
    nbt.putInt("phase", phase);
    nbt.putInt("timer", timer);
    if (phase == 4) {
      long[] remaining = new long[edits.length - mutated];
      System.arraycopy(edits, mutated, remaining, 0, remaining.length);
      nbt.putLongArray("remaining", remaining);
    }
    return nbt;
  }

  static BeamBlast read(PayloadType type, NbtCompound nbt, int radius) {
    BeamBlast blast = new BeamBlast(type, radius);
    blast.beamId = nbt.getLong("beam");
    blast.phase = Math.max(0, Math.min(4, nbt.getInt("phase")));
    blast.timer = nbt.contains("timer") ? Math.max(0, Math.min(WAIT_TICKS, nbt.getInt("timer"))) : WAIT_TICKS;
    if (blast.phase == 4) {
      blast.edits = nbt.getLongArray("remaining");
      blast.found = null;
    }
    return blast;
  }
}

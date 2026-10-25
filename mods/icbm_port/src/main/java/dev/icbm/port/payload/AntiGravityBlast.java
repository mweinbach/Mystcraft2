package dev.icbm.port.payload;

import dev.icbm.port.simulation.FlyingBlockEntity;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

/**
 * Adapted from MIT ICBM-Classic {@code content/blast/BlastAntiGravitational} with its {@code
 * thread/ThreadSmallExplosion} pass: {@code r} rays per cube-face axis march in 0.3-block steps
 * with {@code r * (0.7..1.3)} power (losing the block resistance in each non-air step and 0.225
 * per step, stopping at unbreakable blocks) and record every position reached. The positions are
 * shuffled and ordered top first; each tick the next liftable one becomes a spinning flying block
 * with no gravity, thrown up at 1 block/tick and outward. Every tick, entities from {@code r}
 * below to {@code max(r, 100)} above the centre rise by 0.15 while slower than 0.4 upward. After
 * 2400 ticks the lifted blocks regain gravity and fall.
 *
 * <p>Port differences: the ray pass is time-sliced on the server thread and restarts after a
 * reload (it only reads the world); each tick looks at no more than {@link #SCAN_PER_TICK}
 * positions; upstream's entity box used {@code y + r} as its east edge, here it is {@code x + r};
 * blocks straight above the centre are not given a NaN push; at most {@link
 * BlastSupport#MAX_ENTITIES} entities are lifted; only lifted blocks still loaded at the end regain
 * gravity (others settle when their air time runs out, as upstream's do).
 */
final class AntiGravityBlast implements BlastWork {
  static final int RUNNING_TICKS = 20 * 60 * 2;
  static final int SCAN_PER_TICK = 4096;
  /** Lifts one call may make up for when the queue served this job late (256 jobs / 8 slots). */
  static final int MAX_LIFT_CATCH_UP = 32;
  static final int MAX_TRACKED = RUNNING_TICKS + 1;

  private final int size;
  private int ray, searchIndex;
  /** {@code job.elapsedTicks} at the last lift, or -1 before the first. */
  private int lastLift = -1;
  private long seed;
  private boolean ordered;
  private final LongOpenHashSet results = new LongOpenHashSet();
  private long[] order;
  private final List<UUID> lifted = new ArrayList<>();

  AntiGravityBlast(ServerWorld world, Vec3d center, int size) {
    this.size = size;
    this.seed = world.random.nextLong();
  }

  private AntiGravityBlast(int size) {
    this.size = size;
  }

  private int radius() {
    return (int) BlastSupport.blastRadius(size);
  }

  @Override
  public boolean tick(ServerWorld world, Payloads.Job job, int budget) {
    Vec3d c = job.center;
    int radius = radius();
    int total = radius * radius * radius, units = 0;
    while (ray < total && (units < budget || BlastBudget.hasTime(world))) {
      Vec3d direction = TntBlast.cubeRay(radius, ray++);
      if (direction != null) units += march(world, c, direction, radius);
    }
    if (ray >= total) {
      if (!ordered) {
        ordered = true;
        order = results.toLongArray();
        results.clear();
        shuffle(order, Random.create(seed));
        // Stable sort keeps the shuffle within each layer.
        LongArrayList sorted = new LongArrayList(order);
        sorted.sort((a, b) -> Integer.compare(BlockPos.unpackLongY(b), BlockPos.unpackLongY(a)));
        order = sorted.toLongArray();
      }
      // Upstream lifts one block per world tick. A busy queue serves this job intermittently, so
      // it catches up on the ticks it missed (bounded per call).
      int due =
          lastLift < 0 ? 1 : Math.max(1, Math.min(MAX_LIFT_CATCH_UP, job.elapsedTicks - lastLift));
      lastLift = job.elapsedTicks;
      for (int i = 0; i < due; i++) lift(world, c);
    }
    pull(world, c, radius);
    // World ticks, not service calls: a busy queue serves each job only intermittently.
    int duration = PayloadConfig.forType(PayloadType.ANTI_GRAVITATIONAL).durationTicks();
    if (job.elapsedTicks > Math.min(duration, GasWork.MAX_ELAPSED_TICKS - 1)) {
      for (UUID id : lifted)
        if (world.getEntity(id) instanceof FlyingBlockEntity block) block.restoreGravity();
      return true;
    }
    return false;
  }

  private static void shuffle(long[] values, Random random) {
    for (int i = values.length - 1; i > 0; i--) {
      int j = random.nextInt(i + 1);
      long swap = values[i];
      values[i] = values[j];
      values[j] = swap;
    }
  }

  private void lift(ServerWorld world, Vec3d c) {
    if (order.length == 0) return;
    if (searchIndex >= order.length) searchIndex = 0;
    for (int scanned = 0; scanned < SCAN_PER_TICK && searchIndex < order.length; scanned++) {
      BlockPos pos = BlockPos.fromLong(order[searchIndex]);
      FlyingBlockEntity block = FlyingBlockEntity.capture(world, pos, Vec3d.ZERO, 0);
      if (block != null) {
        double dx = pos.getX() - MathHelper.floor(c.x), dz = pos.getZ() - MathHelper.floor(c.z);
        double magnitude = Math.sqrt(dx * dx + dz * dz);
        if (magnitude > 0) {
          dx /= magnitude;
          dz /= magnitude;
        }
        block.setSpin(50 * world.random.nextFloat(), 100 * world.random.nextFloat());
        block.setVelocity(
            dx * (1 - world.random.nextFloat()), 1, dz * (1 - world.random.nextFloat()));
        block.setGravity(0);
        if (lifted.size() < MAX_TRACKED) lifted.add(block.getUuid());
        // Upstream breaks without advancing; the emptied spot is skipped next tick.
        return;
      }
      searchIndex++;
    }
  }

  private static void pull(ServerWorld world, Vec3d c, int radius) {
    int height = Math.max(radius, 100);
    Box box = new Box(c.x - radius, c.y - radius, c.z - radius, c.x + radius, c.y + height, c.z + radius);
    for (Entity entity : BlastSupport.entities(world, box, c, e -> true))
      if (entity.getY() < height + c.y && entity.getVelocity().y < .4)
        BlastSupport.push(entity, new Vec3d(0, .15, 0));
  }

  private int march(ServerWorld world, Vec3d center, Vec3d step, int radius) {
    float power = radius * (.7f + world.random.nextFloat() * .6f);
    double x = center.x, y = center.y, z = center.z;
    int steps = 0;
    BlockPos.Mutable pos = new BlockPos.Mutable();
    for (; power > 0; power -= .3f * .75f, steps++) {
      pos.set(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z));
      if (!BlastSupport.loaded(world, pos)) break;
      BlockState state = world.getBlockState(pos);
      if (!state.isAir()) {
        if (state.getHardness(world, pos) < 0) break;
        power -= state.getBlock().getBlastResistance();
      }
      if (power > 0) results.add(pos.asLong());
      x += step.x * .3;
      y += step.y * .3;
      z += step.z * .3;
    }
    return steps + 1;
  }

  @Override
  public NbtCompound write() {
    NbtCompound nbt = new NbtCompound();
    nbt.putLong("seed", seed);
    nbt.putInt("searchIndex", searchIndex);
    nbt.putInt("lastLift", lastLift);
    NbtList ids = new NbtList();
    for (UUID id : lifted) ids.add(NbtHelper.fromUuid(id));
    nbt.put("lifted", ids);
    return nbt;
  }

  static AntiGravityBlast read(NbtCompound nbt, int radius) {
    AntiGravityBlast blast = new AntiGravityBlast(radius);
    blast.seed = nbt.getLong("seed");
    // The ray pass restarts (it only reads the world); the seeded order resumes near its index.
    blast.searchIndex = Math.max(0, nbt.getInt("searchIndex"));
    blast.lastLift = nbt.contains("lastLift") ? Math.max(-1, nbt.getInt("lastLift")) : -1;
    NbtList ids = nbt.getList("lifted", 11);
    for (int i = 0; i < Math.min(ids.size(), MAX_TRACKED); i++)
      blast.lifted.add(NbtHelper.toUuid(ids.get(i)));
    return blast;
  }
}

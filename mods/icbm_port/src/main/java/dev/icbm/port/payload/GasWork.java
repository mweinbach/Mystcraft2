package dev.icbm.port.payload;

import dev.icbm.port.exposure.ExposureContent;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.UUID;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3f;

/**
 * Gas cloud state for the debilitation, chemical, contagious and colors payloads. Adapted from
 * MIT ICBM-Classic {@code content/blast/gas/BlastGasBase.java} with the per-family colors and
 * entity rules of {@code BlastDebilitation}, {@code BlastChemical}, {@code BlastContagious} and
 * {@code BlastColor}.
 *
 * <p>Upstream behaviour kept: a run every {@link #RUN_INTERVAL} ticks; the cloud radius grows as
 * {@code floor(size * min(1, 2 * t / duration + 0.1))}; each time that radius changes one
 * flood-fill layer is pathed from the previous edge (6 neighbours, integer block distance from
 * the origin, the collision-box pass rule) and the edge is re-seeded from a random half of the
 * cloud when it runs dry; each run hits living entities whose block is in the cloud unless their
 * protective armor qualifies, counting hits per entity for this cloud; the cloud ends on the
 * first run where {@code t + 1 > duration}. Colors never affects entities.
 *
 * <p>Port adaptations: world reads stay on the server thread and a layer is pathed under a
 * per-tick neighbour-check budget, continuing on later ticks when it does not fit; the cloud,
 * hit counts and cursors are saved; vanilla dust particles stand in for upstream air particles.
 */
final class GasWork {
  /** Upstream {@code TICKS_BETWEEN_RUNS}. */
  static final int RUN_INTERVAL = 5;

  static final int MAX_CELLS = 65536;

  /** Entities whose cloud-local hit count is remembered (and saved) at once. */
  static final int MAX_TRACKED = 4096;

  /** Entities considered per run; matches the payload engine's other entity sweeps. */
  static final int MAX_ENTITIES_PER_RUN = 256;

  /** Dust packets per served tick for newly reached cells, and per run for the lingering cloud. */
  static final int NEW_CELL_PARTICLES = 32, CLOUD_PARTICLES = 32;

  /** Upstream {@code BlastColor.COLOR_COUNT}. */
  static final int COLOR_COUNT = 100;

  private static int[] palette;

  private final BlockPos origin;
  private final LongOpenHashSet affected = new LongOpenHashSet();

  /** Cloud cells in the order reached: saved, and sampled for lingering particles. */
  private final LongArrayList cells = new LongArrayList();

  private final LongArrayFIFOQueue edge = new LongArrayFIFOQueue();
  private final LongArrayFIFOQueue next = new LongArrayFIFOQueue();

  /** Upstream per-layer {@code hasPathed}; transient, cleared at each new layer. */
  private final LongOpenHashSet pathed = new LongOpenHashSet();

  private final Object2IntOpenHashMap<UUID> hits = new Object2IntOpenHashMap<>();
  private int lastRadius, layerRadius, lastRun = -1, colorIndex;

  GasWork(BlockPos origin) {
    this.origin = origin.toImmutable();
  }

  static boolean handles(PayloadType type) {
    return type == PayloadType.DEBILITATION
        || type == PayloadType.CHEMICAL
        || type == PayloadType.CONTAGIOUS
        || type == PayloadType.COLOR;
  }

  boolean contains(BlockPos pos) {
    return affected.contains(pos.asLong());
  }

  int cellCount() {
    return affected.size();
  }

  int hitCount(UUID entity) {
    return hits.getInt(entity);
  }

  int currentRadius() {
    return lastRadius;
  }

  /** Upstream {@code sizePercentageOverTime}, evaluated with the same float arithmetic. */
  static int radiusAt(int maxRadius, int duration, int timePassed) {
    double percentage = duration <= 0 ? 1 : Math.min(1, 2f * timePassed / duration + 0.1f);
    return (int) Math.floor(maxRadius * percentage);
  }

  /**
   * Advances the cloud by one served world tick.
   *
   * @param elapsedTicks world ticks since detonation, starting at 1 (upstream callCount + 1)
   * @param budget neighbour checks this tick may spend pathing the current layer
   * @return true once the cloud has expired
   */
  boolean tick(
      ServerWorld world,
      PayloadType type,
      Vec3d center,
      int maxRadius,
      int duration,
      int elapsedTicks,
      int budget) {
    int callCount = Math.max(0, elapsedTicks - 1);
    int run = callCount / RUN_INTERVAL;
    boolean due = run > lastRun;
    if (due) {
      if (lastRun < 0) startSound(world, center);
      lastRun = run;
      int radius = radiusAt(maxRadius, duration, run * RUN_INTERVAL + 1);
      if (radius != lastRadius) beginLayer(world, radius);
    }
    expand(world, type, budget);
    if (due) {
      cloudParticles(world, type);
      if (type != PayloadType.COLOR) affectEntities(world, type, center, maxRadius);
    }
    return lastRun * RUN_INTERVAL + 1 > duration;
  }

  private void startSound(ServerWorld world, Vec3d center) {
    // Upstream plays its debilitation hiss once when the cloud starts (playShortSoundFX is never
    // enabled by ExplosiveInit). Vanilla fizz is the original substitute.
    float pitch = (1 + (world.random.nextFloat() - world.random.nextFloat()) * .2f) * .7f;
    world.playSound(
        null,
        center.x,
        center.y,
        center.z,
        SoundEvents.BLOCK_FIRE_EXTINGUISH,
        SoundCategory.BLOCKS,
        4,
        pitch);
  }

  private void beginLayer(ServerWorld world, int radius) {
    lastRadius = radius;
    if (affected.isEmpty()) {
      add(origin.asLong());
      edge.enqueue(origin.asLong());
    }
    if (edge.isEmpty()) {
      while (!next.isEmpty()) edge.enqueue(next.dequeueLong());
      if (edge.isEmpty())
        for (int i = 0; i < cells.size(); i++)
          if (world.random.nextFloat() > .5f) edge.enqueue(cells.getLong(i));
    }
    layerRadius = radius;
    pathed.clear();
  }

  private int newParticles;

  private void expand(ServerWorld world, PayloadType type, int budget) {
    newParticles = 0;
    long radiusSq = (long) layerRadius * layerRadius;
    for (int checked = 0;
        checked + 6 <= budget && !edge.isEmpty() && affected.size() < MAX_CELLS;
        checked += 6) {
      BlockPos from = BlockPos.fromLong(edge.dequeueLong());
      for (Direction direction : Direction.values()) {
        BlockPos pos = from.offset(direction);
        long key = pos.asLong();
        if (affected.contains(key) || pathed.contains(key)) continue;
        long dx = pos.getX() - origin.getX(),
            dy = pos.getY() - origin.getY(),
            dz = pos.getZ() - origin.getZ();
        if (dx * dx + dy * dy + dz * dz > radiusSq) continue;
        if (world.isOutOfHeightLimit(pos)
            || !world.getWorldBorder().contains(pos)
            || !world.isChunkLoaded(pos)) continue;
        if (passable(world, pos, direction)) {
          add(key);
          next.enqueue(key);
          if (newParticles < NEW_CELL_PARTICLES) {
            newParticles++;
            particle(world, type, pos);
          }
        } else pathed.add(key);
      }
    }
  }

  private void add(long key) {
    if (affected.add(key)) cells.add(key);
  }

  /** Upstream {@code isValidPath}: blocked only when the box spans both faces across the move. */
  static boolean passable(ServerWorld world, BlockPos pos, Direction direction) {
    var shape = world.getBlockState(pos).getCollisionShape(world, pos);
    if (shape.isEmpty()) return true;
    Box box = shape.getBoundingBox();
    boolean x = box.minX <= 0 && box.maxX >= 1,
        y = box.minY <= 0 && box.maxY >= 1,
        z = box.minZ <= 0 && box.maxZ >= 1;
    return switch (direction.getAxis()) {
      case X -> !(y && z);
      case Y -> !(x && z);
      case Z -> !(x && y);
    };
  }

  private void affectEntities(ServerWorld world, PayloadType type, Vec3d center, int radius) {
    Box bounds = new Box(center, center).expand(radius);
    var entities =
        world.getEntitiesByClass(
            LivingEntity.class,
            bounds,
            e -> ExposureContent.gasCanAffect(e, type) && contains(e.getBlockPos()));
    float threshold = ExposureContent.gasProtectionThreshold(type);
    int considered = 0;
    for (LivingEntity entity : entities) {
      if (considered++ >= MAX_ENTITIES_PER_RUN) break;
      float protection = ExposureContent.protection(entity);
      if (!(protection < threshold || protection < world.random.nextFloat())) continue;
      UUID id = entity.getUuid();
      int count;
      if (hits.containsKey(id) || hits.size() < MAX_TRACKED) {
        count = Math.min(100000, hits.getInt(id) + 1);
        hits.put(id, count);
      } else count = 1;
      ExposureContent.applyGas(entity, type, count);
    }
  }

  private void cloudParticles(ServerWorld world, PayloadType type) {
    if (cells.isEmpty()) return;
    int count = Math.min(CLOUD_PARTICLES, cells.size());
    for (int i = 0; i < count; i++)
      particle(world, type, BlockPos.fromLong(cells.getLong(world.random.nextInt(cells.size()))));
  }

  private void particle(ServerWorld world, PayloadType type, BlockPos pos) {
    Vec3f color =
        switch (type) {
          case DEBILITATION -> new Vec3f(1, 1, 1);
          case CHEMICAL -> new Vec3f(.8f, .8f, 0);
          case CONTAGIOUS -> new Vec3f(.3f, .8f, 0);
          default -> paletteColor(world);
        };
    world.spawnParticles(
        new DustParticleEffect(color, 4),
        pos.getX() + .5,
        pos.getY() + .5,
        pos.getZ() + .5,
        1,
        .4,
        .4,
        .4,
        0);
  }

  /** Upstream {@code BlastColor}: 100 random hues at saturation 0.9, brightness 0.5, cycled. */
  private Vec3f paletteColor(ServerWorld world) {
    if (palette == null) {
      int[] colors = new int[COLOR_COUNT];
      for (int i = 0; i < COLOR_COUNT; i++)
        colors[i] = MathHelper.hsvToRgb(world.random.nextFloat(), .9f, .5f);
      palette = colors;
    }
    colorIndex = (colorIndex + 1) % COLOR_COUNT;
    int rgb = palette[colorIndex];
    return new Vec3f(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f);
  }

  NbtCompound write() {
    NbtCompound n = new NbtCompound();
    n.putInt("version", 2);
    n.putLongArray("cells", cells.toLongArray());
    n.putLongArray("edge", drain(edge));
    n.putLongArray("next", drain(next));
    n.putInt("lastRadius", lastRadius);
    n.putInt("layerRadius", layerRadius);
    n.putInt("lastRun", lastRun);
    n.putInt("colorIndex", colorIndex);
    NbtList hitList = new NbtList();
    for (var entry : hits.object2IntEntrySet()) {
      NbtCompound hit = new NbtCompound();
      hit.putUuid("id", entry.getKey());
      hit.putInt("hits", entry.getIntValue());
      hitList.add(hit);
    }
    n.put("hits", hitList);
    return n;
  }

  /** Copies a queue's contents without disturbing its order. */
  private static long[] drain(LongArrayFIFOQueue queue) {
    long[] values = new long[queue.size()];
    for (int i = 0; i < values.length; i++) {
      values[i] = queue.dequeueLong();
      queue.enqueue(values[i]);
    }
    return values;
  }

  static GasWork read(NbtCompound n, BlockPos center) {
    GasWork work = new GasWork(center);
    if (n.getInt("version") < 2) {
      // Earlier port saves held a whole-sphere fill; keep its cells and resume from its frontier.
      for (long value : n.getLongArray("affected")) {
        if (work.cells.size() >= MAX_CELLS) break;
        work.add(value);
      }
      for (long value : n.getLongArray("frontier"))
        if (work.next.size() < MAX_CELLS && work.affected.contains(value)) work.next.enqueue(value);
      return work;
    }
    for (long value : n.getLongArray("cells")) {
      if (work.cells.size() >= MAX_CELLS) break;
      work.add(value);
    }
    for (long value : n.getLongArray("edge"))
      if (work.edge.size() < MAX_CELLS && work.affected.contains(value)) work.edge.enqueue(value);
    for (long value : n.getLongArray("next"))
      if (work.next.size() < MAX_CELLS && work.affected.contains(value)) work.next.enqueue(value);
    work.lastRadius = Math.max(0, Math.min(PayloadType.MAX_RADIUS, n.getInt("lastRadius")));
    work.layerRadius = Math.max(0, Math.min(PayloadType.MAX_RADIUS, n.getInt("layerRadius")));
    work.lastRun = Math.max(-1, Math.min(72000, n.getInt("lastRun")));
    work.colorIndex = Math.floorMod(n.getInt("colorIndex"), COLOR_COUNT);
    NbtList hitList = n.getList("hits", 10);
    for (int i = 0; i < hitList.size() && work.hits.size() < MAX_TRACKED; i++) {
      NbtCompound hit = hitList.getCompound(i);
      if (hit.containsUuid("id"))
        work.hits.put(hit.getUuid("id"), Math.max(0, Math.min(100000, hit.getInt("hits"))));
    }
    return work;
  }
}

package dev.icbm.port.payload;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtLongArray;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A solid sphere of terrain to erase: an antimatter blast, or the crater of a red matter core
 * finishing chunks it could not reach. Loaded chunks are cleared nearest-first within a per-tick
 * time budget; chunks that are not loaded are remembered and cleared when they next load, so a
 * blast larger than the loaded area still leaves one round crater.
 */
final class Scar {
  private static final Logger LOGGER = LoggerFactory.getLogger("icbm_port");

  /**
   * Server-thread time one world tick may spend on scars, shared by every scar and detonation in
   * that tick. A default r=275 blast measured about 0.55 s on a solid-stone benchmark, so it clears
   * in the detonation tick; slower hardware or larger radii spill into the next ticks instead of
   * stalling one tick for longer than a second.
   */
  static long tickBudgetNanos = 1_000_000_000L;

  /** At most this many chunk positions are remembered per scar. */
  static final int MAX_CHUNKS = 4096;

  final Vec3d center;
  final int radius;
  final LongArrayList pending = new LongArrayList();
  final LongOpenHashSet deferred = new LongOpenHashSet();
  int cursor;
  int ticks;
  final SphereEraser.Stats stats = new SphereEraser.Stats();
  boolean reported;

  Scar(Vec3d center, int radius) {
    this.center = center;
    this.radius = radius;
  }

  /** Every chunk column the sphere touches, nearest to the centre first. */
  static Scar of(ServerWorld world, Vec3d center, int radius) {
    Scar scar = new Scar(center, radius);
    int reach = (radius >> 4) + 1, ccx = (int) Math.floor(center.x) >> 4, ccz = (int) Math.floor(center.z) >> 4;
    java.util.List<Long> chunks = new java.util.ArrayList<>();
    for (int dz = -reach; dz <= reach; dz++)
      for (int dx = -reach; dx <= reach; dx++)
        if (SphereEraser.touches(world, center, -1, radius, ccx + dx, ccz + dz))
          chunks.add(ChunkPos.toLong(ccx + dx, ccz + dz));
    chunks.sort(
        java.util.Comparator.comparingDouble(
            pos -> {
              double x = (ChunkPos.getPackedX(pos) << 4) + 8 - center.x;
              double z = (ChunkPos.getPackedZ(pos) << 4) + 8 - center.z;
              return x * x + z * z;
            }));
    for (int i = 0; i < Math.min(chunks.size(), MAX_CHUNKS); i++) scar.pending.add((long) chunks.get(i));
    return scar;
  }

  boolean active() {
    return cursor < pending.size();
  }

  boolean dormant() {
    return !active() && !deferred.isEmpty();
  }

  /** Clears loaded chunks until the deadline. Always clears at least one chunk per call. */
  void tick(ServerWorld world, long deadline) {
    if (!active()) return;
    ticks++;
    boolean first = true;
    while (cursor < pending.size() && (first || System.nanoTime() < deadline)) {
      first = false;
      long pos = pending.getLong(cursor++);
      WorldChunk chunk =
          world.getChunkManager().getWorldChunk(ChunkPos.getPackedX(pos), ChunkPos.getPackedZ(pos));
      if (chunk == null) {
        deferred.add(pos);
        continue;
      }
      if (SphereEraser.eraseChunk(world, chunk, center, -1, radius, false, stats) > 0) {
        long sent = System.nanoTime();
        SphereEraser.resend(world, chunk);
        stats.nanos += System.nanoTime() - sent;
      }
    }
    if (!active()) {
      pending.clear();
      cursor = 0;
      if (!reported) {
        reported = true;
        LOGGER.info(
            "Antimatter scar r={} at {}: {} chunks, {} blocks, {} sections replaced, {} edited, {} block-light seeds,"
                + " {} sky sections written, {} deferred; {} ms server time over {} ticks",
            radius,
            new ChunkPos((int) Math.floor(center.x) >> 4, (int) Math.floor(center.z) >> 4),
            stats.chunks,
            stats.blocks,
            stats.sectionsReplaced,
            stats.sectionsEdited,
            stats.lightSeeds,
            stats.lightSections,
            deferred.size(),
            String.format(java.util.Locale.ROOT, "%.1f", stats.nanos / 1e6),
            ticks);
      }
    }
  }

  /** A deferred chunk loaded; queue it for the next tick. */
  boolean chunkLoaded(long pos) {
    if (!deferred.remove(pos)) return false;
    pending.add(pos);
    return true;
  }

  NbtCompound write() {
    NbtCompound n = new NbtCompound();
    n.putDouble("x", center.x);
    n.putDouble("y", center.y);
    n.putDouble("z", center.z);
    n.putInt("radius", radius);
    LongArrayList remaining = new LongArrayList();
    for (int i = cursor; i < pending.size(); i++) remaining.add(pending.getLong(i));
    n.put("pending", new NbtLongArray(remaining.toLongArray()));
    n.put("deferred", new NbtLongArray(deferred.toLongArray()));
    return n;
  }

  static Scar read(NbtCompound n) {
    Vec3d center = new Vec3d(n.getDouble("x"), n.getDouble("y"), n.getDouble("z"));
    if (!Double.isFinite(center.x + center.y + center.z)
        || Math.abs(center.x) > 30000000
        || Math.abs(center.z) > 30000000) return null;
    int radius = Math.max(0, Math.min(PayloadType.MAX_EXTREME_RADIUS, n.getInt("radius")));
    Scar scar = new Scar(center, radius);
    scar.reported = true;
    for (long pos : n.getLongArray("pending"))
      if (scar.pending.size() < MAX_CHUNKS) scar.pending.add(pos);
    for (long pos : n.getLongArray("deferred"))
      if (scar.deferred.size() < MAX_CHUNKS) scar.deferred.add(pos);
    return scar;
  }
}

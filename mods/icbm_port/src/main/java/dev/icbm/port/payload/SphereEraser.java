package dev.icbm.port.payload;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.LightType;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.PalettedContainer;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.poi.PointOfInterestStorage;
import net.minecraft.world.poi.PointOfInterestTypes;

/**
 * Removes terrain inside a sphere (or a spherical shell) by writing chunk sections directly.
 *
 * <p>This is a deliberate departure from upstream's per-block {@code setBlockState} sweep. Nothing
 * drops, no neighbour or shape updates run, block entities are deleted without scattering their
 * inventories, sky light is written straight into the light arrays of opened columns, and block
 * light is only re-checked around removed light sources.
 * Unbreakable blocks (hardness &lt; 0), positions outside the world border and the build height are
 * left alone. A block belongs to the sphere when its centre lies within the radius.
 */
public final class SphereEraser {
  private static final BlockState AIR = Blocks.AIR.getDefaultState();

  private SphereEraser() {}

  /** Work counters, accumulated across chunks. */
  public static final class Stats {
    public long blocks;
    public long sectionsReplaced;
    public long sectionsEdited;
    public long lightSeeds;
    public long lightSections;
    public long chunks;
    public long nanos;
  }

  /** True when a block centred at {@code pos} lies inside the closed sphere. */
  public static boolean inside(Vec3d center, double radius, BlockPos pos) {
    return Vec3d.ofCenter(pos).squaredDistanceTo(center) <= radius * radius;
  }

  /**
   * True when some block of this chunk column might satisfy {@code inner < d <= outer}. Pass a
   * negative {@code inner} for a solid sphere.
   */
  public static boolean touches(ServerWorld world, Vec3d c, double inner, double outer, int cx, int cz) {
    double x0 = cx << 4, z0 = cz << 4, x1 = x0 + 16, z1 = z0 + 16;
    double nx = MathHelper.clamp(c.x, x0, x1) - c.x, nz = MathHelper.clamp(c.z, z0, z1) - c.z;
    double ny = MathHelper.clamp(c.y, world.getBottomY(), world.getTopY()) - c.y;
    if (nx * nx + nz * nz + ny * ny > (outer + 1) * (outer + 1)) return false;
    if (inner < 0) return true;
    double fx = Math.max(Math.abs(x0 - c.x), Math.abs(x1 - c.x));
    double fz = Math.max(Math.abs(z0 - c.z), Math.abs(z1 - c.z));
    double fy = Math.max(Math.abs(world.getBottomY() - c.y), Math.abs(world.getTopY() - c.y));
    return fx * fx + fz * fz + fy * fy >= (inner - 1) * (inner - 1);
  }

  /**
   * Clears every breakable block of {@code chunk} whose centre satisfies {@code inner < d <=
   * outer}. With {@code markBlocks} each change is queued for the vanilla per-section delta packet;
   * otherwise the caller resends the chunk with {@link #resend}. Returns the blocks removed.
   */
  public static int eraseChunk(
      ServerWorld world,
      WorldChunk chunk,
      Vec3d c,
      double inner,
      double outer,
      boolean markBlocks,
      Stats stats) {
    return eraseChunk(world, chunk, c, inner, outer, markBlocks, stats, null);
  }

  /**
   * Opened sky columns collected over several erasures and written in one pass. A core that eats a
   * one-block shell every tick would otherwise queue a fresh light array for every crater section
   * every tick, and the light thread, which also serves chunk loading, would fall behind.
   */
  public static final class SkyBatch {
    private final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<int[]> columns =
        new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();

    void add(ChunkPos pos, int[] openLo, int[] openHi) {
      int[] merged =
          columns.computeIfAbsent(
              pos.toLong(),
              key -> {
                int[] fresh = new int[512];
                java.util.Arrays.fill(fresh, 0, 256, Integer.MAX_VALUE);
                java.util.Arrays.fill(fresh, 256, 512, Integer.MIN_VALUE);
                return fresh;
              });
      // Successive openings of one column are contiguous: each starts where the last surface was.
      for (int i = 0; i < 256; i++)
        if (openLo[i] <= openHi[i]) {
          merged[i] = Math.min(merged[i], openLo[i]);
          merged[256 + i] = Math.max(merged[256 + i], openHi[i]);
        }
    }

    public boolean isEmpty() {
      return columns.isEmpty();
    }

    /** Writes everything collected so far; chunks that unloaded meanwhile are skipped. */
    public int flush(ServerWorld world) {
      var lighting = world.getChunkManager().getLightingProvider();
      int written = 0;
      for (var entry : columns.long2ObjectEntrySet()) {
        int[] merged = entry.getValue();
        written +=
            openSky(
                lighting,
                new ChunkPos(entry.getLongKey()),
                java.util.Arrays.copyOfRange(merged, 0, 256),
                java.util.Arrays.copyOfRange(merged, 256, 512),
                false);
      }
      columns.clear();
      return written;
    }
  }

  /** As {@link #eraseChunk}, collecting sky light into {@code sky} when it is not null. */
  public static int eraseChunk(
      ServerWorld world,
      WorldChunk chunk,
      Vec3d c,
      double inner,
      double outer,
      boolean markBlocks,
      Stats stats,
      SkyBatch sky) {
    long started = System.nanoTime();
    ChunkPos cp = chunk.getPos();
    int x0 = cp.getStartX(), z0 = cp.getStartZ();
    int bottom = world.getBottomY(), top = world.getTopY() - 1;
    double o2 = outer * outer, i2 = inner < 0 ? -1 : inner * inner;
    WorldBorder border = world.getWorldBorder();
    // Per column: the sphere interval [lo, hi] minus the already-cleared interval [ilo, ihi].
    int[] lo = new int[256], hi = new int[256], ilo = new int[256], ihi = new int[256];
    boolean any = false;
    for (int lz = 0; lz < 16; lz++)
      for (int lx = 0; lx < 16; lx++) {
        int i = lz << 4 | lx;
        double dx = x0 + lx + .5 - c.x, dz = z0 + lz + .5 - c.z, flat = dx * dx + dz * dz;
        ilo[i] = 1;
        ihi[i] = 0;
        if (flat > o2 || !border.contains(x0 + lx + .5, z0 + lz + .5)) {
          lo[i] = 1;
          hi[i] = 0;
          continue;
        }
        double h = Math.sqrt(o2 - flat);
        lo[i] = Math.max(bottom, (int) Math.ceil(c.y - h - .5));
        hi[i] = Math.min(top, (int) Math.floor(c.y + h - .5));
        if (i2 >= flat) {
          double ih = Math.sqrt(i2 - flat);
          ilo[i] = (int) Math.ceil(c.y - ih - .5);
          ihi[i] = (int) Math.floor(c.y + ih - .5);
        }
        any |= lo[i] <= hi[i];
      }
    if (!any) return 0;

    var lighting = world.getChunkManager().getLightingProvider();
    PointOfInterestStorage poi = world.getPointOfInterestStorage();
    List<BlockPos> seeds = new ArrayList<>();
    int[] changedLo = new int[256], changedHi = new int[256];
    java.util.Arrays.fill(changedLo, Integer.MAX_VALUE);
    java.util.Arrays.fill(changedHi, Integer.MIN_VALUE);
    BlockPos.Mutable mutable = new BlockPos.Mutable();
    ChunkSection[] sections = chunk.getSectionArray();
    int removed = 0;
    for (int index = 0; index < sections.length; index++) {
      ChunkSection section = sections[index];
      if (section == null || section.isEmpty()) continue;
      int sectionCoord = world.sectionIndexToCoord(index);
      int sy0 = sectionCoord << 4, sy1 = sy0 + 15;
      if (sy1 < bottom || sy0 > top) continue;
      boolean full = true, touched = false;
      for (int i = 0; i < 256; i++) {
        if (lo[i] > sy1 || hi[i] < sy0) {
          full = false;
          continue;
        }
        boolean hidden = ilo[i] <= sy0 && ihi[i] >= sy1;
        if (!hidden) touched = true;
        if (lo[i] > sy0 || hi[i] < sy1 || ilo[i] <= sy1 && ihi[i] >= sy0) full = false;
      }
      if (!touched) continue;
      BlockPos origin = new BlockPos(x0, sy0, z0);
      if (full && !markBlocks && !section.hasAny(s -> unbreakable(world, origin, s))) {
        // Whole section inside the sphere: swap in an empty container, keeping its biomes.
        if (section.hasAny(s -> s.getLuminance() > 0))
          collect(section, x0, sy0, z0, s -> s.getLuminance() > 0, seeds);
        if (section.hasAny(s -> PointOfInterestTypes.getTypeForState(s).isPresent())) {
          List<BlockPos> points = new ArrayList<>();
          collect(section, x0, sy0, z0, s -> PointOfInterestTypes.getTypeForState(s).isPresent(), points);
          for (BlockPos point : points) if (poi.getType(point).isPresent()) poi.remove(point);
        }
        sections[index] =
            new ChunkSection(
                sectionCoord,
                new PalettedContainer<>(
                    Block.STATE_IDS, AIR, PalettedContainer.PaletteProvider.BLOCK_STATE),
                section.getBiomeContainer());
        // The section deliberately stays "ready" for the light engine so its light arrays survive
        // and can take the directly written sky light below.
        for (int i = 0; i < 256; i++) {
          changedLo[i] = Math.min(changedLo[i], sy0);
          changedHi[i] = Math.max(changedHi[i], sy1);
        }
        removed += 4096;
        stats.sectionsReplaced++;
        continue;
      }
      int before = removed;
      for (int i = 0; i < 256; i++) {
        int from = Math.max(lo[i], sy0), to = Math.min(hi[i], sy1);
        if (from > to) continue;
        int lx = i & 15, lz = i >> 4;
        for (int y = from; y <= to; y++) {
          if (y >= ilo[i] && y <= ihi[i]) {
            y = ihi[i];
            continue;
          }
          BlockState state = section.getBlockState(lx, y & 15, lz);
          if (state.isAir()) continue;
          mutable.set(x0 + lx, y, z0 + lz);
          if (state.getHardness(world, mutable) < 0) continue;
          if (state.getLuminance() > 0) seeds.add(mutable.toImmutable());
          if (PointOfInterestTypes.getTypeForState(state).isPresent()
              && poi.getType(mutable).isPresent()) poi.remove(mutable.toImmutable());
          section.setBlockState(lx, y & 15, lz, AIR, false);
          if (markBlocks) world.getChunkManager().markForUpdate(mutable);
          changedLo[i] = Math.min(changedLo[i], y);
          changedHi[i] = Math.max(changedHi[i], y);
          removed++;
        }
      }
      if (removed > before) stats.sectionsEdited++;
    }
    if (removed == 0) {
      stats.nanos += System.nanoTime() - started;
      return 0;
    }
    // Delete orphaned block entities; their contents vanish with the block, as nothing drops.
    for (BlockPos pos : new ArrayList<>(chunk.getBlockEntityPositions())) {
      int i = (pos.getZ() & 15) << 4 | (pos.getX() & 15);
      if (pos.getY() >= changedLo[i] && pos.getY() <= changedHi[i] && chunk.getBlockState(pos).isAir())
        chunk.removeBlockEntity(pos);
    }
    int[] openLo = new int[256], openHi = new int[256];
    java.util.Arrays.fill(openLo, Integer.MAX_VALUE);
    java.util.Arrays.fill(openHi, Integer.MIN_VALUE);
    for (Map.Entry<Heightmap.Type, Heightmap> entry : chunk.getHeightmaps()) {
      Heightmap map = entry.getValue();
      Predicate<BlockState> blocking = entry.getKey().getBlockPredicate();
      boolean surface = entry.getKey() == Heightmap.Type.WORLD_SURFACE;
      for (int i = 0; i < 256; i++) {
        if (changedLo[i] > changedHi[i]) continue;
        int lx = i & 15, lz = i >> 4, oldTop = map.get(lx, lz) - 1;
        if (oldTop < changedLo[i] || oldTop > changedHi[i]) continue;
        mutable.set(x0 + lx, oldTop, z0 + lz);
        if (blocking.test(chunk.getBlockState(mutable))) continue;
        int height = scanDown(world, sections, lx, oldTop - 1, lz, blocking);
        map.set(lx, lz, height);
        if (surface) {
          // Everything from the new surface up to the old one now sees the sky.
          openLo[i] = height;
          openHi[i] = oldTop;
        }
      }
    }
    // Block light only changes around removed light sources; a handful of seeds at most.
    for (BlockPos seed : seeds) lighting.checkBlock(seed);
    if (sky != null) sky.add(cp, openLo, openHi);
    else stats.lightSections += openSky(lighting, cp, openLo, openHi, true);
    chunk.setNeedsSaving(true);
    stats.blocks += removed;
    stats.lightSeeds += seeds.size();
    stats.chunks++;
    stats.nanos += System.nanoTime() - started;
    return removed;
  }

  /**
   * Writes full sky light into every cell between a column's new and old surface, one queued light
   * array per affected section. Vanilla would instead propagate sky light block by block through
   * the whole crater, which for a radius-275 sphere is tens of millions of light-engine updates and
   * backs up the light executor (and with it, chunk loading) for minutes. Cells opened below the new
   * surface, such as an overhung cavity, keep their previous light; the engine fixes them up the next
   * time a block nearby changes.
   */
  private static int openSky(
      net.minecraft.world.chunk.light.LightingProvider lighting,
      ChunkPos cp,
      int[] openLo,
      int[] openHi,
      boolean edges) {
    int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
    for (int i = 0; i < 256; i++)
      if (openLo[i] <= openHi[i]) {
        min = Math.min(min, openLo[i]);
        max = Math.max(max, openHi[i]);
      }
    if (min > max) return 0;
    var sky = lighting.get(LightType.SKY);
    int written = 0;
    for (int sy = ChunkSectionPos.getSectionCoord(min); sy <= ChunkSectionPos.getSectionCoord(max); sy++) {
      ChunkSectionPos pos = ChunkSectionPos.from(cp, sy);
      ChunkNibbleArray current = sky.getLightSection(pos);
      // No array: the engine reads this section from the one above, which is written here.
      if (current == null) continue;
      int y0 = sy << 4, y1 = y0 + 15;
      boolean full = true;
      for (int i = 0; i < 256 && full; i++) full = openLo[i] <= y0 && openHi[i] >= y1;
      ChunkNibbleArray next;
      if (full) {
        byte[] bytes = new byte[2048];
        java.util.Arrays.fill(bytes, (byte) -1);
        next = new ChunkNibbleArray(bytes);
      } else {
        next = current.copy();
        boolean changed = false;
        for (int i = 0; i < 256; i++) {
          int from = Math.max(openLo[i], y0), to = Math.min(openHi[i], y1);
          for (int y = from; y <= to; y++)
            if (next.get(i & 15, y & 15, i >> 4) != 15) {
              next.set(i & 15, y & 15, i >> 4, 15);
              changed = true;
            }
        }
        if (!changed) continue;
      }
      // Partially lit sections sit on the crater wall: unless the caller opts out, let the engine
      // spread light across their borders. Fully lit interior sections need no edge pass.
      lighting.enqueueSectionData(LightType.SKY, pos, next, full || !edges);
      written++;
    }
    return written;
  }

  /** Sends a fresh copy of the chunk to every player already watching it. */
  public static void resend(ServerWorld world, WorldChunk chunk) {
    List<ServerPlayerEntity> players =
        world
            .getChunkManager()
            .threadedAnvilChunkStorage
            .getPlayersWatchingChunk(chunk.getPos(), false);
    if (players.isEmpty()) return;
    ChunkDataS2CPacket packet =
        new ChunkDataS2CPacket(chunk, world.getLightingProvider(), null, null, true);
    for (ServerPlayerEntity player : players) player.networkHandler.sendPacket(packet);
  }

  private static boolean unbreakable(ServerWorld world, BlockPos pos, BlockState state) {
    return state.getHardness(world, pos) < 0;
  }

  private static void collect(
      ChunkSection section,
      int x0,
      int y0,
      int z0,
      Predicate<BlockState> predicate,
      List<BlockPos> into) {
    for (int y = 0; y < 16; y++)
      for (int z = 0; z < 16; z++)
        for (int x = 0; x < 16; x++)
          if (predicate.test(section.getBlockState(x, y, z)))
            into.add(new BlockPos(x0 + x, y0 + y, z0 + z));
  }

  /** Height above the first blocking state at or below {@code y}, skipping empty sections. */
  private static int scanDown(
      ServerWorld world, ChunkSection[] sections, int lx, int y, int lz, Predicate<BlockState> test) {
    int bottom = world.getBottomY();
    while (y >= bottom) {
      int index = world.getSectionIndex(y);
      ChunkSection section = sections[index];
      if (section == null || section.isEmpty()) {
        y = (world.sectionIndexToCoord(index) << 4) - 1;
        continue;
      }
      if (test.test(section.getBlockState(lx, y & 15, lz))) return y + 1;
      y--;
    }
    return bottom;
  }
}

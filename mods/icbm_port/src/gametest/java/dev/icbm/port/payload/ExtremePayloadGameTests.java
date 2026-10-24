package dev.icbm.port.payload;

import dev.icbm.port.simulation.*;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.math.*;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.PalettedContainer;
import net.minecraft.world.chunk.WorldChunk;

/**
 * Antimatter and red matter at scale. Every test works on its own force-loaded, stone-filled
 * region thousands of blocks from the GameTest structures, in its own batch.
 */
public final class ExtremePayloadGameTests implements FabricGameTest {
  private static void check(TestContext c, boolean value, String message) {
    if (!value) c.throwGameTestException(message);
  }

  /** Force-loads every chunk within {@code reach} chunks of the column containing {@code x, z}. */
  static List<ChunkPos> forceLoad(ServerWorld world, double x, double z, int reach) {
    int cx = (int) Math.floor(x) >> 4, cz = (int) Math.floor(z) >> 4;
    List<ChunkPos> chunks = new ArrayList<>();
    for (int dz = -reach; dz <= reach; dz++)
      for (int dx = -reach; dx <= reach; dx++) {
        world.setChunkForced(cx + dx, cz + dz, true);
        chunks.add(new ChunkPos(cx + dx, cz + dz));
      }
    return chunks;
  }

  static void release(ServerWorld world, List<ChunkPos> chunks) {
    for (ChunkPos pos : chunks) world.setChunkForced(pos.x, pos.z, false);
  }

  /**
   * Replaces whole sections with stone, with scattered ores so palettes hold several states like
   * real terrain, and a bedrock floor when the bottom section of the world is included.
   */
  static void fillStone(ServerWorld world, List<ChunkPos> chunks, int fromSection, int toSection) {
    var random = net.minecraft.util.math.random.Random.create(42);
    for (ChunkPos pos : chunks) {
      WorldChunk chunk = world.getChunk(pos.x, pos.z);
      ChunkSection[] sections = chunk.getSectionArray();
      for (int sy = fromSection; sy <= toSection; sy++) {
        int index = world.sectionCoordToIndex(sy);
        if (index < 0 || index >= sections.length) continue;
        ChunkSection section =
            new ChunkSection(
                sy,
                new PalettedContainer<>(
                    Block.STATE_IDS,
                    Blocks.STONE.getDefaultState(),
                    PalettedContainer.PaletteProvider.BLOCK_STATE),
                sections[index].getBiomeContainer());
        for (int i = 0; i < 48; i++)
          section.setBlockState(
              random.nextInt(16),
              random.nextInt(16),
              random.nextInt(16),
              (i % 3 == 0 ? Blocks.IRON_ORE : i % 3 == 1 ? Blocks.COAL_ORE : Blocks.GRAVEL)
                  .getDefaultState(),
              false);
        if (sy << 4 == world.getBottomY())
          for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++)
              section.setBlockState(x, 0, z, Blocks.BEDROCK.getDefaultState(), false);
        sections[index] = section;
        var lighting = world.getChunkManager().getLightingProvider();
        lighting.setSectionStatus(ChunkSectionPos.from(pos, sy), false);
        // Generated terrain is dark inside solid stone; without this the old open-air sky light
        // stays in the arrays, and the first nearby light update floods through all of it.
        lighting.enqueueSectionData(
            net.minecraft.world.LightType.SKY,
            ChunkSectionPos.from(pos, sy),
            new net.minecraft.world.chunk.ChunkNibbleArray(new byte[2048]),
            true);
      }
      Heightmap.populateHeightmaps(chunk, EnumSet.allOf(Heightmap.Type.class));
      chunk.setNeedsSaving(true);
    }
  }

  @GameTest(
      templateName = FabricGameTest.EMPTY_STRUCTURE,
      tickLimit = 100,
      batchId = "icbmExtremeAntimatter")
  public void antimatterErasesTheWholeSphereAtOnce(TestContext c) {
    ServerWorld world = c.getWorld();
    int radius = 40;
    Vec3d center = new Vec3d(4104.5, .5, 4104.5);
    BlockPos origin = new BlockPos(center);
    List<ChunkPos> chunks = forceLoad(world, center.x, center.z, (radius >> 4) + 2);
    fillStone(world, chunks, -4, 3);
    BlockPos shield = origin.add(6, 0, 0), chest = origin.add(0, 12, 3);
    world.setBlockState(shield, Blocks.BEDROCK.getDefaultState(), 2);
    world.setBlockState(chest, Blocks.CHEST.getDefaultState(), 2);
    ((ChestBlockEntity) world.getBlockEntity(chest)).setStack(0, new ItemStack(Items.DIAMOND, 5));
    check(
        c,
        Payloads.detonateScaled(world, center, PayloadType.ANTIMATTER, null, radius),
        "Antimatter detonation must be accepted");
    long detonationTick = world.getTime();
    // Instant means no visible sweep: the whole sphere must already be gone, or at worst within
    // a handful of ticks on a very slow machine.
    c.runAtEveryTick(
        () -> {
          String missing = firstMismatch(world, origin, radius, radius + 6, 2);
          if (missing != null) {
            check(c, world.getTime() - detonationTick < 5, "Sphere still incomplete: " + missing);
            return;
          }
          try {
            check(c, world.getBlockState(shield).isOf(Blocks.BEDROCK), "Bedrock must survive");
            check(
                c,
                world.getBlockState(chest).isAir() && world.getBlockEntity(chest) == null,
                "Block entities inside the sphere must be deleted");
            check(
                c,
                world
                    .getEntitiesByClass(
                        ItemEntity.class, new Box(origin).expand(radius + 4), e -> true)
                    .isEmpty(),
                "Antimatter must not drop items");
            check(
                c,
                !world.getBlockState(origin.add(radius + 1, 0, 0)).isAir(),
                "Terrain just outside the radius must survive");
            check(
                c,
                world.getTime() - detonationTick <= 1,
                "A " + radius + "-block antimatter sphere must clear in the detonation tick");
          } finally {
            release(world, chunks);
          }
          c.complete();
        });
  }

  /**
   * Returns a description of the first lattice point whose state disagrees with a solid sphere of
   * {@code radius} around {@code origin}'s centre, sampling a cube of half-width {@code extent}.
   * Bedrock is ignored, as it is unbreakable.
   */
  static String firstMismatch(ServerWorld world, BlockPos origin, double radius, int extent, int step) {
    Vec3d center = Vec3d.ofCenter(origin);
    BlockPos.Mutable pos = new BlockPos.Mutable();
    for (int y = -extent; y <= extent; y += step)
      for (int z = -extent; z <= extent; z += step)
        for (int x = -extent; x <= extent; x += step) {
          pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
          if (world.isOutOfHeightLimit(pos)) continue;
          BlockState state = world.getBlockState(pos);
          if (state.isOf(Blocks.BEDROCK)) continue;
          boolean inside = SphereEraser.inside(center, radius, pos);
          if (inside != state.isAir())
            return (inside ? "solid inside at " : "air outside at ")
                + x + "," + y + "," + z + " (" + state + ")";
        }
    return null;
  }

  @GameTest(
      templateName = FabricGameTest.EMPTY_STRUCTURE,
      tickLimit = 200,
      batchId = "icbmExtremeRedmatterShape")
  public void redmatterHorizonIsASphere(TestContext c) {
    ServerWorld world = c.getWorld();
    int radius = 12;
    BlockPos origin = new BlockPos(4616, 0, 4104);
    Vec3d center = Vec3d.ofCenter(origin);
    List<ChunkPos> chunks = forceLoad(world, center.x, center.z, 2);
    fillStone(world, chunks, -2, 1);
    RedmatterEntity core = new RedmatterEntity(SimulationContent.REDMATTER, world);
    core.refreshPositionAndAngles(center.x, center.y, center.z, 0, 0);
    core.setHorizonLimit(radius);
    check(c, world.spawnEntity(core), "Core must spawn");
    c.runAtEveryTick(
        () -> {
          if (core.horizonRadius() < radius) return;
          try {
            // Every block within the radius is gone and every block beyond it remains: a true
            // sphere, so its footprint is a circle rather than a square.
            String mismatch = firstMismatch(world, origin, radius, radius + 2, 1);
            check(c, mismatch == null, "Red matter horizon is not a sphere: " + mismatch);
            for (int sx = -1; sx <= 1; sx += 2)
              for (int sy = -1; sy <= 1; sy += 2)
                for (int sz = -1; sz <= 1; sz += 2)
                  check(
                      c,
                      !world.getBlockState(origin.add(sx * radius, sy * radius, sz * radius)).isAir(),
                      "Corners of the bounding cube must survive");
            check(
                c,
                !world.getBlockState(origin.add(9, 0, 9)).isAir()
                    && world.getBlockState(origin.add(radius, 0, 0)).isAir()
                    && world.getBlockState(origin.add(0, 0, -radius)).isAir()
                    && world.getBlockState(origin.add(8, 0, 8)).isAir(),
                "The horizontal footprint must be circular");
            check(c, core.isAlive(), "The core must persist after reaching its radius");
          } finally {
            core.discard();
            Payloads.removeNear(world, center, radius + 8);
            release(world, chunks);
          }
          c.complete();
        });
  }

  /** A survival player that is not connected, as {@link TestContext#createMockPlayer} is creative. */
  static PlayerEntity survivalPlayer(ServerWorld world) {
    return new PlayerEntity(
        world, BlockPos.ORIGIN, 0, new GameProfile(UUID.randomUUID(), "test-survivor"), null) {
      @Override
      public boolean isSpectator() {
        return false;
      }

      @Override
      public boolean isCreative() {
        return false;
      }
    };
  }

  private static RedmatterEntity fieldCore(ServerWorld world, Vec3d at) {
    RedmatterEntity core = new RedmatterEntity(SimulationContent.REDMATTER, world);
    core.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
    // No terrain growth: these tests are about the gravitational field and the kill.
    core.setHorizonLimit(0);
    world.spawnEntity(core);
    return core;
  }

  private static <T extends Entity> T place(ServerWorld world, T entity, Vec3d at) {
    entity.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
    world.spawnEntity(entity);
    return entity;
  }

  @GameTest(
      templateName = FabricGameTest.EMPTY_STRUCTURE,
      tickLimit = 300,
      batchId = "icbmExtremeRedmatterKill")
  public void redmatterPullsInAndKillsMobsAndPlayers(TestContext c) {
    ServerWorld world = c.getWorld();
    Vec3d base = new Vec3d(5128.5, 0, 4104.5);
    List<ChunkPos> chunks = forceLoad(world, base.x, base.z, 2);
    int ground = world.getTopY(Heightmap.Type.MOTION_BLOCKING, (int) base.x, (int) base.z);
    RedmatterEntity core = fieldCore(world, new Vec3d(base.x, ground + 2.5, base.z));
    LivingEntity pig = place(world, EntityType.PIG.create(world), new Vec3d(base.x + 9, ground, base.z));
    PlayerEntity player = place(world, survivalPlayer(world), new Vec3d(base.x - 7, ground, base.z + 2));
    java.util.Map<LivingEntity, net.minecraft.entity.damage.DamageSource> killedBy =
        new java.util.IdentityHashMap<>();
    c.runAtEveryTick(
        () -> {
          // Vanilla forgets the recent damage source 40 ticks later, so record it at death.
          for (LivingEntity victim : List.of(pig, player))
            if (victim.isDead() && !killedBy.containsKey(victim))
              killedBy.put(victim, victim.getRecentDamageSource());
          if (killedBy.size() < 2) return;
          try {
            for (LivingEntity victim : List.of(pig, player)) {
              var source = killedBy.get(victim);
              check(
                  c,
                  source instanceof RedmatterDamage,
                  victim.getName().getString() + " must die to red matter damage");
              var message = source.getDeathMessage(victim);
              check(
                  c,
                  message.getContent() instanceof TranslatableTextContent text
                      && text.getKey().equals("death.attack.icbm_port.redmatter"),
                  "Death message must read 'was consumed by red matter'");
            }
          } finally {
            core.discard();
            pig.discard();
            player.discard();
            release(world, chunks);
          }
          c.complete();
        });
  }

  @GameTest(
      templateName = FabricGameTest.EMPTY_STRUCTURE,
      tickLimit = 120,
      batchId = "icbmExtremeRedmatterCreative")
  public void redmatterSparesCreativePlayers(TestContext c) {
    ServerWorld world = c.getWorld();
    Vec3d base = new Vec3d(5640.5, 0, 4104.5);
    List<ChunkPos> chunks = forceLoad(world, base.x, base.z, 2);
    int ground = world.getTopY(Heightmap.Type.MOTION_BLOCKING, (int) base.x, (int) base.z);
    RedmatterEntity core = fieldCore(world, new Vec3d(base.x, ground + .5, base.z));
    // One creative player inside the kill range, another out in the field.
    PlayerEntity inside = place(world, c.createMockPlayer(), new Vec3d(base.x, ground, base.z));
    PlayerEntity near = place(world, c.createMockPlayer(), new Vec3d(base.x + 5, ground, base.z));
    check(c, inside.isCreative() && !inside.getAbilities().invulnerable, "Mock must be creative only");
    PlayerEntity spectator = survivalPlayer(world);
    check(
        c,
        !RedmatterEntity.immune(spectator)
            && RedmatterEntity.immune(inside)
            && RedmatterEntity.immune(
                new PlayerEntity(world, BlockPos.ORIGIN, 0, new GameProfile(UUID.randomUUID(), "spectator"), null) {
                  @Override
                  public boolean isSpectator() {
                    return true;
                  }

                  @Override
                  public boolean isCreative() {
                    return false;
                  }
                }),
        "Creative and spectator players are immune; survival players are not");
    Vec3d nearStart = near.getPos();
    c.runAtTick(
        80,
        () -> {
          try {
            check(c, inside.isAlive() && inside.getHealth() == inside.getMaxHealth(), "Creative player at the core must live");
            check(c, near.isAlive(), "Creative player in the field must live");
            double moved =
                Math.hypot(near.getX() - nearStart.x, near.getZ() - nearStart.z);
            check(c, moved < .05, "Creative players must not be pulled (moved " + moved + ")");
            check(c, core.isAlive(), "The core must still be active");
          } finally {
            core.discard();
            inside.discard();
            near.discard();
            release(world, chunks);
          }
          c.complete();
        });
  }

  @GameTest(
      templateName = FabricGameTest.EMPTY_STRUCTURE,
      tickLimit = 200,
      batchId = "icbmExtremeRedmatterMerge")
  public void mergedCoreFinishesItsDeferredCrater(TestContext c) {
    ServerWorld world = c.getWorld();
    Vec3d base = new Vec3d(6152.5, 0, 4104.5);
    // Only the cores' own chunk is loaded; the chunk six away stays unloaded until the end.
    List<ChunkPos> chunks = forceLoad(world, base.x, base.z, 0);
    int ground = world.getTopY(Heightmap.Type.MOTION_BLOCKING, (int) base.x, (int) base.z);
    check(c, !world.getBlockState(new BlockPos(base.x, ground - 1, base.z)).isAir(), "Ground expected");
    Vec3d at = new Vec3d(base.x, ground + .5, base.z);
    ChunkPos far = new ChunkPos(new BlockPos(base.x + 100, 0, base.z));
    BlockPos target = new BlockPos(base.x + 100, ground - 1, base.z);
    check(c, world.getChunkManager().getWorldChunk(far.x, far.z) == null, "Far chunk must start unloaded");
    // The survivor spawns first, so it has the lower id and absorbs the other.
    RedmatterEntity survivor = fieldCore(world, at);
    RedmatterEntity absorbed = new RedmatterEntity(SimulationContent.REDMATTER, world);
    absorbed.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
    // A full-grown horizon of 120 whose sweep passed the far chunk while it was unloaded.
    net.minecraft.nbt.NbtCompound saved = new net.minecraft.nbt.NbtCompound();
    absorbed.writeNbt(saved);
    saved.putInt("horizon", 120);
    saved.putInt("maxHorizon", 120);
    saved.putDouble("anchorX", at.x);
    saved.putDouble("anchorY", at.y);
    saved.putDouble("anchorZ", at.z);
    saved.putLongArray("deferred", new long[] {far.toLong()});
    absorbed.readNbt(saved);
    check(c, world.spawnEntity(absorbed), "Second core must spawn");
    List<ChunkPos> loaded = new ArrayList<>();
    long[] mergedAt = {-1};
    c.runAtEveryTick(
        () -> {
          if (mergedAt[0] < 0) {
            if (!absorbed.isRemoved()) return;
            check(c, survivor.isAlive(), "The older core must absorb the newer one");
            // Stop the survivor before its own growing horizon could reach the far chunk.
            survivor.discard();
            mergedAt[0] = world.getTime();
            loaded.addAll(forceLoad(world, target.getX(), target.getZ(), 0));
            return;
          }
          if (world.getTime() - mergedAt[0] < 5) return;
          try {
            check(
                c,
                world.getBlockState(target).isAir(),
                "The absorbed core's deferred crater must be erased once its chunk loads");
          } finally {
            release(world, loaded);
            release(world, chunks);
          }
          c.complete();
        });
  }

  @GameTest(
      templateName = FabricGameTest.EMPTY_STRUCTURE,
      tickLimit = 40,
      batchId = "icbmExtremeLegacyAntimatter")
  public void legacyAntimatterSweepsWaitForScarRoom(TestContext c) {
    ServerWorld world = c.getWorld();
    Vec3d base = new Vec3d(6664.5, 200, 4104.5);
    List<ChunkPos> chunks = forceLoad(world, base.x, base.z, 2);
    long budget = Scar.tickBudgetNanos;
    // With no budget each world tick advances a single scar by a single chunk, so scars stay busy.
    Scar.tickBudgetNanos = 0;
    try {
      // An older save: more unfinished antimatter cube sweeps than there are scar slots.
      int saved = Payloads.QueueState.MAX_SCARS + 6;
      net.minecraft.nbt.NbtList jobs = new net.minecraft.nbt.NbtList();
      for (int i = 0; i < saved; i++) {
        net.minecraft.nbt.NbtCompound job = new net.minecraft.nbt.NbtCompound();
        job.putString("type", PayloadType.ANTIMATTER.name());
        job.putDouble("x", base.x);
        job.putDouble("y", base.y);
        job.putDouble("z", base.z);
        job.putInt("radius", 20);
        jobs.add(job);
      }
      net.minecraft.nbt.NbtCompound nbt = new net.minecraft.nbt.NbtCompound();
      nbt.put("jobs", jobs);
      Payloads.QueueState state = Payloads.QueueState.read(nbt);
      state.tick(world);
      check(
          c,
          state.scars.size() == Payloads.QueueState.MAX_SCARS && state.legacy.size() == 6,
          "Sweeps beyond the scar limit must wait, not vanish (scars "
              + state.scars.size()
              + ", waiting "
              + state.legacy.size()
              + ")");
      Payloads.QueueState reloaded =
          Payloads.QueueState.read(state.writeNbt(new net.minecraft.nbt.NbtCompound()));
      reloaded.tick(world);
      check(
          c,
          reloaded.legacy.size() + reloaded.scars.size() == saved,
          "Waiting sweeps must survive a save");
      int converted = 0;
      for (int i = 0; i < 4000 && !(state.legacy.isEmpty() && state.scars.isEmpty()); i++) {
        int waiting = state.legacy.size();
        state.tick(world);
        converted += waiting - state.legacy.size();
      }
      check(c, state.legacy.isEmpty() && state.scars.isEmpty(), "Every sweep must finish");
      check(c, converted == 6, "The waiting sweeps must become scars as slots free up");
    } finally {
      Scar.tickBudgetNanos = budget;
      release(world, chunks);
    }
    c.complete();
  }
}

package dev.icbm.port.payload;

import dev.icbm.port.api.*;
import dev.icbm.port.exposure.ExposureContent;
import dev.icbm.port.simulation.*;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.*;
import net.minecraft.entity.mob.*;
import net.minecraft.entity.passive.*;
import net.minecraft.nbt.*;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import org.jetbrains.annotations.Nullable;

/** Server-side adaptations of the MIT-licensed ICBM blast families. No worker accesses worlds. */
public final class Payloads {
  private static boolean initialized;

  private Payloads() {}

  public static void initialize() {
    if (initialized) return;
    initialized = true;
    SimulationContent.register();
    try {
      PayloadConfig.initialize(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir());
    } catch (java.io.IOException e) {
      org.slf4j.LoggerFactory.getLogger("icbm_port")
          .error("Payload configuration could not be loaded; retaining working defaults", e);
    }
    net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register(
        (dispatcher, access, environment) ->
            dispatcher.register(
                net.minecraft.server.command.CommandManager.literal("icbm_payloads")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(
                        net.minecraft.server.command.CommandManager.literal("reload")
                            .executes(
                                context -> {
                                  try {
                                    PayloadConfig.reload();
                                    context
                                        .getSource()
                                        .sendFeedback(
                                            net.minecraft.text.Text.literal(
                                                "ICBM payload configuration reloaded"),
                                            true);
                                    return 1;
                                  } catch (java.io.IOException e) {
                                    context
                                        .getSource()
                                        .sendError(net.minecraft.text.Text.literal(e.getMessage()));
                                    return 0;
                                  }
                                }))));
    ServerTickEvents.END_WORLD_TICK.register(world -> state(world).tick(world));
    net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.CHUNK_LOAD.register(
        (world, chunk) -> state(world).chunkLoaded(world, chunk.getPos().toLong()));
  }

  public static void detonate(
      World world, Vec3d center, PayloadType type, @Nullable Entity source) {
    if (world instanceof ServerWorld server)
      detonateScaled(server, center, type, source, radius(type));
  }

  public static boolean detonateScaled(
      ServerWorld server, Vec3d center, PayloadType type, @Nullable Entity source, double size) {
    if (!Double.isFinite(center.x + center.y + center.z)
        || !Double.isFinite(size)
        || size < 0
        || size > type.maxRadius()
        || !loaded(server, new BlockPos(center))) return false;
    ActionContext current = IcbmApi.currentContext();
    net.minecraft.util.Identifier action =
        new net.minecraft.util.Identifier("icbm_port", type.id());
    if (current != null
        && (current.world() != server
            || !current.action().equals(action)
            || !current.position().equals(center))) current = null;
    ActionContext context =
        current == null
            ? new ActionContext(
                server,
                center,
                new net.minecraft.util.Identifier("icbm_port", type.id()),
                size,
                ActionSource.of(source))
            : current;
    if (!IcbmApi.fireBefore(context)) {
      IcbmApi.fireAfter(context, false);
      return false;
    }
    boolean accepted =
        dispatch(server, center, type, source, (int) Math.ceil(size), context.source());
    IcbmApi.fireAfter(context, accepted);
    if (accepted) dev.icbm.port.effects.BlastEffects.broadcast(server, center, type, size);
    return accepted;
  }

  private static boolean dispatch(
      ServerWorld server,
      Vec3d center,
      PayloadType type,
      Entity source,
      int size,
      ActionSource attribution) {
    PayloadConfig.Settings settings = PayloadConfig.forType(type);
    if (!settings.enabled()) return false;
    if (type == PayloadType.ANTIMATTER) {
      // Deliberate departure: the whole sphere is erased through direct section writes, in the
      // detonation tick when it fits the scar budget, instead of a cube sweep over minutes.
      if (!state(server).startScar(server, center, size)) return false;
      boolean neutralized = false;
      for (Entity entity :
          server.getOtherEntities(
              null, new Box(center, center).expand(size), e -> e instanceof RedmatterEntity)) {
        if (entity.squaredDistanceTo(center) <= (double) size * size) {
          ((RedmatterEntity) entity).neutralize(server);
          neutralized = true;
        }
      }
      // Upstream damageOnRedmatterKill=false: neutralizing a core consumes the
      // entity-damage pulse. Terrain work still follows the accepted blast.
      if (!neutralized)
        for (Entity e : nearby(server, center, size))
          e.damage(DamageSource.OUT_OF_WORLD, (float) settings.entityDamage());
      antimatterFlash(server, center, size);
      return true;
    }
    switch (type) {
      case CONDENSED,
          REPULSIVE,
          ATTRACTIVE,
          INCENDIARY,
          SONIC,
          BREACHING,
          THERMOBARIC,
          NUCLEAR,
          EXOTHERMIC,
          ENDOTHERMIC,
          ANTI_GRAVITATIONAL,
          ENDER -> {
        // Staged upstream blast ports: see BlastWork and the per-family classes.
        BlastWork work = BlastWork.create(server, center, type, size, source);
        if (work == null) return false;
        Job job = new Job(type, center, size);
        job.source = attribution;
        job.work = work;
        return state(server).enqueue(job, false);
      }
      case MUTATION -> mutate(server, center, size);
      case EMP -> {
        return EmpBlast.detonate(server, center, size, source, attribution);
      }
      case SHRAPNEL, ANVIL, FRAGMENTATION ->
          FragmentBlast.spawn(server, center, type, settings, size, attribution);
      case SMOKE -> {
        return dev.icbm.port.entity.SmokeEntity.spawn(server, center, settings.durationTicks());
      }
      case REDMATTER -> {
        RedmatterEntity singularity = new RedmatterEntity(SimulationContent.REDMATTER, server);
        singularity.refreshPositionAndAngles(center.x, center.y + .5, center.z, 0, 0);
        singularity.setOwner(source);
        singularity.setHorizonLimit(size);
        singularity.setVelocity(source == null ? Vec3d.ZERO : source.getVelocity().multiply(.1));
        return server.spawnEntity(singularity);
      }
      case HYPERSONIC, MISSILEMODULE -> {
        /* Upstream reserved no-op slots. */
      }
      default -> {
        Job job = new Job(type, center, size);
        job.source = attribution;
        return state(server).enqueue(job, false);
      }
    }
    return true;
  }

  public static int removeNear(ServerWorld world, Vec3d center, double radius) {
    if (!Double.isFinite(radius) || radius < 0 || radius > 10000) return 0;
    QueueState queue = state(world);
    int before = queue.jobs.size() + queue.pending.size() + queue.legacy.size() + queue.scars.size();
    queue.jobs.removeIf(j -> j.center.squaredDistanceTo(center) <= radius * radius);
    queue.pending.removeIf(j -> j.center.squaredDistanceTo(center) <= radius * radius);
    queue.legacy.removeIf(j -> j.center.squaredDistanceTo(center) <= radius * radius);
    queue.scars.removeIf(s -> s.center.squaredDistanceTo(center) <= radius * radius);
    queue.markDirty();
    int removed =
        before - queue.jobs.size() - queue.pending.size() - queue.legacy.size() - queue.scars.size();
    for (Entity entity :
        world.getOtherEntities(
            null,
            new Box(center, center).expand(radius),
            e -> e instanceof RedmatterEntity || e instanceof FlyingBlockEntity)) {
      if (entity.squaredDistanceTo(center) > radius * radius) continue;
      if (entity instanceof FlyingBlockEntity block) block.settle();
      else entity.discard();
      removed++;
    }
    return removed;
  }

  public static int queuedCount(ServerWorld world) {
    QueueState queue = state(world);
    return queue.jobs.size()
        + queue.pending.size()
        + queue.legacy.size()
        + (int) queue.scars.stream().filter(Scar::active).count();
  }

  /**
   * Erases the solid sphere in the given chunks, now if loaded or else when each next loads. Red
   * matter uses this for chunks its horizon passed while they were unloaded. Returns false only
   * when the scar list is full of active work, in which case nothing was taken and the caller
   * still owns the chunks.
   */
  public static boolean registerScar(
      ServerWorld world, Vec3d center, int radius, it.unimi.dsi.fastutil.longs.LongCollection chunks) {
    if (chunks.isEmpty() || radius <= 0) return true;
    if (!state(world).hasRoom()) return false;
    Scar scar = new Scar(center, Math.min(PayloadType.MAX_EXTREME_RADIUS, radius));
    scar.reported = true;
    chunks.forEach((long pos) -> {
      if (scar.pending.size() < Scar.MAX_CHUNKS) scar.pending.add(pos);
    });
    return state(world).addScar(scar);
  }

  private static void antimatterFlash(ServerWorld world, Vec3d center, int radius) {
    double reach = radius + 96;
    for (var player : world.getPlayers()) {
      if (player.squaredDistanceTo(center) > reach * reach) continue;
      world.spawnParticles(
          player, ParticleTypes.FLASH, true, center.x, center.y, center.z, 4, 1, 1, 1, 0);
      world.spawnParticles(
          player,
          ParticleTypes.EXPLOSION_EMITTER,
          true,
          center.x,
          center.y,
          center.z,
          12,
          Math.min(radius, 48) * .5,
          Math.min(radius, 48) * .25,
          Math.min(radius, 48) * .5,
          0);
    }
    world.playSound(
        null,
        center.x,
        center.y,
        center.z,
        net.minecraft.sound.SoundEvents.ENTITY_GENERIC_EXPLODE,
        net.minecraft.sound.SoundCategory.BLOCKS,
        Math.max(4, radius / 8f),
        .5f);
  }

  /**
   * Defense integrations register here; avoids a hard dependency on optional technology systems.
   */
  public static final List<java.util.function.BiConsumer<ServerWorld, Vec3d>> EMP_HANDLERS =
      new ArrayList<>();

  private static boolean loaded(ServerWorld w, BlockPos p) {
    return !w.isOutOfHeightLimit(p) && w.getWorldBorder().contains(p) && w.isChunkLoaded(p);
  }

  private static List<Entity> nearby(ServerWorld w, Vec3d p, double r) {
    var list =
        w.getOtherEntities(
            null, new Box(p, p).expand(r), e -> e.isAlive() && e.squaredDistanceTo(p) <= r * r);
    return list.size() > 256 ? list.subList(0, 256) : list;
  }

  private static void force(ServerWorld w, Vec3d p, double r, double strength, boolean lift) {
    for (Entity e : nearby(w, p, r)) {
      if (e instanceof net.minecraft.entity.player.PlayerEntity player && player.isCreative())
        continue;
      Vec3d d = e.getPos().subtract(p);
      double distance = d.length();
      Vec3d velocity =
          distance < .001
              ? new Vec3d(0, .2, 0)
              : d.multiply(strength * Math.max(0, 1 - distance / r) / distance);
      e.addVelocity(velocity.x, lift ? .15 : velocity.y, velocity.z);
      e.velocityModified = true;
    }
  }

  /**
   * Adapted from MIT ICBM-Classic {@code content/blast/BlastMutation}: every mob (upstream {@code
   * EntityLiving}, so not players or armour stands) in the axis-aligned cube of half-size {@code
   * radius} is offered to {@link #applyMutation}.
   */
  static int mutate(ServerWorld world, Vec3d center, double radius) {
    int mutated = 0;
    for (Entity entity :
        BlastSupport.entities(
            world, new Box(center, center).expand(radius), center, e -> e instanceof MobEntity))
      if (applyMutation((LivingEntity) entity)) mutated++;
    return mutated;
  }

  public static boolean applyMutation(LivingEntity original) {
    if (!(original.world instanceof ServerWorld world)) return false;
    LivingEntity replacement =
        original instanceof PigEntity
            ? EntityType.ZOMBIFIED_PIGLIN.create(world)
            : original instanceof VillagerEntity ? EntityType.ZOMBIE_VILLAGER.create(world) : null;
    if (replacement == null) return false;
    replacement.refreshPositionAndAngles(
        original.getX(), original.getY(), original.getZ(), original.getYaw(), original.getPitch());
    if (replacement instanceof ZombieVillagerEntity zombie
        && original instanceof VillagerEntity villager)
      zombie.setVillagerData(villager.getVillagerData());
    if (original.hasCustomName()) {
      replacement.setCustomName(original.getCustomName());
      replacement.setCustomNameVisible(original.isCustomNameVisible());
    }
    if (!world.spawnEntity(replacement)) return false;
    original.discard();
    return true;
  }

  static int radius(PayloadType type) {
    return PayloadConfig.forType(type).radius();
  }

  static QueueState state(ServerWorld world) {
    return world
        .getPersistentStateManager()
        .getOrCreate(QueueState::read, QueueState::new, "icbm_payloads");
  }

  static final class QueueState extends PersistentState {
    /** Scars clearing at once. */
    static final int MAX_SCARS = 64;
    /** Scars stored at once, including dormant ones waiting for unloaded chunks. */
    static final int MAX_STORED_SCARS = 512;
    /** Regular jobs, and separately waiting legacy sweeps, kept (and saved) at once. */
    static final int MAX_JOBS = 256;
    final List<Job> jobs = new ArrayList<>();
    final List<Job> pending = new ArrayList<>();
    final List<Scar> scars = new ArrayList<>();
    /**
     * Antimatter cube sweeps saved by older versions (up to 256 of them), waiting to become scars.
     * They stay here, and are saved in their own list, until {@link #addScar} has room, so none is
     * lost when more than {@link #MAX_SCARS} were pending at upgrade.
     */
    final List<Job> legacy = new ArrayList<>();
    /**
     * Chunks whose load event fired, by world time. The event can fire before the chunk is
     * reachable through getWorldChunk, so scars are only told once it is (see {@link #tick}).
     */
    private final it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap loaded =
        new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();
    int nextJob;

    boolean enqueue(Job job, boolean deferred) {
      if (jobs.size() + pending.size() >= MAX_JOBS) return false;
      (deferred ? pending : jobs).add(job);
      markDirty();
      return true;
    }

    private long budgetTime = Long.MIN_VALUE, budgetDeadline;

    /**
     * One wall-clock deadline per world tick, shared by every scar and every detonation in that
     * tick, so simultaneous antimatter hits cannot multiply the stall.
     */
    private long deadline(ServerWorld world) {
      if (budgetTime != world.getTime()) {
        budgetTime = world.getTime();
        long now = System.nanoTime();
        budgetDeadline =
            Scar.tickBudgetNanos > Long.MAX_VALUE - now ? Long.MAX_VALUE : now + Scar.tickBudgetNanos;
      }
      return budgetDeadline;
    }

    /** Starts an antimatter scar and clears as much of it as the tick budget allows right now. */
    boolean startScar(ServerWorld world, Vec3d center, int radius) {
      if (radius <= 0) return true;
      Scar scar = Scar.of(world, center, radius);
      if (!addScar(scar)) return false;
      scar.tick(world, deadline(world));
      markDirty();
      return true;
    }

    boolean addScar(Scar scar) {
      if (!hasRoom()) return false;
      scars.add(scar);
      markDirty();
      return true;
    }

    /**
     * Accepted crater work is never evicted. New scars are admitted while fewer than {@link
     * #MAX_SCARS} are actively clearing and fewer than {@link #MAX_STORED_SCARS} are stored in all
     * (dormant scars wait for unloaded chunks); otherwise callers keep their work and retry.
     */
    boolean hasRoom() {
      if (scars.size() >= MAX_STORED_SCARS) return false;
      int active = 0;
      for (Scar scar : scars) if (scar.active() && ++active >= MAX_SCARS) return false;
      return true;
    }

    /** Turns waiting legacy antimatter sweeps into scars, oldest first, while scars have room. */
    private void convertLegacy(ServerWorld world) {
      while (!legacy.isEmpty()) {
        Job job = legacy.get(0);
        if (!addScar(Scar.of(world, job.center, job.radius))) return;
        legacy.remove(0);
        markDirty();
      }
    }

    void chunkLoaded(ServerWorld world, long pos) {
      loaded.put(pos, world.getTime());
    }

    /**
     * Routes buffered load events to scars once each chunk is reachable. A chunk that unloads first
     * is forgotten here after a while but stays in its scar's deferred set (saved), and its next
     * load event brings it back.
     */
    private void routeLoadedChunks(ServerWorld world) {
      if (loaded.isEmpty()) return;
      boolean changed = false;
      var iterator = loaded.long2LongEntrySet().fastIterator();
      while (iterator.hasNext()) {
        var entry = iterator.next();
        long pos = entry.getLongKey();
        if (world
                .getChunkManager()
                .getWorldChunk(ChunkPos.getPackedX(pos), ChunkPos.getPackedZ(pos))
            != null) {
          for (Scar scar : scars) changed |= scar.chunkLoaded(pos);
          iterator.remove();
        } else if (world.getTime() - entry.getLongValue() > 200) iterator.remove();
      }
      if (changed) markDirty();
    }

    private void tickScars(ServerWorld world) {
      if (scars.isEmpty()) return;
      long deadline = deadline(world);
      boolean worked = false;
      for (Scar scar : scars) {
        if (!scar.active()) continue;
        if (worked && System.nanoTime() >= deadline) break;
        scar.tick(world, deadline);
        worked = true;
      }
      if (worked) markDirty();
      if (scars.removeIf(s -> !s.active() && !s.dormant())) markDirty();
    }

    static QueueState read(NbtCompound nbt) {
      QueueState state = new QueueState();
      NbtList scarList = nbt.getList("scars", 10);
      for (int i = 0; i < Math.min(scarList.size(), MAX_STORED_SCARS); i++) {
        Scar scar = Scar.read(scarList.getCompound(i));
        if (scar != null) state.scars.add(scar);
      }
      NbtList list = nbt.getList("jobs", 10);
      for (int i = 0; i < Math.min(list.size(), 4096) && state.jobs.size() < MAX_JOBS; i++) {
        Job j = readJob(list.getCompound(i));
        if (j != null) state.enqueue(j, false);
      }
      // Each list has its own limit, matching what each admits, so everything saved comes back.
      NbtList waiting = nbt.getList("legacy", 10);
      for (int i = 0; i < Math.min(waiting.size(), 4096) && state.legacy.size() < MAX_JOBS; i++) {
        Job j = readJob(waiting.getCompound(i));
        if (j != null) state.legacy.add(j);
      }
      return state;
    }

    private static @Nullable Job readJob(NbtCompound n) {
      try {
        Vec3d center = new Vec3d(n.getDouble("x"), n.getDouble("y"), n.getDouble("z"));
        if (!validCoordinate(center.x) || !validCoordinate(center.y) || !validCoordinate(center.z))
          return null;
        int radius = Math.min(80, Math.max(0, n.getInt("radius")));
        Job j = new Job(PayloadType.valueOf(n.getString("type")), center, radius);
        int side = radius * 2 + 1;
        j.cursor = Math.min(side * side * side, Math.max(0, n.getInt("cursor")));
        j.age = Math.min(72000, Math.max(0, n.getInt("age")));
        j.elapsedTicks =
            n.contains("elapsedTicks")
                ? Math.min(72000, Math.max(0, n.getInt("elapsedTicks")))
                : j.age;
        j.suppressEntityDamage = n.getBoolean("suppressEntityDamage");
        j.source = ActionSource.fromNbt(n.getCompound("source"));
        if (n.contains("gasWork", 10))
          j.gasWork = GasWork.read(n.getCompound("gasWork"), new BlockPos(center));
        if (staged(j.type)) {
          // Older saves carried ray progress or a raw ender destination instead; their blast
          // restarts from its saved centre (collection only reads the world).
          NbtCompound work = n.contains("work", 10) ? n.getCompound("work") : new NbtCompound();
          if (!n.contains("work", 10) && n.contains("destinationWorld", 8)) {
            work.putString("destinationWorld", n.getString("destinationWorld"));
            work.putLong("destination", n.getLong("destination"));
          }
          j.work = BlastWork.read(j.type, work, radius);
          if (j.work == null) return null;
        }
        return j;
      } catch (IllegalArgumentException ignored) {
        return null;
      }
    }

    private static boolean validCoordinate(double coordinate) {
      return Double.isFinite(coordinate) && Math.abs(coordinate) <= 30000000;
    }

    private static NbtList writeJobs(List<Job> saved) {
      NbtList list = new NbtList();
      for (Job j : saved) {
        NbtCompound n = new NbtCompound();
        n.putString("type", j.type.name());
        n.putDouble("x", j.center.x);
        n.putDouble("y", j.center.y);
        n.putDouble("z", j.center.z);
        n.putInt("radius", j.radius);
        n.putInt("cursor", j.cursor);
        n.putInt("age", j.age);
        n.putInt("elapsedTicks", j.elapsedTicks);
        n.putBoolean("suppressEntityDamage", j.suppressEntityDamage);
        n.put("source", j.source.toNbt());
        if (j.gasWork != null) n.put("gasWork", j.gasWork.write());
        if (j.work != null) n.put("work", j.work.write());
        list.add(n);
      }
      return list;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
      List<Job> saved = new ArrayList<>(jobs);
      saved.addAll(pending);
      nbt.put("jobs", writeJobs(saved));
      nbt.put("legacy", writeJobs(legacy));
      NbtList scarList = new NbtList();
      for (Scar scar : scars) if (scar.active() || scar.dormant()) scarList.add(scar.write());
      nbt.put("scars", scarList);
      return nbt;
    }

    void tick(ServerWorld w) {
      routeLoadedChunks(w);
      jobs.addAll(pending);
      pending.clear();
      // Worlds saved by older versions may hold antimatter cube sweeps; each becomes a scar once
      // there is room. Convert into free slots first, then again into slots this tick's scars free.
      if (jobs.removeIf(
          j -> j.type == PayloadType.ANTIMATTER && legacy.size() < MAX_JOBS && legacy.add(j)))
        markDirty();
      convertLegacy(w);
      tickScars(w);
      convertLegacy(w);
      if (jobs.isEmpty()) return;
      for (Job job : jobs) job.elapsedTicks = Math.min(72000, job.elapsedTicks + 1);
      int served = 0, slots = Math.min(8, jobs.size());
      int index = Math.floorMod(nextJob, jobs.size());
      while (served < slots && !jobs.isEmpty()) {
        if (index >= jobs.size()) index = 0;
        Job job = jobs.get(index);
        if (job.tick(w, 512)) jobs.remove(index);
        else index++;
        served++;
      }
      jobs.addAll(pending);
      pending.clear();
      nextJob = jobs.isEmpty() ? 0 : index % jobs.size();
      markDirty();
    }
  }

  /** Families whose queued work is a {@link BlastWork} (saved under {@code "work"}). */
  static boolean staged(PayloadType type) {
    return switch (type) {
      case CONDENSED,
          REPULSIVE,
          ATTRACTIVE,
          INCENDIARY,
          SONIC,
          BREACHING,
          THERMOBARIC,
          NUCLEAR,
          EXOTHERMIC,
          ENDOTHERMIC,
          ANTI_GRAVITATIONAL,
          ENDER -> true;
      default -> false;
    };
  }

  static final class Job {
    final PayloadType type;
    final Vec3d center;
    final int radius;
    int cursor, age, elapsedTicks;
    boolean suppressEntityDamage;
    GasWork gasWork;
    BlastWork work;
    ActionSource source = ActionSource.of(null);

    Job(PayloadType type, Vec3d center, int radius) {
      this.type = type;
      this.center = center;
      this.radius = radius;
    }

    boolean tick(ServerWorld w, int budget) {
      PayloadConfig.Settings settings = PayloadConfig.forType(type);
      if (!settings.enabled()) return true;
      boolean timed = type == PayloadType.REDMATTER;
      if (timed && elapsedTicks >= settings.durationTicks()) return true;
      age++;
      if (work != null) return work.tick(w, this, budget);
      if (type == PayloadType.SMOKE) {
        // Smoke is a SmokeEntity now; a smoke job saved by an older version becomes one.
        dev.icbm.port.entity.SmokeEntity.spawn(
            w, center, Math.max(0, settings.durationTicks() - elapsedTicks));
        return true;
      }
      if (GasWork.handles(type)) {
        if (gasWork == null) gasWork = new GasWork(new BlockPos(center));
        return gasWork.tick(
            w, type, center, radius, settings.durationTicks(), elapsedTicks, budget * 2);
      }
      if (type == PayloadType.REDMATTER) {
        // Legacy queued red matter (pre-entity saves) keeps its old pull.
        force(w, center, radius, -2, false);
        for (Entity e : nearby(w, center, 1.5))
          e.damage(DamageSource.OUT_OF_WORLD, (float) settings.entityDamage());
        if (age % 20 == 1)
          w.spawnParticles(ParticleTypes.PORTAL, center.x, center.y, center.z, 40, 2, 2, 2, .03);
      }
      int side = radius * 2 + 1, total = side * side * side;
      for (int i = 0; i < budget && cursor < total; i++, cursor++) {
        int x = cursor % side - radius,
            y = (cursor / side) % side - radius,
            z = cursor / (side * side) - radius;
        if (x * x + y * y + z * z > radius * radius) continue;
        BlockPos pos = new BlockPos(center).add(x, y, z);
        if (!loaded(w, pos)) continue;
        BlockState block = w.getBlockState(pos);
        if (block.getHardness(w, pos) < 0) continue;
        edit(w, pos, block);
      }
      if (type == PayloadType.REDMATTER) {
        if (cursor >= total) cursor = 0;
        return elapsedTicks >= settings.durationTicks();
      }
      return cursor >= total;
    }

    void edit(ServerWorld w, BlockPos p, BlockState s) {
      switch (type) {
        // Adapted from MIT ICBM-Classic content/blast/BlastRot: the radioactive block swaps.
        case ROT -> ExposureContent.replace(w, p);
        case REDMATTER -> {
          if (!s.isAir() && w.random.nextInt(8) == 0) w.removeBlock(p, false);
        }
        default -> {}
      }
    }
  }
}

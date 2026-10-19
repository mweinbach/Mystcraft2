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
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.nbt.*;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.explosion.Explosion;
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
        || size > 80
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
      Job job = new Job(type, center, size);
      job.source = attribution;
      if (!state(server).enqueue(job, false)) return false;
      for (Entity entity :
          server.getOtherEntities(
              null, new Box(center, center).expand(size), e -> e instanceof RedmatterEntity)) {
        if (entity.squaredDistanceTo(center) <= size * size) {
          entity.discard();
          job.suppressEntityDamage = true;
        }
      }
      // Upstream damageOnRedmatterKill=false: neutralizing a core consumes the
      // entity-damage pulse. Terrain work still follows the accepted blast.
      return true;
    }
    switch (type) {
      case CONDENSED -> {
        Job job = new Job(type, center, size);
        job.source = attribution;
        job.rayWork = new RayWork(true, size, settings.energy());
        return state(server).enqueue(job, false);
      }
      case SHRAPNEL ->
          arrows(server, center, source, settings.fragments(), settings.entityDamage(), true);
      case ANVIL -> {
        for (int i = 0; i < settings.fragments(); i++) {
          BlockPos pos =
              new BlockPos(center)
                  .add(
                      server.random.nextInt(9) - 4,
                      3 + server.random.nextInt(5),
                      server.random.nextInt(9) - 4);
          if (!loaded(server, pos) || !server.getBlockState(pos).isAir()) continue;
          FallingBlockEntity anvil =
              FallingBlockEntity.spawnFromBlock(server, pos, Blocks.ANVIL.getDefaultState());
          anvil.setHurtEntities(
              (float) settings.entityDamage(),
              (int) Math.min(Integer.MAX_VALUE, settings.entityDamage() * 2));
          anvil.dropItem = false;
          anvil.setVelocity(
              (server.random.nextDouble() - .5) * 1.5, .6, (server.random.nextDouble() - .5) * 1.5);
        }
      }
      case FRAGMENTATION -> {
        for (int i = 0; i < settings.fragments(); i++) {
          Vec3d direction =
              new Vec3d(
                      server.random.nextDouble() - .5,
                      server.random.nextDouble() - .3,
                      server.random.nextDouble() - .5)
                  .normalize();
          Vec3d end = center.add(direction.multiply(18));
          var hit =
              server.raycast(
                  new RaycastContext(
                      center,
                      end,
                      RaycastContext.ShapeType.COLLIDER,
                      RaycastContext.FluidHandling.NONE,
                      source));
          Vec3d target = hit.getType() == HitResult.Type.MISS ? end : hit.getPos();
          if (loaded(server, new BlockPos(target)))
            explosion(server, target, (float) Math.min(16, settings.energy()), source, false);
        }
        arrows(server, center, source, settings.fragments(), settings.entityDamage(), false);
      }
      case REPULSIVE, ATTRACTIVE -> {
        force(server, center, size * 4, type == PayloadType.ATTRACTIVE ? -3 : 3, false);
        Job job = new Job(type, center, size);
        job.source = attribution;
        job.rayWork = new RayWork(true, size, settings.energy());
        return state(server).enqueue(job, false);
      }
      case BREACHING -> breach(server, center, source);
      case MUTATION -> mutate(server, center, size);
      case ENDER -> {
        if (source instanceof dev.icbm.port.entity.MissileEntity missile
            && missile.getTargetPosition() != null) {
          ServerWorld destination = server.getServer().getWorld(missile.getTargetDimension());
          if (destination == null) return false;
          Job job = new Job(type, center, size);
          job.source = attribution;
          job.destinationWorld = destination.getRegistryKey().getValue().toString();
          job.destination = missile.getTargetPosition();
          return state(server).enqueue(job, false);
        }
        teleport(server, center);
      }
      case EMP -> {
        for (var handler : EMP_HANDLERS) handler.accept(server, center);
      }
      case REDMATTER -> {
        RedmatterEntity singularity = new RedmatterEntity(SimulationContent.REDMATTER, server);
        singularity.refreshPositionAndAngles(center.x, center.y + .5, center.z, 0, 0);
        singularity.setOwner(source);
        singularity.setVelocity(source == null ? Vec3d.ZERO : source.getVelocity().multiply(.1));
        return server.spawnEntity(singularity);
      }
      case HYPERSONIC, MISSILEMODULE -> {
        /* Upstream reserved no-op slots. */
      }
      default -> {
        Job job = new Job(type, center, size);
        job.source = attribution;
        if (type == PayloadType.THERMOBARIC || type == PayloadType.NUCLEAR)
          job.rayWork = new RayWork(false, size, settings.energy());
        return state(server).enqueue(job, false);
      }
    }
    return true;
  }

  public static int removeNear(ServerWorld world, Vec3d center, double radius) {
    if (!Double.isFinite(radius) || radius < 0 || radius > 10000) return 0;
    QueueState queue = state(world);
    int before = queue.jobs.size() + queue.pending.size();
    queue.jobs.removeIf(j -> j.center.squaredDistanceTo(center) <= radius * radius);
    queue.pending.removeIf(j -> j.center.squaredDistanceTo(center) <= radius * radius);
    queue.markDirty();
    int removed = before - queue.jobs.size() - queue.pending.size();
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
    return state(world).jobs.size() + state(world).pending.size();
  }

  /**
   * Defense integrations register here; avoids a hard dependency on optional technology systems.
   */
  public static final List<java.util.function.BiConsumer<ServerWorld, Vec3d>> EMP_HANDLERS =
      new ArrayList<>();

  private static void explosion(ServerWorld w, Vec3d p, float power, Entity source, boolean fire) {
    if (loaded(w, new BlockPos(p)))
      w.createExplosion(source, p.x, p.y, p.z, power, fire, Explosion.DestructionType.DESTROY);
  }

  private static void arrows(
      ServerWorld w, Vec3d p, Entity source, int count, double damage, boolean fire) {
    for (int i = 0; i < count; i++) {
      ArrowEntity arrow = new ArrowEntity(w, p.x, p.y + .2, p.z);
      arrow.setOwner(source);
      arrow.setDamage(damage);
      arrow.setCritical(true);
      arrow.pickupType =
          net.minecraft.entity.projectile.PersistentProjectileEntity.PickupPermission.DISALLOWED;
      if (fire) arrow.setOnFireFor(100);
      arrow.setVelocity(
          w.random.nextDouble() - .5,
          w.random.nextDouble() - .25,
          w.random.nextDouble() - .5,
          2.5f,
          1);
      w.spawnEntity(arrow);
    }
  }

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

  private static void breach(ServerWorld w, Vec3d p, Entity source) {
    // Upstream searches the most resistant adjacent wall, then spends energy along a narrow tunnel.
    Direction best = Direction.DOWN;
    float resistance = -1;
    for (Direction d : Direction.values()) {
      BlockPos pos = new BlockPos(p).offset(d);
      if (loaded(w, pos) && w.getBlockState(pos).getBlock().getBlastResistance() > resistance) {
        resistance = w.getBlockState(pos).getBlock().getBlastResistance();
        best = d;
      }
    }
    float energy = (float) PayloadConfig.forType(PayloadType.BREACHING).energy();
    for (int depth = 0; depth < 7 && energy > 0; depth++) {
      BlockPos target = new BlockPos(p).offset(best, depth);
      for (int a = -1; a <= 1; a++)
        for (int b = -1; b <= 1; b++) {
          BlockPos pos =
              best.getAxis() == Direction.Axis.Y
                  ? target.add(a, 0, b)
                  : best.getAxis() == Direction.Axis.X ? target.add(0, a, b) : target.add(a, b, 0);
          if (!loaded(w, pos)) continue;
          BlockState s = w.getBlockState(pos);
          energy -= s.getBlock().getBlastResistance() * .25f;
          if (energy > 0 && s.getHardness(w, pos) >= 0) w.breakBlock(pos, false, source);
        }
      energy *= .65f;
    }
    for (Entity e : nearby(w, p, 7))
      e.damage(
          DamageSource.explosion((LivingEntity) null),
          (float) PayloadConfig.forType(PayloadType.BREACHING).entityDamage());
  }

  private static void mutate(ServerWorld world, Vec3d center, int radius) {
    for (Entity entity : nearby(world, center, radius))
      if (entity instanceof LivingEntity living) applyMutation(living);
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

  private static void teleport(ServerWorld w, Vec3d p) {
    for (Entity e : nearby(w, p, PayloadConfig.forType(PayloadType.ENDER).radius())) {
      for (int attempt = 0; attempt < 16; attempt++) {
        BlockPos dest =
            new BlockPos(p)
                .add(
                    w.random.nextInt(61) - 30,
                    w.random.nextInt(31) - 15,
                    w.random.nextInt(61) - 30);
        if (loaded(w, dest)
            && w.getBlockState(dest).isAir()
            && w.getBlockState(dest.up()).isAir()
            && w.getBlockState(dest.down()).isSolidBlock(w, dest.down())) {
          e.requestTeleport(dest.getX() + .5, dest.getY(), dest.getZ() + .5);
          break;
        }
      }
    }
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
    final List<Job> jobs = new ArrayList<>();
    final List<Job> pending = new ArrayList<>();
    int nextJob;

    boolean enqueue(Job job, boolean deferred) {
      if (jobs.size() + pending.size() >= 256) return false;
      (deferred ? pending : jobs).add(job);
      markDirty();
      return true;
    }

    static QueueState read(NbtCompound nbt) {
      QueueState state = new QueueState();
      NbtList list = nbt.getList("jobs", 10);
      for (int i = 0; i < Math.min(list.size(), 4096) && state.jobs.size() < 256; i++) {
        NbtCompound n = list.getCompound(i);
        try {
          Vec3d center = new Vec3d(n.getDouble("x"), n.getDouble("y"), n.getDouble("z"));
          if (!validCoordinate(center.x)
              || !validCoordinate(center.y)
              || !validCoordinate(center.z)) continue;
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
          if (n.contains("rayWork", 10)) j.rayWork = RayWork.read(n.getCompound("rayWork"));
          if (n.contains("gasWork", 10))
            j.gasWork = GasWork.read(n.getCompound("gasWork"), new BlockPos(center));
          if (n.contains("destinationWorld", 8) && n.contains("destination")) {
            j.destinationWorld = n.getString("destinationWorld");
            j.destination = BlockPos.fromLong(n.getLong("destination"));
          }
          state.enqueue(j, false);
        } catch (IllegalArgumentException ignored) {
        }
      }
      return state;
    }

    private static boolean validCoordinate(double coordinate) {
      return Double.isFinite(coordinate) && Math.abs(coordinate) <= 30000000;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
      NbtList list = new NbtList();
      List<Job> saved = new ArrayList<>(jobs);
      saved.addAll(pending);
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
        if (j.rayWork != null) n.put("rayWork", j.rayWork.write());
        if (j.gasWork != null) n.put("gasWork", j.gasWork.write());
        if (j.destination != null && j.destinationWorld != null) {
          n.putString("destinationWorld", j.destinationWorld);
          n.putLong("destination", j.destination.asLong());
        }
        list.add(n);
      }
      nbt.put("jobs", list);
      return nbt;
    }

    void tick(ServerWorld w) {
      jobs.addAll(pending);
      pending.clear();
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

  static final class Job {
    final PayloadType type;
    final Vec3d center;
    final int radius;
    int cursor, age, elapsedTicks;
    boolean suppressEntityDamage;
    RayWork rayWork;
    GasWork gasWork;
    String destinationWorld;
    BlockPos destination;
    ActionSource source = ActionSource.of(null);

    Job(PayloadType type, Vec3d center, int radius) {
      this.type = type;
      this.center = center;
      this.radius = radius;
    }

    boolean tick(ServerWorld w, int budget) {
      PayloadConfig.Settings settings = PayloadConfig.forType(type);
      if (!settings.enabled()) return true;
      boolean timed =
          type == PayloadType.DEBILITATION
              || type == PayloadType.CHEMICAL
              || type == PayloadType.CONTAGIOUS
              || type == PayloadType.SMOKE
              || type == PayloadType.COLOR
              || type == PayloadType.ANTI_GRAVITATIONAL
              || type == PayloadType.REDMATTER;
      if (timed && elapsedTicks >= settings.durationTicks()) return true;
      age++;
      boolean gas =
          type == PayloadType.DEBILITATION
              || type == PayloadType.CHEMICAL
              || type == PayloadType.CONTAGIOUS
              || type == PayloadType.SMOKE
              || type == PayloadType.COLOR;
      if (gas) {
        if (type == PayloadType.SMOKE) {
          w.spawnParticles(
              ParticleTypes.SMOKE,
              center.x,
              center.y + .1,
              center.z,
              3 + w.random.nextInt(4),
              .05,
              .1,
              .05,
              .02);
          return elapsedTicks >= settings.durationTicks();
        }
        if (gasWork == null) gasWork = new GasWork(new BlockPos(center));
        gasWork.tick(w, center, radius, budget);
        if (age % 5 == 1) {
          if (type == PayloadType.COLOR) {
            var dust =
                new net.minecraft.particle.DustParticleEffect(
                    new Vec3f(w.random.nextFloat(), w.random.nextFloat(), w.random.nextFloat()), 2);
            w.spawnParticles(
                dust, center.x, center.y, center.z, 100, radius * .4, 2, radius * .4, .01);
          } else
            w.spawnParticles(
                type == PayloadType.SMOKE
                    ? ParticleTypes.CAMPFIRE_SIGNAL_SMOKE
                    : ParticleTypes.SNEEZE,
                center.x,
                center.y,
                center.z,
                80,
                radius * .4,
                2,
                radius * .4,
                .01);
          for (Entity e : nearby(w, center, radius))
            if (e instanceof LivingEntity living) {
              if (gasWork.contains(living.getBlockPos())
                  && (type == PayloadType.DEBILITATION
                      || type == PayloadType.CHEMICAL
                      || type == PayloadType.CONTAGIOUS)) ExposureContent.expose(living, type);
            }
        }
        return elapsedTicks >= settings.durationTicks();
      }
      if (type == PayloadType.ANTI_GRAVITATIONAL || type == PayloadType.REDMATTER) {
        force(
            w,
            center,
            radius,
            type == PayloadType.REDMATTER ? -2 : 0,
            type == PayloadType.ANTI_GRAVITATIONAL);
        if (type == PayloadType.REDMATTER)
          for (Entity e : nearby(w, center, 1.5))
            e.damage(DamageSource.OUT_OF_WORLD, (float) settings.entityDamage());
        if (age % 20 == 1)
          w.spawnParticles(
              type == PayloadType.REDMATTER ? ParticleTypes.PORTAL : ParticleTypes.END_ROD,
              center.x,
              center.y,
              center.z,
              40,
              2,
              2,
              2,
              .03);
      }
      if (type == PayloadType.SONIC) force(w, center, Math.min(radius, age * 2), .6, true);
      if (age == 1 && type == PayloadType.ENDER && destination != null) {
        ServerWorld target = targetWorld(w);
        if (target == null) return true;
        for (Entity entity : nearby(w, center, radius)) {
          Vec3d to = Vec3d.ofCenter(destination).add(entity.getPos().subtract(center));
          if (!loaded(target, new BlockPos(to))) continue;
          if (entity instanceof net.minecraft.server.network.ServerPlayerEntity player)
            player.teleport(target, to.x, to.y, to.z, entity.getYaw(), entity.getPitch());
          else {
            Entity moved = entity.world == target ? entity : entity.moveToWorld(target);
            if (moved != null) moved.requestTeleport(to.x, to.y, to.z);
          }
        }
      }
      if (age == 1) {
        Entity resolved = source.resolve(w);
        LivingEntity owner = resolved instanceof LivingEntity living ? living : null;
        if (type == PayloadType.CONDENSED)
          for (Entity entity : nearby(w, center, radius))
            entity.damage(
                DamageSource.explosion(owner),
                (float)
                    (settings.entityDamage()
                        * Math.max(
                            0, 1 - entity.getPos().distanceTo(center) / Math.max(1, radius))));
        if (type == PayloadType.ANTIMATTER && !suppressEntityDamage)
          for (Entity e : nearby(w, center, radius))
            e.damage(DamageSource.OUT_OF_WORLD, (float) settings.entityDamage());
        if (type == PayloadType.INCENDIARY)
          for (Entity e : nearby(w, center, radius)) e.setOnFireFor(20);
        w.spawnParticles(
            ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        if (type == PayloadType.NUCLEAR || type == PayloadType.THERMOBARIC)
          for (Entity e : nearby(w, center, 50))
            e.damage(
                DamageSource.explosion(owner),
                (float)
                    (settings.entityDamage()
                        * Math.max(0, 1 - e.getPos().distanceTo(center) / 50)));
        if (type == PayloadType.ENDOTHERMIC)
          for (Entity e : nearby(w, center, radius))
            if (e instanceof LivingEntity l) {
              effect(l, StatusEffects.POISON, 200, 2);
              effect(l, StatusEffects.MINING_FATIGUE, 2400, 2);
              effect(l, StatusEffects.SLOWNESS, 2400, 4);
            }
      }
      if (rayWork != null) {
        boolean complete = rayWork.tick(w, center, source.resolve(w), budget);
        if (complete) completed(w);
        return complete;
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
      if (type == PayloadType.ANTI_GRAVITATIONAL) return elapsedTicks >= settings.durationTicks();
      if (type == PayloadType.REDMATTER) {
        if (cursor >= total) cursor = 0;
        return elapsedTicks >= settings.durationTicks();
      }
      if (cursor >= total) {
        completed(w);
        return true;
      }
      return false;
    }

    void completed(ServerWorld world) {
      if (type == PayloadType.NUCLEAR) {
        mutate(world, center, 80);
        ExposureContent.contaminate(world, center, 80);
      }
    }

    ServerWorld targetWorld(ServerWorld sourceWorld) {
      net.minecraft.util.Identifier id =
          net.minecraft.util.Identifier.tryParse(destinationWorld == null ? "" : destinationWorld);
      return id == null
          ? null
          : sourceWorld
              .getServer()
              .getWorld(
                  net.minecraft.util.registry.RegistryKey.of(
                      net.minecraft.util.registry.Registry.WORLD_KEY, id));
    }

    void edit(ServerWorld w, BlockPos p, BlockState s) {
      switch (type) {
        case ENDER -> {
          if (s.isAir() || destination == null) return;
          ServerWorld target = targetWorld(w);
          if (target == null) return;
          BlockPos to = destination.add(p.subtract(new BlockPos(center)));
          if (!loaded(target, to) || !target.getBlockState(to).isAir()) return;
          FlyingBlockEntity captured = FlyingBlockEntity.capture(w, p, Vec3d.ZERO, 0);
          if (captured == null) return;
          // Entity dimensions change through vanilla transfer, preserving custom capture NBT.
          Entity moved = target == w ? captured : captured.moveToWorld(target);
          if (moved instanceof FlyingBlockEntity block) {
            block.requestTeleport(to.getX() + .5, to.getY(), to.getZ() + .5);
            block.settle();
          }
        }
        case INCENDIARY -> {
          if (s.isAir()
              && Blocks.FIRE.getDefaultState().canPlaceAt(w, p)
              && w.random.nextInt(4) == 0) w.setBlockState(p, Blocks.FIRE.getDefaultState(), 3);
        }
        case EXOTHERMIC -> {
          if (s.isOf(Blocks.WATER) || s.isOf(Blocks.ICE)) w.removeBlock(p, false);
          else if (s.isOf(Blocks.STONE)
              || s.isOf(Blocks.DIRT)
              || s.isOf(Blocks.GRASS_BLOCK)
              || s.isOf(Blocks.SAND))
            w.setBlockState(
                p,
                (w.random.nextInt(10) == 0
                        ? Blocks.LAVA
                        : w.random.nextBoolean() ? Blocks.MAGMA_BLOCK : Blocks.NETHERRACK)
                    .getDefaultState(),
                3);
        }
        case ENDOTHERMIC -> {
          Block replacement =
              s.isOf(Blocks.WATER)
                  ? Blocks.ICE
                  : s.isOf(Blocks.LAVA)
                      ? Blocks.OBSIDIAN
                      : s.isOf(Blocks.MAGMA_BLOCK)
                          ? Blocks.STONE
                          : s.isOf(Blocks.NETHERRACK)
                              ? Blocks.DIRT
                              : s.isOf(Blocks.SOUL_SAND)
                                  ? Blocks.SAND
                                  : s.isOf(Blocks.DIRT) || s.isOf(Blocks.GRASS_BLOCK)
                                      ? Blocks.SNOW_BLOCK
                                      : null;
          if (replacement != null) w.setBlockState(p, replacement.getDefaultState(), 3);
          else if (s.isOf(Blocks.FIRE)) w.removeBlock(p, false);
        }

        case ROT -> {
          if (s.isOf(Blocks.GRASS_BLOCK) || s.isOf(Blocks.MYCELIUM) || s.isOf(Blocks.PODZOL))
            w.setBlockState(p, Blocks.COARSE_DIRT.getDefaultState(), 3);
          else if (s.getBlock() instanceof LeavesBlock || s.getBlock() instanceof PlantBlock)
            w.removeBlock(p, false);
        }
        case ANTI_GRAVITATIONAL -> {
          if (!s.isAir() && w.random.nextInt(32) == 0)
            FlyingBlockEntity.capture(
                w,
                p,
                new Vec3d(0, .3, 0),
                Math.max(0, PayloadConfig.forType(type).durationTicks() - elapsedTicks));
        }
        case REDMATTER -> {
          if (!s.isAir() && w.random.nextInt(8) == 0) w.removeBlock(p, false);
        }
        case SONIC -> {
          if (!s.isAir()) {
            if (w.random.nextInt(10) == 0)
              FlyingBlockEntity.capture(
                  w, p, new Vec3d((p.getX() - center.x) * .08, .5, (p.getZ() - center.z) * .08), 0);
            else w.breakBlock(p, false, source.resolve(w));
          }
        }
        case THERMOBARIC, NUCLEAR -> {
          double distance = Vec3d.ofCenter(p).distanceTo(center);
          if (!s.isAir()
              && s.getBlock().getBlastResistance()
                  < PayloadConfig.forType(type).energy()
                      * Math.max(0, 1 - distance / Math.max(1, radius))) w.removeBlock(p, false);
        }
        case ANTIMATTER -> {
          if (!s.isAir()) w.removeBlock(p, false);
        }
        default -> {}
      }
    }
  }

  private static void effect(LivingEntity entity, StatusEffect effect, int ticks, int amplifier) {
    entity.addStatusEffect(new StatusEffectInstance(effect, ticks, amplifier));
  }
}

package dev.icbm.port.payload;

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
    if (!(world instanceof ServerWorld server) || !Double.isFinite(center.x + center.y + center.z))
      return;
    PayloadConfig.Settings settings = PayloadConfig.forType(type);
    if (!settings.enabled()) return;
    switch (type) {
      case CONDENSED -> explosion(server, center, Math.min(16, settings.radius()), source, false);
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
        force(
            server, center, settings.radius() * 4, type == PayloadType.ATTRACTIVE ? -3 : 3, false);
        explosion(server, center, Math.min(16, settings.radius()), source, false);
      }
      case BREACHING -> breach(server, center, source);
      case MUTATION -> mutate(server, center, settings.radius());
      case ENDER -> teleport(server, center);
      case EMP -> {
        for (var handler : EMP_HANDLERS) handler.accept(server, center);
      }
      case HYPERSONIC, MISSILEMODULE -> {
        /* Upstream reserved no-op slots. */
      }
      default -> {
        Job job = new Job(type, center, radius(type));
        state(server).enqueue(job, false);
        state(server).markDirty();
      }
    }
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

  private static void mutate(ServerWorld w, Vec3d p, int radius) {
    for (Entity entity : nearby(w, p, radius)) {
      Entity replacement =
          entity instanceof PigEntity
              ? EntityType.ZOMBIFIED_PIGLIN.create(w)
              : entity instanceof VillagerEntity ? EntityType.ZOMBIE_VILLAGER.create(w) : null;
      if (replacement != null) {
        replacement.refreshPositionAndAngles(
            entity.getX(), entity.getY(), entity.getZ(), entity.getYaw(), entity.getPitch());
        w.spawnEntity(replacement);
        entity.discard();
      }
    }
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
        if (age % 10 == 1) {
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
              if (type == PayloadType.DEBILITATION) {
                effect(living, StatusEffects.POISON, 360, 0);
                effect(living, StatusEffects.MINING_FATIGUE, 1200, 0);
                effect(living, StatusEffects.SLOWNESS, 1200, 2);
              } else if (type == PayloadType.CHEMICAL || type == PayloadType.CONTAGIOUS) {
                effect(living, StatusEffects.POISON, 100, Math.min(3, age / 200));
                if (type == PayloadType.CONTAGIOUS) {
                  effect(living, StatusEffects.WITHER, 100, 0);
                  for (Entity other : nearby(w, living.getPos(), 3))
                    if (other instanceof LivingEntity l) effect(l, StatusEffects.POISON, 200, 0);
                }
              }
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
      if (age == 1) {
        if (type == PayloadType.ANTIMATTER)
          for (Entity e : nearby(w, center, radius))
            e.damage(DamageSource.OUT_OF_WORLD, (float) settings.entityDamage());
        if (type == PayloadType.INCENDIARY)
          for (Entity e : nearby(w, center, radius)) e.setOnFireFor(20);
        w.spawnParticles(
            ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        if (type == PayloadType.NUCLEAR || type == PayloadType.THERMOBARIC)
          for (Entity e : nearby(w, center, 50))
            e.damage(
                DamageSource.explosion((LivingEntity) null),
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
        if (type == PayloadType.NUCLEAR) {
          mutate(w, center, 80);
          state(w).enqueue(new Job(PayloadType.ROT, center, 80), true);
        }
        return true;
      }
      return false;
    }

    void edit(ServerWorld w, BlockPos p, BlockState s) {
      switch (type) {
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
          if (!s.isAir()
              && s.getFluidState().isEmpty()
              && !s.hasBlockEntity()
              && w.random.nextInt(32) == 0) {
            FallingBlockEntity f = FallingBlockEntity.spawnFromBlock(w, p, s);
            f.setVelocity(0, 1, 0);
            f.dropItem = false;
          }
        }
        case REDMATTER -> {
          if (!s.isAir() && w.random.nextInt(8) == 0) w.removeBlock(p, false);
        }
        case SONIC -> {
          if (!s.isAir()) {
            if (!s.hasBlockEntity() && w.random.nextInt(10) == 0) {
              FallingBlockEntity f = FallingBlockEntity.spawnFromBlock(w, p, s);
              f.setVelocity((p.getX() - center.x) * .08, .5, (p.getZ() - center.z) * .08);
              f.dropItem = false;
            } else w.removeBlock(p, false);
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

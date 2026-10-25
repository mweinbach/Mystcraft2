package dev.icbm.port.payload;

import dev.icbm.port.entity.MissileEntity;
import dev.icbm.port.entity.PrimedExplosiveEntity;
import net.fabricmc.fabric.api.dimension.v1.FabricDimensions;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.TeleportTarget;
import org.jetbrains.annotations.Nullable;

/**
 * Adapted from MIT ICBM-Classic {@code content/blast/ender/BlastEnder} and {@code
 * EnderBlastCustomization}. For 160 ticks every entity in the radius cube is drawn towards the
 * centre (horizontal pull {@code (±r - d) |d| 0.0006}, vertical {@code 0.0011}); entities within 4
 * blocks are teleported, with the enderman sound, to the customised target or else to one random
 * spot chosen once within 150 blocks. Portal sounds play each tick and portal particles swirl on a
 * shrinking shell; eight endermen appear at the centre when the blast ends.
 *
 * <p>The target comes from a targeting tool used on the placed explosive (kept by the primed
 * explosive) or from the missile's configured destination. Port differences: the random spot is
 * searched only in loaded chunks for a two-block air gap (upstream's loop could pick a spot inside
 * terrain or load chunks; with no loaded spot nobody is moved that tick); a target in another
 * dimension is honoured (upstream ignored the saved dimension); portal particles are sent by the
 * server with a bounded count; at most {@link BlastSupport#MAX_ENTITIES} entities are affected per
 * tick.
 */
final class EnderBlast implements BlastWork {
  /** Upstream {@code BlastEnder.duration}. */
  static final int DURATION = 20 * 8;
  static final double TELEPORT_RANGE = 4;
  static final int RANDOM_RANGE = 150;
  static final int PARTICLES_PER_TICK = 48;

  private @Nullable Identifier targetWorld;
  private @Nullable Vec3d target;

  private EnderBlast(@Nullable Identifier targetWorld, @Nullable Vec3d target) {
    this.targetWorld = targetWorld;
    this.target = target;
  }

  static EnderBlast create(ServerWorld world, Vec3d center, int size, @Nullable Entity source) {
    if (source instanceof PrimedExplosiveEntity primed && primed.getTargetPosition() != null)
      return new EnderBlast(primed.getTargetDimension(), onTop(primed.getTargetPosition()));
    if (source instanceof MissileEntity missile && missile.getTargetPosition() != null)
      return new EnderBlast(
          missile.getTargetDimension().getValue(), onTop(missile.getTargetPosition()));
    return new EnderBlast(null, null);
  }

  /** The recorded block is the one aimed at; arrive standing on it. */
  static Vec3d onTop(BlockPos pos) {
    return new Vec3d(pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5);
  }

  @Override
  public boolean tick(ServerWorld world, Payloads.Job job, int budget) {
    Vec3d c = job.center;
    double radius = BlastSupport.blastRadius(job.radius);
    int call = job.age - 1;
    particles(world, c, radius, job.age);
    boolean flashed = false;
    for (Entity entity : BlastSupport.entities(world, new Box(c, c).expand(radius), c, e -> true)) {
      double dx = entity.getX() - c.x, dy = entity.getY() - c.y, dz = entity.getZ() - c.z;
      int r = (int) radius;
      Vec3d velocity =
          new Vec3d(
              -((dx < 0 ? -r : r) - dx) * Math.abs(dx) * .0006,
              ((entity.getY() > c.y ? -r : r) - dy) * Math.abs(dy) * .0011,
              -((dz < 0 ? -r : r) - dz) * Math.abs(dz) * .0006);
      BlastSupport.push(entity, velocity);
      if (entity.getPos().distanceTo(c) >= TELEPORT_RANGE) continue;
      if (!flashed && call % 5 == 0) {
        world.spawnParticles(
            ParticleTypes.EXPLOSION_EMITTER, entity.getX(), entity.getY(), entity.getZ(), 1, 0, 0, 0,
            0);
        flashed = true;
      }
      if (target == null) target = randomTarget(world, c);
      if (target == null) continue;
      world.playSound(
          null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ENTITY_ENDERMAN_TELEPORT,
          SoundCategory.BLOCKS, 1, 1);
      teleport(world, entity, target);
    }
    world.playSound(
        null, c.x, c.y, c.z, SoundEvents.BLOCK_PORTAL_AMBIENT, SoundCategory.BLOCKS, 2f,
        world.random.nextFloat() * .4f + .8f);
    // The lifetime is in world ticks; a busy queue serves each job only intermittently.
    if (job.elapsedTicks > DURATION) {
      for (int i = 0; i < 8; i++) {
        EndermanEntity enderman = EntityType.ENDERMAN.create(world);
        if (enderman == null) break;
        enderman.refreshPositionAndAngles(c.x, c.y, c.z, world.random.nextFloat() * 360, 0);
        world.spawnEntity(enderman);
      }
      return true;
    }
    return false;
  }

  private void teleport(ServerWorld world, Entity entity, Vec3d to) {
    ServerWorld destination = world;
    if (targetWorld != null && !targetWorld.equals(world.getRegistryKey().getValue())) {
      ServerWorld other =
          world.getServer().getWorld(RegistryKey.of(Registry.WORLD_KEY, targetWorld));
      if (other != null) destination = other;
    }
    boolean loaded = BlastSupport.loaded(destination, new BlockPos(to));
    // Only players may arrive in an unloaded chunk (the server loads around them); never load
    // chunks in another dimension from inside a blast tick.
    if (!loaded && (destination != world || !(entity instanceof ServerPlayerEntity))) return;
    if (destination == world) {
      if (entity instanceof ServerPlayerEntity player)
        player.networkHandler.requestTeleport(
            to.x, to.y, to.z, entity.getYaw(), entity.getPitch());
      else entity.requestTeleport(to.x, to.y, to.z);
      return;
    }
    FabricDimensions.teleport(
        entity,
        destination,
        new TeleportTarget(to, entity.getVelocity(), entity.getYaw(), entity.getPitch()));
  }

  private static @Nullable Vec3d randomTarget(ServerWorld world, Vec3d c) {
    for (int attempt = 0; attempt < 16; attempt++) {
      int x = world.random.nextInt(RANDOM_RANGE * 2) - RANDOM_RANGE + (int) c.x;
      int z = world.random.nextInt(RANDOM_RANGE * 2) - RANDOM_RANGE + (int) c.z;
      BlockPos.Mutable pos = new BlockPos.Mutable(x, MathHelper.floor(c.y), z);
      if (!world.isChunkLoaded(pos)) continue;
      for (; pos.getY() < world.getTopY() - 1; pos.move(0, 1, 0)) {
        if (world.isOutOfHeightLimit(pos) || !world.getWorldBorder().contains(pos)) break;
        if (world.getBlockState(pos).isAir() && world.getBlockState(pos.up()).isAir())
          return new Vec3d(x + .5, pos.getY(), z + .5);
      }
    }
    return null;
  }

  /** Upstream drew these on clients over the whole shell; here a bounded sample is sent. */
  private static void particles(ServerWorld world, Vec3d c, double radius, int calls) {
    double r = radius - (double) calls / DURATION * radius;
    if (r <= 1) return;
    for (int i = 0; i < PARTICLES_PER_TICK; i++) {
      double yaw = world.random.nextDouble() * Math.PI * 2;
      double pitch = Math.acos(2 * world.random.nextDouble() - 1);
      Vec3d offset =
          new Vec3d(
              Math.sin(pitch) * Math.cos(yaw) * r,
              Math.cos(pitch) * r,
              Math.sin(pitch) * Math.sin(yaw) * r);
      Vec3d at = c.add(offset);
      if (!world.isChunkLoaded(new BlockPos(at)) || !world.getBlockState(new BlockPos(at)).isAir())
        continue;
      // Count 0 makes the offsets a velocity, as upstream's per-particle motion.
      world.spawnParticles(
          ParticleTypes.PORTAL, at.x, at.y, at.z, 0, offset.x * .6, offset.y * .6, offset.z * .6,
          1);
    }
  }

  @Override
  public NbtCompound write() {
    NbtCompound nbt = new NbtCompound();
    if (targetWorld != null) nbt.putString("targetWorld", targetWorld.toString());
    if (target != null) {
      nbt.putDouble("tx", target.x);
      nbt.putDouble("ty", target.y);
      nbt.putDouble("tz", target.z);
    }
    return nbt;
  }

  static EnderBlast read(NbtCompound nbt, int radius) {
    Identifier world =
        nbt.contains("targetWorld", 8) ? Identifier.tryParse(nbt.getString("targetWorld")) : null;
    Vec3d target =
        nbt.contains("tx") ? new Vec3d(nbt.getDouble("tx"), nbt.getDouble("ty"), nbt.getDouble("tz")) : null;
    // Saves from before this port stored a raw destination on the job.
    if (target == null && nbt.contains("destination", 4)) {
      target = onTop(BlockPos.fromLong(nbt.getLong("destination")));
      world = Identifier.tryParse(nbt.getString("destinationWorld"));
    }
    if (target != null && !Double.isFinite(target.x + target.y + target.z)) target = null;
    return new EnderBlast(world, target);
  }
}

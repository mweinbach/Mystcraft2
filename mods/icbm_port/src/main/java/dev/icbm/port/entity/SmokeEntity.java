package dev.icbm.port.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Stationary smoke emitter spawned by the smoke payload. Adapted from MIT ICBM-Classic {@code
 * content/entity/EntitySmoke.java}, which upstream spawns through {@code
 * content/actions/entity/ActionSpawnEntity.java} at the blast position with a random yaw.
 *
 * <p>Like upstream it does not move or collide, emits 3-6 vanilla smoke particles per tick on
 * clients that track it (range 100 blocks), and is removed by the server once its age exceeds
 * its lifetime (upstream {@code ticksToLive}, default 100). Unlike upstream its age is saved, so
 * a reload continues the remaining lifetime instead of restarting it. The upstream color fields
 * are saved for data compatibility but, as upstream, do not tint the vanilla smoke.
 */
public class SmokeEntity extends Entity {
  /** Upstream default {@code ticksToLive}. */
  public static final int DEFAULT_LIFETIME = 100;

  /** Upper bound accepted from configuration or saved data (one hour). */
  public static final int MAX_LIFETIME = 72000;

  private int ticksToLive = DEFAULT_LIFETIME;
  private int life;
  private float red = 1, green, blue;

  public SmokeEntity(EntityType<? extends SmokeEntity> type, World world) {
    super(type, world);
    noClip = true;
  }

  /** Upstream {@code ActionSpawnEntity.doAction} for the smoke registry name. */
  public static boolean spawn(ServerWorld world, Vec3d position, int lifetime) {
    if (lifetime <= 0 || !world.isChunkLoaded(new BlockPos(position))) return false;
    SmokeEntity smoke = new SmokeEntity(BlastEntities.SMOKE, world);
    smoke.refreshPositionAndAngles(
        position.x,
        position.y,
        position.z,
        MathHelper.wrapDegrees(world.random.nextFloat() * 360),
        0);
    smoke.setLifetime(lifetime);
    return world.spawnEntity(smoke);
  }

  public int getLifetime() {
    return ticksToLive;
  }

  public void setLifetime(int ticks) {
    ticksToLive = Math.max(0, Math.min(MAX_LIFETIME, ticks));
  }

  public int getLife() {
    return life;
  }

  @Override
  protected void initDataTracker() {}

  @Override
  public void tick() {
    super.tick();
    if (!world.isClient) {
      // Upstream: "Safety in case the beam is never killed".
      if (++life > ticksToLive) discard();
      return;
    }
    int count = 3 + random.nextInt(4);
    for (int i = 0; i < count; i++)
      world.addParticle(
          ParticleTypes.SMOKE,
          getX(),
          getY() + .1,
          getZ(),
          .05 * random.nextFloat() - .05 * random.nextFloat(),
          .1,
          .05 * random.nextFloat() - .05 * random.nextFloat());
  }

  @Override
  public boolean isPushable() {
    return false;
  }

  @Override
  public boolean canHit() {
    return false;
  }

  @Override
  protected MoveEffect getMoveEffect() {
    return MoveEffect.NONE;
  }

  @Override
  public boolean shouldRender(double distance) {
    return true;
  }

  @Override
  protected void readCustomDataFromNbt(NbtCompound nbt) {
    if (nbt.contains("ticksToLive")) setLifetime(nbt.getInt("ticksToLive"));
    life = Math.max(0, Math.min(MAX_LIFETIME, nbt.getInt("life")));
    NbtCompound color = nbt.getCompound("color");
    if (color.contains("red")) red = color.getFloat("red");
    green = color.getFloat("green");
    blue = color.getFloat("blue");
  }

  @Override
  protected void writeCustomDataToNbt(NbtCompound nbt) {
    nbt.putInt("ticksToLive", ticksToLive);
    nbt.putInt("life", life);
    NbtCompound color = new NbtCompound();
    color.putFloat("red", red);
    color.putFloat("green", green);
    color.putFloat("blue", blue);
    nbt.put("color", color);
  }

  @Override
  public Packet<?> createSpawnPacket() {
    return new EntitySpawnS2CPacket(this);
  }
}

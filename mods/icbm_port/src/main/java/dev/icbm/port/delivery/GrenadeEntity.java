package dev.icbm.port.delivery;

import dev.icbm.port.payload.*;
import net.minecraft.entity.*;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Thrown grenade. Physics, fuse and throw vector are adapted from MIT ICBM-Classic {@code
 * content/entity/EntityGrenade.java}: 0.98 air / 0.8 water drag, 0.03 gravity, halved motion on
 * the ground, no impact detonation, and detonation 0.3 above the entity once the configured
 * {@code ConfigFuseTimes.GRENADES} fuse has fully elapsed ({@code ticksExisted > fuse}).
 *
 * <p>Unlike upstream, which restarts the fuse from {@code ticksExisted} after a reload, the
 * remaining fuse is saved.
 */
public final class GrenadeEntity extends ThrownItemEntity {
  /** Upstream {@code EntityGrenade#setThrowMotion} speed for a fully charged throw. */
  public static final double THROW_SPEED = 1.8;

  /** Upstream {@code setThrowableHeading} inaccuracy per axis. */
  public static final double THROW_INACCURACY = 0.007499999832361937;

  private PayloadType payload = PayloadType.CONDENSED;

  /** Remaining ticks; the grenade detonates on the first tick that starts with zero. */
  private int fuse = 100;

  public GrenadeEntity(EntityType<? extends GrenadeEntity> type, World world) {
    super(type, world);
  }

  @Override
  protected Item getDefaultItem() {
    return DeliveryContent.GRENADES.getOrDefault(payload, net.minecraft.item.Items.FIRE_CHARGE);
  }

  public void setPayload(PayloadType payload) {
    this.payload = payload;
    fuse = DeliveryContent.grenadeFuse(payload);
  }

  public PayloadType getPayload() {
    return payload;
  }

  public int getFuse() {
    return fuse;
  }

  public void setFuse(int fuse) {
    this.fuse = Math.max(0, Math.min(72000, fuse));
  }

  /**
   * Places the grenade at the thrower's eye, offset as upstream {@code aimFromThrower} does, and
   * applies the charge-scaled upstream throw vector.
   */
  public void aimFromThrower(Entity thrower, float charge) {
    float yaw = thrower.getYaw(), pitch = thrower.getPitch();
    double yawRad = yaw * MathHelper.RADIANS_PER_DEGREE;
    double pitchRad = pitch * MathHelper.RADIANS_PER_DEGREE;
    setPosition(
        thrower.getX() - Math.cos(yawRad) * 0.16F,
        thrower.getEyeY() - 0.10000000149011612D,
        thrower.getZ() - Math.sin(yawRad) * 0.16F);
    Vec3d heading =
        new Vec3d(
                -Math.sin(yawRad) * Math.cos(pitchRad),
                -Math.sin(pitchRad),
                Math.cos(yawRad) * Math.cos(pitchRad))
            .normalize()
            .add(
                random.nextGaussian() * THROW_INACCURACY,
                random.nextGaussian() * THROW_INACCURACY,
                random.nextGaussian() * THROW_INACCURACY)
            .multiply(THROW_SPEED * MathHelper.clamp(charge, 0, 1));
    setVelocity(heading);
    setYaw((float) (Math.atan2(heading.x, heading.z) * 180 / Math.PI));
    setPitch((float) (Math.atan2(heading.y, heading.horizontalLength()) * 180 / Math.PI));
    prevYaw = getYaw();
    prevPitch = getPitch();
  }

  @Override
  public void tick() {
    baseTick();
    Vec3d motion = getVelocity();
    move(MovementType.SELF, motion);
    double drag = .98;
    if (isTouchingWater()) {
      drag = .8;
      if (world.isClient)
        for (int i = 0; i < 4; i++)
          world.addParticle(
              ParticleTypes.BUBBLE,
              getX() - motion.x * .25,
              getY() - motion.y * .25,
              getZ() - motion.z * .25,
              motion.x,
              motion.y,
              motion.z);
    }
    setVelocity(getVelocity().multiply(drag));
    if (onGround) setVelocity(getVelocity().multiply(.5));
    else setVelocity(getVelocity().add(0, -.03, 0));
    if (world.isClient) {
      Vec3d v = getVelocity();
      world.addParticle(
          ParticleTypes.SMOKE, getX() - v.x, getY() - v.y + .4, getZ() - v.z, v.x, v.y, v.z);
    } else if (fuse-- <= 0) {
      discard();
      Payloads.detonate(
          world, getPos().add(0, .3, 0), payload, getOwner() == null ? this : getOwner());
    }
  }

  /** Upstream grenades can be targeted and hit ({@code canBeCollidedWith}). */
  @Override
  public boolean canHit() {
    return !isRemoved();
  }

  /** Upstream {@code canBePushed}. */
  @Override
  public boolean isPushable() {
    return true;
  }

  @Override
  public void writeCustomDataToNbt(NbtCompound nbt) {
    super.writeCustomDataToNbt(nbt);
    nbt.putString("Payload", payload.id());
    nbt.putInt("Fuse", fuse);
  }

  @Override
  public void readCustomDataFromNbt(NbtCompound nbt) {
    super.readCustomDataFromNbt(nbt);
    payload = DeliveryContent.readPayload(nbt.getString("Payload"));
    fuse =
        nbt.contains("Fuse")
            ? Math.max(0, Math.min(72000, nbt.getInt("Fuse")))
            : DeliveryContent.grenadeFuse(payload);
  }
}

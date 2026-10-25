package dev.icbm.port.delivery;

import dev.icbm.port.payload.*;
import net.minecraft.entity.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Small gravity-driven impact explosive.
 *
 * <p>Adapted from MIT ICBM-Classic {@code content/cluster/bomblet/EntityBombDroplet.java} and
 * {@code lib/projectile/EntityProjectile.java}: 0.99 air drag then 0.05 gravity per tick, impact
 * damage {@code ceil(speed * 3)} (ConfigBomblet.impactDamage), projectile knockback on living
 * targets, the explosion centred on the impact point, and no health ({@code hasHealth = false}) so
 * attacks, fire and nearby blasts never destroy or chain-detonate a droplet in flight.
 */
public final class BombletEntity extends ThrownItemEntity {
  /** Upstream {@code EntityProjectile.DEFAULT_GRAVITY}. */
  public static final float GRAVITY = 0.05F;

  /** Upstream {@code ConfigBomblet.impactDamage}. */
  public static final float IMPACT_DAMAGE_SCALE = 3F;

  /** Upstream {@code EntityProjectile} air-kill after sixty seconds of flight. */
  public static final int MAX_FLIGHT_TICKS = 1200;

  private PayloadType payload = PayloadType.CONDENSED;
  private boolean detonated;

  public BombletEntity(EntityType<? extends BombletEntity> type, World world) {
    super(type, world);
  }

  public void setPayload(PayloadType payload) {
    this.payload = payload;
    setItem(new ItemStack(DeliveryContent.BOMBLETS.getOrDefault(payload, Items.FIRE_CHARGE)));
  }

  public PayloadType getPayload() {
    return payload;
  }

  @Override
  protected Item getDefaultItem() {
    return Items.FIRE_CHARGE;
  }

  @Override
  protected float getGravity() {
    return GRAVITY;
  }

  /** Impact damage for a given speed, upstream {@code MathHelper.ceil(velocity * impactDamage)}. */
  public static float impactDamage(double speed) {
    return (float) Math.ceil(speed * IMPACT_DAMAGE_SCALE);
  }

  /**
   * Players can target a falling bomblet (crosshair selection only offers entities whose no-arg
   * {@code canHit()} is true), so the defuser's attack callback can disarm it, like grenades and
   * primed charges.
   */
  @Override
  public boolean canHit() {
    return !isRemoved();
  }

  @Override
  protected boolean canHit(Entity entity) {
    return !(entity instanceof BombletEntity) && super.canHit(entity);
  }

  @Override
  protected void onCollision(HitResult hit) {
    super.onCollision(hit);
    if (hit.getType() == HitResult.Type.MISS) return;
    Vec3d impact = hit.getPos();
    if (hit instanceof EntityHitResult entityHit) {
      Entity target = entityHit.getEntity();
      Vec3d motion = getVelocity();
      if (!world.isClient
          && target.damage(
              DamageSource.thrownProjectile(this, getOwner()), impactDamage(motion.length()))
          && target instanceof LivingEntity) {
        // Upstream EntityProjectile.applyKnockBack: 0.6 along horizontal heading, 0.1 upward.
        double horizontal = motion.horizontalLength();
        if (horizontal > 0)
          target.addVelocity(motion.x * .6 / horizontal, .1, motion.z * .6 / horizontal);
      }
      // Vanilla entity hits report the target's feet; the droplet's own position is closer to
      // upstream's ray intercept point.
      impact = getPos();
    }
    detonate(impact);
  }

  private void detonate(Vec3d at) {
    if (!world.isClient && !detonated) {
      detonated = true;
      discard();
      Payloads.detonate(world, at, payload, getOwner() == null ? this : getOwner());
    }
  }

  /** Bomblets have no health upstream, so no damage source can destroy or set them off. */
  @Override
  public boolean damage(DamageSource source, float amount) {
    return false;
  }

  @Override
  public void tick() {
    if (hasVehicle()) {
      baseTick();
      return;
    }
    if (!world.isClient
        && (age > MAX_FLIGHT_TICKS
            || !world.isChunkLoaded(getBlockPos())
            || getY() < world.getBottomY() - 16)) {
      discard();
      return;
    }
    super.tick();
  }

  @Override
  public void writeCustomDataToNbt(NbtCompound nbt) {
    super.writeCustomDataToNbt(nbt);
    nbt.putString("Payload", payload.id());
    nbt.putInt("FlightAge", age);
  }

  @Override
  public void readCustomDataFromNbt(NbtCompound nbt) {
    super.readCustomDataFromNbt(nbt);
    payload = DeliveryContent.readPayload(nbt.getString("Payload"));
    age = Math.max(0, nbt.getInt("FlightAge"));
  }
}

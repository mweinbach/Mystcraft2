package dev.icbm.port.delivery;

import dev.icbm.port.payload.*;
import net.minecraft.entity.*;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Upstream grenade drag, gravity, charge and fuse; fuse persists across saves. */
public final class GrenadeEntity extends ThrownItemEntity {
  private PayloadType payload = PayloadType.CONDENSED;
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

  @Override
  public void tick() {
    baseTick();
    Vec3d motion = getVelocity();
    move(MovementType.SELF, motion);
    double drag = isTouchingWater() ? .8 : .98;
    setVelocity(motion.multiply(drag));
    if (onGround) setVelocity(getVelocity().multiply(.5));
    else setVelocity(getVelocity().add(0, -.03, 0));
    if (!world.isClient && --fuse <= 0) {
      discard();
      Payloads.detonate(
          world, getPos().add(0, .3, 0), payload, getOwner() == null ? this : getOwner());
    } else if (world.isClient)
      world.addParticle(ParticleTypes.SMOKE, getX(), getY() + .4, getZ(), 0, 0, 0);
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

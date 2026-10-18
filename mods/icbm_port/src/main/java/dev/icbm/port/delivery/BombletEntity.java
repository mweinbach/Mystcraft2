package dev.icbm.port.delivery;

import dev.icbm.port.payload.*;
import net.minecraft.entity.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.hit.*;
import net.minecraft.world.World;

/** Small gravity-driven impact explosive, adapted from EntityBombDroplet. */
public final class BombletEntity extends ThrownItemEntity {
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
  protected boolean canHit(Entity entity) {
    return !(entity instanceof BombletEntity) && super.canHit(entity);
  }

  @Override
  protected void onCollision(HitResult hit) {
    super.onCollision(hit);
    if (hit instanceof EntityHitResult entityHit)
      entityHit
          .getEntity()
          .damage(
              DamageSource.thrownProjectile(this, getOwner()),
              (float) Math.ceil(getVelocity().length() * 4));
    if (hit.getType() != HitResult.Type.MISS) detonate();
  }

  private void detonate() {
    if (!world.isClient && !detonated) {
      detonated = true;
      discard();
      Payloads.detonate(world, getPos(), payload, getOwner() == null ? this : getOwner());
    }
  }

  @Override
  public boolean damage(DamageSource source, float amount) {
    if (source.isFire() || source.isExplosive()) detonate();
    return true;
  }

  @Override
  public void tick() {
    if (hasVehicle()) {
      baseTick();
      return;
    }
    if (!world.isClient
        && (age > 1200
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

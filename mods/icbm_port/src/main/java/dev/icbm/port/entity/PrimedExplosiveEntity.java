package dev.icbm.port.entity;

import dev.icbm.port.IcbmPort;
import dev.icbm.port.payload.PayloadType;
import dev.icbm.port.payload.Payloads;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FlyingItemEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.world.World;

public final class PrimedExplosiveEntity extends Entity implements FlyingItemEntity {
  private static final TrackedData<Integer> FUSE =
      DataTracker.registerData(PrimedExplosiveEntity.class, TrackedDataHandlerRegistry.INTEGER);
  private static final TrackedData<String> PAYLOAD =
      DataTracker.registerData(PrimedExplosiveEntity.class, TrackedDataHandlerRegistry.STRING);

  public PrimedExplosiveEntity(EntityType<? extends PrimedExplosiveEntity> type, World world) {
    super(type, world);
    intersectionChecked = true;
  }

  @Override
  protected void initDataTracker() {
    dataTracker.startTracking(FUSE, IcbmPort.FUSE_TICKS);
    dataTracker.startTracking(PAYLOAD, PayloadType.CONDENSED.id());
  }

  public PayloadType getPayload() {
    return IcbmPort.payloadById(dataTracker.get(PAYLOAD));
  }

  public void setPayload(PayloadType payload) {
    dataTracker.set(PAYLOAD, payload.id());
  }

  public int getFuse() {
    return dataTracker.get(FUSE);
  }

  public void setFuse(int fuse) {
    dataTracker.set(FUSE, Math.max(0, Math.min(fuse, 72000)));
  }

  @Override
  public void tick() {
    super.tick();
    if (!hasNoGravity()) setVelocity(getVelocity().add(0, -0.04, 0));
    move(MovementType.SELF, getVelocity());
    setVelocity(getVelocity().multiply(0.98));
    if (onGround) setVelocity(getVelocity().multiply(0.7, -0.5, 0.7));
    if (!world.isClient) {
      setFuse(getFuse() - 1);
      if (getFuse() <= 0) {
        discard();
        Payloads.detonate(world, getPos(), getPayload(), this);
      }
    } else {
      world.addParticle(ParticleTypes.SMOKE, getX(), getY() + 0.5, getZ(), 0, 0, 0);
    }
  }

  @Override
  protected void readCustomDataFromNbt(NbtCompound nbt) {
    if (nbt.contains("Fuse")) setFuse(nbt.getInt("Fuse"));
    setPayload(IcbmPort.payloadById(nbt.getString("Payload")));
  }

  @Override
  protected void writeCustomDataToNbt(NbtCompound nbt) {
    nbt.putInt("Fuse", getFuse());
    nbt.putString("Payload", getPayload().id());
  }

  @Override
  public Packet<?> createSpawnPacket() {
    return new EntitySpawnS2CPacket(this);
  }

  @Override
  public ItemStack getStack() {
    return new ItemStack(
        IcbmPort.EXPLOSIVE_BLOCKS.getOrDefault(getPayload(), IcbmPort.CONDENSED_EXPLOSIVE));
  }
}

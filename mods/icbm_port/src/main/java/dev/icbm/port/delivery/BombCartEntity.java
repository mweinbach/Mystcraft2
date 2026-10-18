package dev.icbm.port.delivery;

import dev.icbm.port.IcbmPort;
import dev.icbm.port.payload.*;
import net.minecraft.block.BlockState;
import net.minecraft.entity.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.*;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.*;

/**
 * Vanilla rail movement, activator, burning-projectile and collision triggers, with an ICBM
 * payload.
 */
public final class BombCartEntity extends TntMinecartEntity {
  private boolean detonated;
  private static final TrackedData<String> PAYLOAD =
      DataTracker.registerData(BombCartEntity.class, TrackedDataHandlerRegistry.STRING);

  public BombCartEntity(EntityType<? extends BombCartEntity> type, World world) {
    super(type, world);
  }

  @Override
  protected void initDataTracker() {
    super.initDataTracker();
    dataTracker.startTracking(PAYLOAD, "condensed");
  }

  public void setPayload(PayloadType payload) {
    dataTracker.set(PAYLOAD, payload.id());
  }

  public PayloadType getPayload() {
    return DeliveryContent.readPayload(dataTracker.get(PAYLOAD));
  }

  @Override
  public BlockState getDefaultContainedBlock() {
    return IcbmPort.EXPLOSIVE_BLOCKS
        .getOrDefault(getPayload(), IcbmPort.CONDENSED_EXPLOSIVE)
        .getDefaultState();
  }

  @Override
  protected Item getItem() {
    return DeliveryContent.BOMB_CARTS.getOrDefault(getPayload(), Items.TNT_MINECART);
  }

  @Override
  public ItemStack getPickBlockStack() {
    return new ItemStack(getItem());
  }

  @Override
  public void prime() {
    super.prime();
    // Vanilla owns the private fuse and its network status; adapt its stored duration.
    NbtCompound state = new NbtCompound();
    super.writeCustomDataToNbt(state);
    state.putInt("TNTFuse", getPayload().cartFuseTicks());
    super.readCustomDataFromNbt(state);
  }

  @Override
  protected void explode(double power) {
    if (!world.isClient && !detonated) {
      detonated = true;
      PayloadType payload = getPayload();
      discard();
      Payloads.detonate(world, getPos(), payload, this);
    }
  }

  @Override
  public void dropItems(DamageSource source) {
    if (world.isClient) return;
    double speed = getVelocity().horizontalLengthSquared();
    // An intact explosive cart may only be recovered when its payload stays inert.
    if (source.isFire() || source.isExplosive() || speed >= .01 || isPrimed()) {
      explode(speed);
      return;
    }
    if (world.getGameRules().getBoolean(GameRules.DO_ENTITY_DROPS)) dropItem(getItem());
    discard();
  }

  @Override
  public void writeCustomDataToNbt(NbtCompound nbt) {
    super.writeCustomDataToNbt(nbt);
    nbt.putString("Payload", getPayload().id());
  }

  @Override
  public void readCustomDataFromNbt(NbtCompound nbt) {
    NbtCompound state = nbt.copy();
    if (state.contains("TNTFuse"))
      state.putInt("TNTFuse", Math.max(-1, Math.min(72000, state.getInt("TNTFuse"))));
    super.readCustomDataFromNbt(state);
    setPayload(DeliveryContent.readPayload(nbt.getString("Payload")));
  }
}

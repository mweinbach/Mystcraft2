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

  private static final TrackedData<Integer> VISUAL_FUSE =
      DataTracker.registerData(BombCartEntity.class, TrackedDataHandlerRegistry.INTEGER);

  public BombCartEntity(EntityType<? extends BombCartEntity> type, World world) {
    super(type, world);
  }

  @Override
  protected void initDataTracker() {
    super.initDataTracker();
    dataTracker.startTracking(PAYLOAD, "condensed");
    dataTracker.startTracking(VISUAL_FUSE, -1);
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
    if (!world.isClient) dataTracker.set(VISUAL_FUSE, super.getFuseTicks());
  }

  /** Renderer-only view, synchronized from the server's actual private TNT fuse. */
  public int getVisualFuseTicks() {
    return dataTracker.get(VISUAL_FUSE);
  }

  @Override
  public int getFuseTicks() {
    return world.isClient ? getVisualFuseTicks() : super.getFuseTicks();
  }

  @Override
  public void tick() {
    super.tick();
    if (!world.isClient && !isRemoved()) dataTracker.set(VISUAL_FUSE, super.getFuseTicks());
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

  /**
   * Defuser action. Upstream calls {@code killMinecart(DamageSource.GENERIC)}: the cart (primed or
   * not) is recovered as an item and its fuse is lost, while a cart moving at 0.1 blocks/tick or
   * faster detonates. Upstream would also drop the item from a detonating cart; the port detonates
   * without the duplicate.
   */
  public void defuse() {
    if (world.isClient || isRemoved()) return;
    double speed = getVelocity().horizontalLengthSquared();
    if (speed >= .01) {
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
    if (!world.isClient) dataTracker.set(VISUAL_FUSE, super.getFuseTicks());
  }
}

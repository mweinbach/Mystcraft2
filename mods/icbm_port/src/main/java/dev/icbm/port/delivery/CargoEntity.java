package dev.icbm.port.delivery;

import net.minecraft.entity.*;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;

/** Item cargo with upstream parachute .01/.95 drag or balloon .02 lift/.90 drag. */
public final class CargoEntity extends ThrownItemEntity {
  private ItemStack cargo = ItemStack.EMPTY;
  private boolean balloon;
  private int liftTicks = 600;
  private boolean released;

  public CargoEntity(EntityType<? extends CargoEntity> type, World world) {
    super(type, world);
  }

  @Override
  protected Item getDefaultItem() {
    return DeliveryContent.CARGO_PARACHUTE;
  }

  public void setBalloon(boolean balloon) {
    this.balloon = balloon;
  }

  public void setCargo(ItemStack cargo) {
    this.cargo = cargo.copy();
  }

  public ItemStack getCargo() {
    return cargo.copy();
  }

  public int getLiftTicks() {
    return liftTicks;
  }

  public void releaseCargo() {
    if (!world.isClient && !released) {
      released = true;
      if (!cargo.isEmpty()) dropStack(cargo);
      if (!balloon) dropItem(DeliveryContent.CARGO_PARACHUTE);
      cargo = ItemStack.EMPTY;
      discard();
    }
  }

  @Override
  public void tick() {
    baseTick();
    age++;
    var velocity = getVelocity();
    move(MovementType.SELF, velocity);
    double gravity = balloon ? (liftTicks-- > 0 ? -.02 : .005) : .01;
    setVelocity(velocity.multiply(balloon ? .90 : .95).add(0, -gravity, 0));
    if (!world.isClient
        && (onGround
            || horizontalCollision
            || age >= 1200
            || getY() >= world.getTopY() - 1
            || getY() < world.getBottomY()
            || (balloon && liftTicks <= 0 && random.nextFloat() <= .05))) releaseCargo();
  }

  @Override
  public void writeCustomDataToNbt(NbtCompound nbt) {
    super.writeCustomDataToNbt(nbt);
    nbt.put("Cargo", cargo.writeNbt(new NbtCompound()));
    nbt.putBoolean("Balloon", balloon);
    nbt.putInt("LiftTicks", liftTicks);
    nbt.putInt("CarrierAge", age);
  }

  @Override
  public void readCustomDataFromNbt(NbtCompound nbt) {
    super.readCustomDataFromNbt(nbt);
    cargo = ItemStack.fromNbt(nbt.getCompound("Cargo"));
    balloon = nbt.getBoolean("Balloon");
    liftTicks = Math.max(0, Math.min(600, nbt.getInt("LiftTicks")));
    age = Math.max(0, nbt.getInt("CarrierAge"));
  }
}

package dev.icbm.port.delivery;

import dev.icbm.port.simulation.FlyingBlockEntity;
import net.minecraft.entity.*;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Source passenger carrier; passengers persist through vanilla entity-tree serialization. */
public final class CargoEntity extends ThrownItemEntity {
  private ItemStack cargo = ItemStack.EMPTY;
  private boolean balloon, released, deployed;
  private int liftTicks = DeliveryConfig.balloonLiftTicks();
  private CarrierData.Mode mode = CarrierData.Mode.ITEM;

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

  public void setMode(CarrierData.Mode mode) {
    this.mode = mode;
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

  @Override
  protected boolean canAddPassenger(Entity passenger) {
    return !hasPassengers() && DeliveryConfig.entityAllowed(passenger);
  }

  public boolean attachPassenger(Entity passenger) {
    if (world.isClient
        || released
        || passenger.hasVehicle()
        || !DeliveryConfig.entityAllowed(passenger)) return false;
    boolean attached = passenger.startRiding(this, true);
    if (attached) deployed = true;
    return attached;
  }

  public boolean deployCargo() {
    if (world.isClient || deployed || cargo.isEmpty() || !CarrierData.allowed(cargo)) return false;
    Entity passenger = null;
    if (mode == CarrierData.Mode.BLOCK && cargo.getItem() instanceof BlockItem blockItem)
      passenger =
          FlyingBlockEntity.fromCargo(
              (ServerWorld) world,
              blockItem.getBlock().getDefaultState(),
              cargo,
              getPos().add(0, -.7, 0));
    else if (mode == CarrierData.Mode.ENTITY && cargo.getItem() instanceof SpawnEggItem egg) {
      EntityType<?> type = egg.getEntityType(cargo.getNbt());
      passenger = type.create(world);
      if (passenger != null) {
        passenger.setPosition(getPos().add(0, -.7, 0));
        EntityType.loadFromEntityNbt(
            world,
            getOwner() instanceof net.minecraft.entity.player.PlayerEntity p ? p : null,
            passenger,
            cargo.getNbt());
        if (cargo.hasCustomName()) passenger.setCustomName(cargo.getName());
      }
    } else if (mode == CarrierData.Mode.PROJECTILE) {
      var payload = DeliveryContent.payloadForBomblet(cargo);
      if (payload != null) {
        var bomblet = new BombletEntity(DeliveryContent.BOMBLET, world);
        bomblet.setPayload(payload);
        bomblet.setOwner(getOwner());
        passenger = bomblet;
      } else
        for (var entry : DeliveryContent.GRENADES.entrySet())
          if (cargo.isOf(entry.getValue())) {
            var grenade = new GrenadeEntity(DeliveryContent.GRENADE, world);
            grenade.setPayload(entry.getKey());
            grenade.setItem(cargo);
            grenade.setOwner(getOwner());
            passenger = grenade;
            break;
          }
    }
    if (passenger == null || !DeliveryConfig.entityAllowed(passenger))
      passenger = new ItemEntity(world, getX(), getY() - .7, getZ(), cargo.copy());
    passenger.setPosition(getPos().add(0, -.7, 0));
    if (!attachPassenger(passenger)) return false;
    if (!world.spawnEntity(passenger)) {
      passenger.stopRiding();
      deployed = false;
      return false;
    }
    cargo = ItemStack.EMPTY;
    return true;
  }

  @Override
  public void updatePassengerPosition(Entity passenger) {
    if (hasPassenger(passenger)) {
      passenger.setPosition(
          getX(),
          getY() - (passenger instanceof ItemEntity ? .7 : passenger.getHeight() + .2),
          getZ());
      passenger.fallDistance = 0;
    }
  }

  public void releaseCargo() {
    if (!world.isClient && !released) {
      released = true;
      removeAllPassengers();
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
    if (!world.isClient && deployed && !hasPassengers()) {
      releaseCargo();
      return;
    }
    Vec3d velocity = getVelocity();
    Vec3d next = getPos().add(velocity);
    if (!world.isClient
        && (!Double.isFinite(next.x)
            || !Double.isFinite(next.y)
            || !Double.isFinite(next.z)
            || !world.isChunkLoaded(new net.minecraft.util.math.BlockPos(next)))) {
      setVelocity(Vec3d.ZERO);
      return;
    }
    move(MovementType.SELF, velocity);
    double gravity = balloon ? (liftTicks-- > 0 ? -.02 : .005) : .01;
    setVelocity(velocity.multiply(balloon ? .90 : .95).add(0, -gravity, 0));
    getPassengerList().forEach(p -> p.fallDistance = 0);
    boolean cargoLanded =
        getPassengerList().stream()
            .anyMatch(p -> !world.isSpaceEmpty(p, p.getBoundingBox().offset(0, -.05, 0)));
    if (!world.isClient
        && (onGround
            || horizontalCollision
            || cargoLanded
            || age >= (balloon ? Math.max(1200, DeliveryConfig.balloonLiftTicks() + 1200) : 1200)
            || getY() >= world.getTopY() - 1
            || getY() < world.getBottomY()
            || (balloon && liftTicks <= 0 && random.nextFloat() <= .05))) releaseCargo();
  }

  @Override
  public void writeCustomDataToNbt(NbtCompound nbt) {
    super.writeCustomDataToNbt(nbt);
    nbt.put("Cargo", cargo.writeNbt(new NbtCompound()));
    nbt.putBoolean("Balloon", balloon);
    nbt.putBoolean("CargoDeployed", deployed);
    nbt.putString("CargoMode", mode.name());
    nbt.putInt("LiftTicks", liftTicks);
    nbt.putInt("CarrierAge", age);
  }

  @Override
  public void readCustomDataFromNbt(NbtCompound nbt) {
    super.readCustomDataFromNbt(nbt);
    cargo = ItemStack.fromNbt(nbt.getCompound("Cargo"));
    balloon = nbt.getBoolean("Balloon");
    deployed = nbt.getBoolean("CargoDeployed");
    try {
      mode = CarrierData.Mode.valueOf(nbt.getString("CargoMode"));
    } catch (IllegalArgumentException ignored) {
      mode = CarrierData.Mode.ITEM;
    }
    liftTicks = Math.max(0, Math.min(72000, nbt.getInt("LiftTicks")));
    age = Math.max(0, nbt.getInt("CarrierAge"));
  }
}

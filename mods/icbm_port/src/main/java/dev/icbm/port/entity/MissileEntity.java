package dev.icbm.port.entity;

import dev.icbm.port.IcbmPort;
import dev.icbm.port.delivery.ClusterPlan;
import dev.icbm.port.delivery.DeliveryContent;
import dev.icbm.port.payload.PayloadType;
import dev.icbm.port.payload.Payloads;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Server-authoritative game projectile; targeted arcs are bounded Minecraft animations. Riders
 * (from a launcher seat, the rocket launcher or right-clicking) travel with it, as upstream {@code
 * EntityMissile}; a missile carrying a player is never handed to the virtual flight ledger.
 */
public class MissileEntity extends ThrownItemEntity
    implements dev.icbm.port.flight.FlightTracked {
  private static final net.minecraft.entity.data.TrackedData<Integer> VISUAL_STAGE =
      net.minecraft.entity.data.DataTracker.registerData(
          MissileEntity.class, net.minecraft.entity.data.TrackedDataHandlerRegistry.INTEGER);
  private static final net.minecraft.entity.data.TrackedData<Boolean> VISUAL_DISABLED =
      net.minecraft.entity.data.DataTracker.registerData(
          MissileEntity.class, net.minecraft.entity.data.TrackedDataHandlerRegistry.BOOLEAN);

  @Override
  protected void initDataTracker() {
    super.initDataTracker();
    dataTracker.startTracking(VISUAL_STAGE, 0);
    dataTracker.startTracking(VISUAL_DISABLED, false);
  }

  public boolean isVisuallyWarmingUp() {
    return dataTracker.get(VISUAL_STAGE)
        == dev.icbm.port.flight.StagedFlight.Stage.WARMUP.ordinal();
  }

  public boolean isVisuallyDisabled() {
    return dataTracker.get(VISUAL_DISABLED);
  }

  private int flightTicks;
  private int empTicks;
  private PayloadType payload = PayloadType.CONDENSED;
  private boolean cluster;
  private ClusterPlan clusterPlan = ClusterPlan.EMPTY;
  private final dev.icbm.port.flight.StagedFlight flight = new dev.icbm.port.flight.StagedFlight();
  private int fuel = dev.icbm.port.flight.FlightProfile.CRUISE_FUEL;
  private boolean engineFailed;
  private float health = 50;
  private boolean poweredFuel;
  private net.minecraft.util.registry.RegistryKey<World> targetDimension;
  private BlockPos payloadDestination;

  public net.minecraft.util.registry.RegistryKey<World> getTargetDimension() {
    return targetDimension == null ? world.getRegistryKey() : targetDimension;
  }

  public BlockPos getTargetPosition() {
    return payloadDestination;
  }

  public void configurePayloadDestination(
      net.minecraft.util.registry.RegistryKey<World> dimension, BlockPos position) {
    targetDimension = dimension;
    payloadDestination = position.toImmutable();
  }

  @Override
  public dev.icbm.port.flight.StagedFlight flightPlan() {
    return flight;
  }

  public void configureFuel(int ticks) {
    poweredFuel = true;
    fuel = Math.max(0, Math.min(12000, ticks));
  }

  public int getFuel() {
    return fuel;
  }

  public float getMissileHealth() {
    return health;
  }

  @Override
  public boolean canHit() {
    return !isRemoved();
  }

  @Override
  public boolean isAttackable() {
    return true;
  }

  @Override
  public boolean damage(net.minecraft.entity.damage.DamageSource source, float amount) {
    if (world.isClient
        || isRemoved()
        || isInvulnerableTo(source)
        || !Float.isFinite(amount)
        || amount <= 0) return false;
    health -= amount;
    if (health <= 0) {
      onDestroyedBy(source);
      if (!isRemoved()) discard();
    }
    return true;
  }

  /** Upstream {@code EntityMissile#onDestroyedBy}; explosive missiles simply break. */
  protected void onDestroyedBy(net.minecraft.entity.damage.DamageSource source) {}

  public MissileEntity(EntityType<? extends MissileEntity> type, World world) {
    super(type, world);
    setNoGravity(true);
  }

  @Override
  protected Item getDefaultItem() {
    return IcbmPort.CONDENSED_MISSILE;
  }

  public PayloadType getPayload() {
    return payload;
  }

  public void setPayload(PayloadType payload) {
    this.payload = payload.hasContent() ? payload : PayloadType.CONDENSED;
    health = dev.icbm.port.flight.FlightProfile.health(this.payload.tier());
    setItem(
        new ItemStack(
            IcbmPort.MISSILE_ITEMS.getOrDefault(this.payload, IcbmPort.CONDENSED_MISSILE)));
  }

  public void setCluster(boolean cluster) {
    this.cluster = cluster;
    updateClusterItem();
  }

  public boolean isCluster() {
    return cluster;
  }

  public void setClusterPlan(ClusterPlan plan) {
    this.clusterPlan = plan;
    updateClusterItem();
  }

  private void updateClusterItem() {
    if (cluster) {
      ItemStack stack = new ItemStack(DeliveryContent.CLUSTER_MISSILE);
      clusterPlan.writeTo(stack);
      setItem(stack);
    } else
      setItem(
          new ItemStack(IcbmPort.MISSILE_ITEMS.getOrDefault(payload, IcbmPort.CONDENSED_MISSILE)));
  }

  public ClusterPlan getClusterPlan() {
    return clusterPlan;
  }

  public void disableByEmp(int ticks) {
    empTicks = Math.max(empTicks, Math.min(12000, Math.max(0, ticks)));
  }

  public boolean isEmpDisabled() {
    return empTicks > 0;
  }

  /**
   * Upstream EMP {@code DeadFlightLogic}: the engine is gone for good (no thrust or guidance), so
   * the missile falls ballistically, but unlike a temporary EMP disable it still detonates when it
   * hits something.
   */
  public void killEngine() {
    engineFailed = true;
    configureFuel(0);
    flight.clear();
    setNoGravity(false);
  }

  public boolean isEngineFailed() {
    return engineFailed;
  }

  public void configureTarget(BlockPos target) {
    flight.configure(world, getPos(), target);
  }

  @Override
  public void tick() {
    if (!world.isClient) {
      dataTracker.set(VISUAL_STAGE, flight.stage().ordinal());
      dataTracker.set(VISUAL_DISABLED, isEmpDisabled() || engineFailed);
    }
    if (!world.isClient && health <= 0) {
      discard();
      return;
    }
    if (!world.isClient && dev.icbm.port.flight.FlightLedger.suppressDuplicate(this, flight)) {
      discard();
      return;
    }
    if (!world.isClient && isEmpDisabled()) {
      empTicks--;
      flight.clear();
      setNoGravity(empTicks == 0 && fuel > 0);
    }
    if (!world.isClient && engineFailed) {
      flight.clear();
      setNoGravity(false);
    }
    Vec3d motion = getVelocity();
    if (!world.isClient && flight.targeted() && !isEmpDisabled()) {
      motion = flight.nextVelocity(this);
      setVelocity(motion);
    }
    if (!world.isClient && poweredFuel && !flight.targeted() && !isEmpDisabled()) {
      if (fuel > 0) fuel--;
      if (fuel == 0) setNoGravity(false);
    }
    Vec3d next = getPos().add(motion);
    if (!world.isClient) {
      flightTicks++;
      boolean finite =
          Double.isFinite(next.x) && Double.isFinite(next.y) && Double.isFinite(next.z);
      if (!finite
          || flightTicks > (flight.targeted() || poweredFuel ? 12000 : IcbmPort.MISSILE_LIFETIME)
          || next.y < world.getBottomY()
          || !world.getWorldBorder().contains(new BlockPos(next))) {
        discard();
        return;
      }
      // Upstream MissileTrackerHandler never simulates a missile carrying a player. Other riders
      // (a leashed mob, a seat passenger) travel inside the virtual-flight snapshot and reenter
      // with the missile; only riders that cannot be saved keep physical flight too.
      boolean physicalOnly = !dev.icbm.port.flight.FlightLedger.capturable(this);
      if (flight.targeted()
          && !physicalOnly
          && ((next.y >= dev.icbm.port.flight.FlightProfile.exitHeight(world)
                  && flight.stage() != dev.icbm.port.flight.StagedFlight.Stage.DESCENT)
              || !world.isChunkLoaded(new BlockPos(next)))) {
        if (dev.icbm.port.flight.FlightLedger.tryCapture(this, flight)) return;
        // Admission exhaustion is inert and bounded rather than silently loading arbitrary terrain.
        discard();
        return;
      }
      if ((next.y >= world.getTopY() && !physicalOnly) || !world.isChunkLoaded(new BlockPos(next))) {
        discard();
        return;
      }
    }
    super.tick();
    if (!isRemoved() && hasNoGravity()) setVelocity(motion);
    if (!world.isClient
        && !isRemoved()
        && !isEmpDisabled()
        && !engineFailed
        && shouldTriggerInFlight())
      detonate(getPos());
    if (world.isClient && !isVisuallyDisabled())
      world.addParticle(ParticleTypes.SMOKE, getX(), getY(), getZ(), 0, 0, 0);
  }

  /** Cluster airburst timer for untargeted flights, or arrival at a targeted destination. */
  protected boolean shouldTriggerInFlight() {
    return (cluster && flightTicks >= 40 && !flight.targeted()) || flight.arrived();
  }

  protected int flightTicks() {
    return flightTicks;
  }

  protected void detonate(Vec3d position) {
    discard();
    if (cluster) DeliveryContent.burst(world, position, clusterPlan, this, getVelocity());
    else Payloads.detonate(world, position, payload, this);
  }

  /** Never collide with our own riders or with launcher seats (upstream shouldCollideWith). */
  @Override
  protected boolean canHit(net.minecraft.entity.Entity entity) {
    return !(entity instanceof PlayerSeatEntity)
        && !entity.isConnectedThroughVehicle(this)
        && super.canHit(entity);
  }

  /** Upstream processInitialInteract: a player can climb onto a missile. */
  @Override
  public net.minecraft.util.ActionResult interact(
      net.minecraft.entity.player.PlayerEntity player, net.minecraft.util.Hand hand) {
    if (player.shouldCancelInteraction() || hasPassengers() || isRemoved())
      return net.minecraft.util.ActionResult.PASS;
    if (!world.isClient)
      return player.startRiding(this)
          ? net.minecraft.util.ActionResult.CONSUME
          : net.minecraft.util.ActionResult.PASS;
    return net.minecraft.util.ActionResult.SUCCESS;
  }

  @Override
  protected void onCollision(HitResult hit) {
    super.onCollision(hit);
    if (!world.isClient && !isRemoved() && hit.getType() != HitResult.Type.MISS) {
      if (isEmpDisabled()) discard();
      else onImpact(hit.getPos());
    }
  }

  /** Upstream {@code actionOnImpact}: payload missiles detonate where they hit. */
  protected void onImpact(Vec3d position) {
    detonate(position);
  }

  @Override
  public void writeCustomDataToNbt(NbtCompound nbt) {
    super.writeCustomDataToNbt(nbt);
    nbt.putInt("FlightTicks", flightTicks);
    nbt.putInt("EmpTicks", empTicks);
    nbt.putBoolean("EngineFailed", engineFailed);
    nbt.putString("Payload", payload.id());
    nbt.putBoolean("Cluster", cluster);
    clusterPlan.writeTo(nbt);
    if (payloadDestination != null && targetDimension != null) {
      nbt.putLong("PayloadDestination", payloadDestination.asLong());
      nbt.putString("TargetDimension", targetDimension.getValue().toString());
    }
    nbt.put("FlightPlan", flight.writeNbt());
    nbt.putInt("Fuel", fuel);
    nbt.putBoolean("PoweredFuel", poweredFuel);
    nbt.putFloat("MissileHealth", health);
  }

  @Override
  public void readCustomDataFromNbt(NbtCompound nbt) {
    super.readCustomDataFromNbt(nbt);
    flightTicks = Math.max(0, Math.min(12000, nbt.getInt("FlightTicks")));
    empTicks = Math.max(0, Math.min(12000, nbt.getInt("EmpTicks")));
    engineFailed = nbt.getBoolean("EngineFailed");
    setPayload(IcbmPort.payloadById(nbt.getString("Payload")));
    cluster = nbt.getBoolean("Cluster");
    clusterPlan = ClusterPlan.read(nbt);
    updateClusterItem();
    payloadDestination = null;
    targetDimension = null;
    net.minecraft.util.Identifier dimension =
        net.minecraft.util.Identifier.tryParse(nbt.getString("TargetDimension"));
    if (dimension != null && nbt.contains("PayloadDestination"))
      configurePayloadDestination(
          net.minecraft.util.registry.RegistryKey.of(
              net.minecraft.util.registry.Registry.WORLD_KEY, dimension),
          BlockPos.fromLong(nbt.getLong("PayloadDestination")));
    flight.clear();
    if (nbt.contains("FlightPlan")) flight.readNbt(world, nbt.getCompound("FlightPlan"));
    else if (nbt.contains(
        "Target")) { // Upgrade the earlier one-arc prototype save without resetting its target.
      try {
        flight.configure(world, getPos(), BlockPos.fromLong(nbt.getLong("Target")));
      } catch (IllegalArgumentException invalid) {
        flight.clear();
      }
    }
    fuel = nbt.contains("Fuel") ? Math.max(0, Math.min(12000, nbt.getInt("Fuel"))) : 200;
    poweredFuel = nbt.getBoolean("PoweredFuel");
    if (nbt.contains("MissileHealth"))
      health =
          Float.isFinite(nbt.getFloat("MissileHealth"))
              ? Math.max(
                  0,
                  Math.min(
                      dev.icbm.port.flight.FlightProfile.health(payload.tier()),
                      nbt.getFloat("MissileHealth")))
              : dev.icbm.port.flight.FlightProfile.health(payload.tier());
    setNoGravity(!engineFailed && empTicks == 0 && (!poweredFuel || fuel > 0));
  }
}

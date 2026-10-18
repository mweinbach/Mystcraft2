package dev.icbm.port.entity;

import dev.icbm.port.IcbmPort;
import dev.icbm.port.core.GameFlightPath;
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

/** Server-authoritative game projectile; targeted arcs are bounded Minecraft animations. */
public final class MissileEntity extends ThrownItemEntity {
  private int flightTicks;
  private int empTicks;
  private PayloadType payload = PayloadType.CONDENSED;
  private boolean cluster;
  private ClusterPlan clusterPlan = ClusterPlan.EMPTY;
  private Vec3d launchStart;
  private BlockPos target;
  private int duration;
  private int pathTicks;
  private double lift;

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

  public void configureTarget(BlockPos target) {
    Vec3d end = Vec3d.ofCenter(target);
    double distance = getPos().distanceTo(end);
    if (!Double.isFinite(distance)
        || distance > 4096
        || world.isOutOfHeightLimit(target)
        || !world.getWorldBorder().contains(target))
      throw new IllegalArgumentException("Target outside flight bounds");
    this.target = target.toImmutable();
    launchStart = getPos();
    duration = Math.max(2, Math.min(2400, (int) Math.ceil(distance / IcbmPort.MISSILE_SPEED)));
    pathTicks = 0;
    lift =
        Math.max(0, Math.min(distance * .25, world.getTopY() - 2 - Math.max(end.y, launchStart.y)));
  }

  @Override
  public void tick() {
    if (!world.isClient && isEmpDisabled()) {
      empTicks--;
      // Disabled missiles lose their guidance and coast under gravity. Their payload is inert while
      // disabled.
      target = null;
      setNoGravity(empTicks == 0);
    }
    Vec3d motion = getVelocity();
    if (!world.isClient && target != null && !isEmpDisabled()) {
      GameFlightPath.Point point =
          GameFlightPath.sample(
              new GameFlightPath.Point(launchStart.x, launchStart.y, launchStart.z),
              new GameFlightPath.Point(target.getX() + .5, target.getY() + .5, target.getZ() + .5),
              lift,
              ++pathTicks / (double) duration);
      motion = new Vec3d(point.x(), point.y(), point.z()).subtract(getPos());
      setVelocity(motion);
    }
    Vec3d next = getPos().add(motion);
    if (!world.isClient
        && (++flightTicks > (target == null ? IcbmPort.MISSILE_LIFETIME : 2400)
            || !Double.isFinite(next.x)
            || !Double.isFinite(next.y)
            || !Double.isFinite(next.z)
            || next.y < world.getBottomY()
            || next.y >= world.getTopY()
            || !world.getWorldBorder().contains(new BlockPos(next))
            || !world.isChunkLoaded(new BlockPos(next)))) {
      discard();
      return;
    }
    super.tick();
    if (!isRemoved() && hasNoGravity()) setVelocity(motion);
    if (!world.isClient
        && !isRemoved()
        && !isEmpDisabled()
        && ((cluster && flightTicks >= 40) || (target != null && pathTicks >= duration)))
      detonate(getPos());
    if (world.isClient) world.addParticle(ParticleTypes.SMOKE, getX(), getY(), getZ(), 0, 0, 0);
  }

  private void detonate(Vec3d position) {
    discard();
    if (cluster) DeliveryContent.burst(world, position, clusterPlan, this, getVelocity());
    else Payloads.detonate(world, position, payload, this);
  }

  @Override
  protected void onCollision(HitResult hit) {
    super.onCollision(hit);
    if (!world.isClient && !isRemoved() && hit.getType() != HitResult.Type.MISS) {
      if (isEmpDisabled()) discard();
      else detonate(hit.getPos());
    }
  }

  @Override
  public void writeCustomDataToNbt(NbtCompound nbt) {
    super.writeCustomDataToNbt(nbt);
    nbt.putInt("FlightTicks", flightTicks);
    nbt.putInt("EmpTicks", empTicks);
    nbt.putString("Payload", payload.id());
    nbt.putBoolean("Cluster", cluster);
    clusterPlan.writeTo(nbt);
    if (target != null) {
      nbt.putLong("Target", target.asLong());
      nbt.putDouble("StartX", launchStart.x);
      nbt.putDouble("StartY", launchStart.y);
      nbt.putDouble("StartZ", launchStart.z);
      nbt.putInt("Duration", duration);
      nbt.putInt("PathTicks", pathTicks);
      nbt.putDouble("Lift", lift);
    }
  }

  @Override
  public void readCustomDataFromNbt(NbtCompound nbt) {
    super.readCustomDataFromNbt(nbt);
    flightTicks = Math.max(0, Math.min(2400, nbt.getInt("FlightTicks")));
    empTicks = Math.max(0, Math.min(12000, nbt.getInt("EmpTicks")));
    setPayload(IcbmPort.payloadById(nbt.getString("Payload")));
    cluster = nbt.getBoolean("Cluster");
    clusterPlan = ClusterPlan.read(nbt);
    updateClusterItem();
    target = null;
    if (nbt.contains("Target")) {
      Vec3d start =
          new Vec3d(nbt.getDouble("StartX"), nbt.getDouble("StartY"), nbt.getDouble("StartZ"));
      BlockPos end = BlockPos.fromLong(nbt.getLong("Target"));
      double savedLift = nbt.getDouble("Lift");
      if (Double.isFinite(start.x)
          && Double.isFinite(start.y)
          && Double.isFinite(start.z)
          && start.distanceTo(Vec3d.ofCenter(end)) <= 4096
          && !world.isOutOfHeightLimit(end)
          && world.getWorldBorder().contains(end)
          && Double.isFinite(savedLift)) {
        launchStart = start;
        target = end;
        lift = Math.max(0, Math.min(512, savedLift));
        duration = Math.max(2, Math.min(2400, nbt.getInt("Duration")));
        pathTicks = Math.max(0, Math.min(duration, nbt.getInt("PathTicks")));
      }
    }
    setNoGravity(empTicks == 0);
  }
}

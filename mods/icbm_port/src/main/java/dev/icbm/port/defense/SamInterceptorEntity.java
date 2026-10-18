package dev.icbm.port.defense;

import java.util.UUID;
import net.minecraft.entity.*;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.*;

/** Guided, non-explosive interception. Stores target UUID and elapsed life across saves. */
public final class SamInterceptorEntity extends Entity implements FlyingItemEntity {
  private UUID targetUuid;
  private int flightTicks;

  public SamInterceptorEntity(EntityType<? extends SamInterceptorEntity> type, World world) {
    super(type, world);
    setNoGravity(true);
  }

  public void setTarget(Entity target) {
    targetUuid = target.getUuid();
  }

  public UUID getTargetUuid() {
    return targetUuid;
  }

  public int getFlightTicks() {
    return flightTicks;
  }

  @Override
  protected void initDataTracker() {}

  @Override
  public void tick() {
    super.tick();
    if (world.isClient) {
      setPosition(getPos().add(getVelocity()));
      world.addParticle(ParticleTypes.SMOKE, getX(), getY(), getZ(), 0, 0, 0);
      return;
    }
    if (++flightTicks > 100 || targetUuid == null) {
      discard();
      return;
    }
    Entity found = ((ServerWorld) world).getEntity(targetUuid);
    if (found == null
        || !DefenseContent.isTrackedMissile(found)
        || found.isRemoved()
        || DefenseContent.isFlightDisabled(found)
        || squaredDistanceTo(found) > 160 * 160) {
      discard();
      return;
    }
    Entity target = found;
    Vec3d start = getPos();
    Vec3d aim =
        target.getPos().add(target.getVelocity().multiply(Math.min(10, distanceTo(target) / 3.0)));
    Vec3d desired = aim.subtract(start).normalize().multiply(3);
    Vec3d motion = getVelocity().multiply(0.35).add(desired.multiply(0.65));
    Vec3d end = start.add(motion);
    BlockPos next = new BlockPos(end);
    if (!world.isChunkLoaded(next)
        || !world.getWorldBorder().contains(next)
        || end.y < world.getBottomY()
        || end.y >= world.getTopY()) {
      discard();
      return;
    }
    var blockHit =
        world.raycast(
            new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                this));
    if (blockHit.getType() == HitResult.Type.BLOCK) {
      discard();
      return;
    }
    if (segmentDistanceSquared(start, end, target.getPos()) <= 1.5 * 1.5) {
      DefenseContent.neutralizeFlight(target);
      discard();
      return;
    }
    setVelocity(motion);
    setPosition(end);
    velocityDirty = true;
  }

  public static double segmentDistanceSquared(Vec3d start, Vec3d end, Vec3d point) {
    Vec3d segment = end.subtract(start);
    double length = segment.lengthSquared();
    double progress =
        length < 1e-12
            ? 0
            : MathHelper.clamp(point.subtract(start).dotProduct(segment) / length, 0, 1);
    return point.squaredDistanceTo(start.add(segment.multiply(progress)));
  }

  @Override
  protected void writeCustomDataToNbt(NbtCompound nbt) {
    if (targetUuid != null) nbt.putUuid("Target", targetUuid);
    nbt.putInt("FlightTicks", flightTicks);
  }

  @Override
  protected void readCustomDataFromNbt(NbtCompound nbt) {
    targetUuid = nbt.containsUuid("Target") ? nbt.getUuid("Target") : null;
    flightTicks = MathHelper.clamp(nbt.getInt("FlightTicks"), 0, 100);
    setNoGravity(true);
  }

  @Override
  public Packet<?> createSpawnPacket() {
    return new EntitySpawnS2CPacket(this);
  }

  @Override
  public ItemStack getStack() {
    return new ItemStack(Items.FIREWORK_ROCKET);
  }
}

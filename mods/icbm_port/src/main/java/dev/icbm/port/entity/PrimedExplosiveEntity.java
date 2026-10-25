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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class PrimedExplosiveEntity extends Entity implements FlyingItemEntity {
  private static final TrackedData<Integer> FUSE =
      DataTracker.registerData(PrimedExplosiveEntity.class, TrackedDataHandlerRegistry.INTEGER);
  private static final TrackedData<String> PAYLOAD =
      DataTracker.registerData(PrimedExplosiveEntity.class, TrackedDataHandlerRegistry.STRING);

  private java.util.UUID ownerUuid;

  public void setOwner(Entity owner) {
    ownerUuid = owner == null ? null : owner.getUuid();
  }

  public Entity getOwner() {
    return ownerUuid != null && world instanceof net.minecraft.server.world.ServerWorld server
        ? server.getEntity(ownerUuid)
        : null;
  }

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

  /**
   * Upstream {@code EntityExplosive(World, Pos, EnumFacing, ItemStack)} launch: a small random
   * horizontal drift and a 0.2 upward pop.
   */
  public void applyPrimePop() {
    float angle = (float) (random.nextDouble() * Math.PI * 2.0D);
    setVelocity(-Math.sin(angle) * 0.02F, 0.20000000298023224D, -Math.cos(angle) * 0.02F);
  }

  /** Detonation point: upstream {@code posY + 0.5}, the middle of the 0.98-block body. */
  public net.minecraft.util.math.Vec3d detonationPoint() {
    return getPos().add(0, 0.5, 0);
  }

  /**
   * Physics and fuse adapted from MIT ICBM-Classic {@code content/entity/EntityExplosive.java}:
   * 0.95 horizontal drag and 0.045 gravity applied before the move, 0.7/-0.5 ground bounce, and
   * {@code fuse-- < 1}, so a fuse of N ticks detonates on tick N + 1 (zero detonates on the first
   * tick). Upstream also offsets X/Z by 0.5 when detonating; that offset places the blast on a
   * block edge and is not reproduced.
   */
  @Override
  public void tick() {
    super.tick();
    Vec3d motion = getVelocity().multiply(0.95, 1, 0.95);
    if (!hasNoGravity()) motion = motion.add(0, -0.045, 0);
    setVelocity(motion);
    move(MovementType.SELF, getVelocity());
    if (onGround) setVelocity(getVelocity().multiply(0.7, -0.5, 0.7));
    if (!world.isClient) {
      int fuse = getFuse();
      if (fuse < 1) {
        discard();
        Payloads.detonate(world, detonationPoint(), getPayload(), this);
      } else setFuse(fuse - 1);
    } else {
      world.addParticle(ParticleTypes.SMOKE, getX(), getY() + 0.5, getZ(), 0, 0, 0);
      // Upstream EntityExplosive: burning fuses for the heat payloads.
      PayloadType payload = getPayload();
      if (payload == PayloadType.INCENDIARY || payload == PayloadType.EXOTHERMIC)
        world.addParticle(ParticleTypes.LAVA, getX(), getY() + 0.5, getZ(), 0, 0, 0);
    }
  }

  private net.minecraft.util.math.Direction blastDirection;
  private net.minecraft.util.Identifier enderDimension;
  private net.minecraft.util.math.BlockPos enderPosition;

  /** Direction a breaching charge drives into, from the placed block; null when unknown. */
  public net.minecraft.util.math.Direction getBlastDirection() {
    return blastDirection;
  }

  public void setBlastDirection(net.minecraft.util.math.Direction direction) {
    blastDirection = direction;
  }

  /** Destination stored on a placed ender explosive, or null. */
  public net.minecraft.util.math.BlockPos getTargetPosition() {
    return enderPosition;
  }

  public net.minecraft.util.Identifier getTargetDimension() {
    return enderDimension;
  }

  public void setEnderTarget(
      net.minecraft.util.Identifier dimension, net.minecraft.util.math.BlockPos position) {
    enderDimension = dimension;
    enderPosition = position;
  }

  /** Upstream {@code canBeCollidedWith}: players can target (and defuse) a primed explosive. */
  @Override
  public boolean canHit() {
    return !isRemoved();
  }

  /** Upstream {@code canBePushed}. */
  @Override
  public boolean isPushable() {
    return true;
  }

  /**
   * Upstream {@code CapabilityEmpKill}: an EMP removes primed explosives without detonating them.
   */
  public static void registerEmpKill() {
    Payloads.EMP_HANDLERS.add(PrimedExplosiveEntity::empKill);
  }

  /** Removes primed explosives within the configured EMP radius of {@code center}. */
  public static void empKill(net.minecraft.server.world.ServerWorld world, Vec3d center) {
    double radius =
        Math.min(512, dev.icbm.port.payload.PayloadConfig.forType(PayloadType.EMP).radius());
    if (!(radius > 0)) return;
    world
        .getEntitiesByClass(
            PrimedExplosiveEntity.class,
            new net.minecraft.util.math.Box(center, center).expand(radius),
            e -> e.squaredDistanceTo(center) <= radius * radius)
        .forEach(Entity::discard);
  }

  @Override
  protected void readCustomDataFromNbt(NbtCompound nbt) {
    ownerUuid = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
    if (nbt.contains("Fuse")) setFuse(nbt.getInt("Fuse"));
    setPayload(IcbmPort.payloadById(nbt.getString("Payload")));
    blastDirection =
        nbt.contains("Facing", 8)
            ? net.minecraft.util.math.Direction.byName(nbt.getString("Facing"))
            : null;
    enderDimension =
        nbt.contains("EnderDimension", 8)
            ? net.minecraft.util.Identifier.tryParse(nbt.getString("EnderDimension"))
            : null;
    enderPosition =
        enderDimension != null && nbt.contains("EnderPosition", 4)
            ? net.minecraft.util.math.BlockPos.fromLong(nbt.getLong("EnderPosition"))
            : null;
  }

  @Override
  protected void writeCustomDataToNbt(NbtCompound nbt) {
    if (ownerUuid != null) nbt.putUuid("Owner", ownerUuid);
    nbt.putInt("Fuse", getFuse());
    nbt.putString("Payload", getPayload().id());
    if (blastDirection != null) nbt.putString("Facing", blastDirection.getName());
    if (enderDimension != null && enderPosition != null) {
      nbt.putString("EnderDimension", enderDimension.toString());
      nbt.putLong("EnderPosition", enderPosition.asLong());
    }
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

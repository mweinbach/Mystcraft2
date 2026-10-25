package dev.icbm.port.entity;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.block.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.ProjectileDamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import org.jetbrains.annotations.Nullable;

/**
 * Blast fragment projectile for the shrapnel, anvil and fragmentation payloads. Adapted from MIT
 * ICBM-Classic {@code content/entity/EntityFragments.java}.
 *
 * <p>Upstream behaviour kept: 0.99 air drag (0.6 in water), 0.05 gravity, blocks traced two
 * motions ahead, entities ignored for the first five ticks in the air and other fragments always,
 * damage {@code ceil(speed * damage)} as an arrow-type projectile, burning fragments ignite
 * targets for five seconds, a failed hit bounces back at a tenth of the speed, leaves/glass/snow
 * layers are broken through, the fragment lodges on a block and is removed (or explodes) the
 * next tick, explosive fragments explode on any impact or when an entity collides with them
 * during their first two seconds, and anvil fragments use the anvil impact sound.
 *
 * <p>Port adaptations: the blast owner is attributed as the attacker (upstream never set its
 * {@code shootingEntity}); secondary explosions are limited to {@link #MAX_EXPLOSIONS_PER_TICK}
 * per world tick and deferred fragments wait in place; fragments expire after {@link
 * #MAX_LIFETIME} ticks; mode, owner, lodged block and progress are saved.
 */
public class FragmentEntity extends Entity {
  /** Safety lifetime; upstream fragments only end on impact. */
  public static final int MAX_LIFETIME = 1200;

  /** Secondary explosions any one world performs per tick. */
  public static final int MAX_EXPLOSIONS_PER_TICK = 16;

  private static final TrackedData<Boolean> ANVIL =
      DataTracker.registerData(FragmentEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
  private static final TrackedData<Boolean> EXPLOSIVE =
      DataTracker.registerData(FragmentEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
  private static final TrackedData<Boolean> CRITICAL =
      DataTracker.registerData(FragmentEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

  private static final Map<World, long[]> EXPLOSIONS = new WeakHashMap<>();

  private BlockPos inTilePos = BlockPos.ORIGIN;
  @Nullable private BlockState inTile;
  private boolean inGround, exploding, pendingExplosion;
  private int arrowShake, ticksInAir, life;
  private double damage = 11;
  private float explosionSize = 1.5f;
  @Nullable private UUID ownerUuid;
  @Nullable private Entity owner;

  public FragmentEntity(EntityType<? extends FragmentEntity> type, World world) {
    super(type, world);
  }

  public FragmentEntity(World world) {
    this(BlastEntities.FRAGMENT, world);
  }

  @Override
  protected void initDataTracker() {
    dataTracker.startTracking(ANVIL, false);
    dataTracker.startTracking(EXPLOSIVE, false);
    dataTracker.startTracking(CRITICAL, false);
  }

  public boolean isAnvil() {
    return dataTracker.get(ANVIL);
  }

  public FragmentEntity setAnvil(boolean anvil) {
    dataTracker.set(ANVIL, anvil);
    return this;
  }

  public boolean isExplosive() {
    return dataTracker.get(EXPLOSIVE);
  }

  public FragmentEntity setExplosive(boolean explosive) {
    dataTracker.set(EXPLOSIVE, explosive);
    return this;
  }

  public boolean isCritical() {
    return dataTracker.get(CRITICAL);
  }

  public FragmentEntity setCritical(boolean critical) {
    dataTracker.set(CRITICAL, critical);
    return this;
  }

  public double getDamage() {
    return damage;
  }

  public FragmentEntity setDamage(double damage) {
    this.damage = Double.isFinite(damage) ? Math.max(0, damage) : 0;
    return this;
  }

  public float getExplosionSize() {
    return explosionSize;
  }

  public FragmentEntity setExplosionSize(float size) {
    explosionSize = Float.isFinite(size) ? Math.max(0, Math.min(16, size)) : 0;
    return this;
  }

  public boolean isInGround() {
    return inGround;
  }

  public boolean isPendingExplosion() {
    return pendingExplosion;
  }

  public int getLife() {
    return life;
  }

  public void setOwner(@Nullable Entity entity) {
    owner = entity;
    ownerUuid = entity == null ? null : entity.getUuid();
  }

  @Nullable
  public UUID getOwnerUuid() {
    return ownerUuid;
  }

  @Nullable
  public Entity getOwner() {
    if (owner != null && !owner.isRemoved()) return owner;
    if (ownerUuid != null && world instanceof ServerWorld server)
      owner = server.getEntity(ownerUuid);
    return owner;
  }

  /** Upstream {@code setArrowHeading}: normalize, jitter by {@code random}, scale to speed. */
  public void setHeading(double x, double y, double z, float speed, float random) {
    double length = Math.sqrt(x * x + y * y + z * z);
    if (!(length > 1e-9)) return;
    x = x / length + this.random.nextGaussian() * .0075 * random;
    y = y / length + this.random.nextGaussian() * .0075 * random;
    z = z / length + this.random.nextGaussian() * .0075 * random;
    Vec3d velocity = new Vec3d(x * speed, y * speed, z * speed);
    setVelocity(velocity);
    float yaw = (float) Math.toDegrees(Math.atan2(velocity.x, velocity.z));
    float pitch = (float) Math.toDegrees(Math.atan2(velocity.y, velocity.horizontalLength()));
    setYaw(yaw);
    setPitch(pitch);
    prevYaw = yaw;
    prevPitch = pitch;
  }

  @Override
  public void tick() {
    super.tick();
    if (world.isClient) {
      clientTick();
      return;
    }
    if (++life > MAX_LIFETIME) {
      discard();
      return;
    }
    if (pendingExplosion) {
      explode();
      return;
    }
    if (prevPitch == 0 && prevYaw == 0) faceVelocity(false);
    BlockState state = null;
    if (inTile != null && world.isChunkLoaded(inTilePos)) {
      state = world.getBlockState(inTilePos);
      if (!state.isAir()) {
        VoxelShape shape = state.getCollisionShape(world, inTilePos);
        for (Box box : shape.getBoundingBoxes())
          if (box.offset(inTilePos).contains(getPos())) {
            inGround = true;
            break;
          }
      }
    }
    if (arrowShake > 0) arrowShake--;
    if (inGround) {
      if (state != null && state != inTile && blockFree(getBoundingBox().expand(.05))) {
        inGround = false;
        setVelocity(
            getVelocity()
                .multiply(
                    random.nextFloat() * .2f, random.nextFloat() * .2f, random.nextFloat() * .2f));
        ticksInAir = 0;
      } else onImpactGround();
      return;
    }
    ticksInAir++;
    Vec3d start = getPos();
    Vec3d velocity = getVelocity();
    BlockHitResult blockHit =
        world.raycast(
            new RaycastContext(
                start,
                start.add(velocity.multiply(2)),
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                this));
    boolean hitBlock = blockHit.getType() != HitResult.Type.MISS;
    Vec3d end = hitBlock ? blockHit.getPos() : start.add(velocity);
    EntityHitResult entityHit =
        ticksInAir >= 5
            ? ProjectileUtil.getEntityCollision(
                world,
                this,
                start,
                end,
                getBoundingBox().stretch(velocity.multiply(1.2)).expand(1),
                FragmentEntity::canHitEntity)
            : null;
    if (entityHit != null) onEntityHit(entityHit.getEntity());
    else if (hitBlock) onBlockHit(blockHit);
    if (isRemoved() || pendingExplosion) return;
    advance(true);
    checkBlockCollision();
  }

  private static boolean canHitEntity(Entity entity) {
    return !(entity instanceof FragmentEntity) && entity.canHit() && !entity.isSpectator();
  }

  /** Position, rotation, drag and gravity for one tick, shared by server and client. */
  private void advance(boolean server) {
    Vec3d velocity = getVelocity();
    double x = getX() + velocity.x, y = getY() + velocity.y, z = getZ() + velocity.z;
    faceVelocity(true);
    float drag = .99f;
    if (isTouchingWater()) {
      if (!server)
        for (int i = 0; i < 4; i++)
          world.addParticle(
              ParticleTypes.BUBBLE,
              x - velocity.x * .25,
              y - velocity.y * .25,
              z - velocity.z * .25,
              velocity.x,
              velocity.y,
              velocity.z);
      drag = .6f;
    }
    if (isWet()) extinguish();
    velocity = velocity.multiply(drag);
    if (!hasNoGravity()) velocity = velocity.add(0, -.05, 0);
    setVelocity(velocity);
    setPosition(x, y, z);
  }

  private void faceVelocity(boolean smooth) {
    Vec3d v = getVelocity();
    float yaw = (float) Math.toDegrees(MathHelper.atan2(v.x, v.z));
    float pitch = (float) Math.toDegrees(MathHelper.atan2(v.y, v.horizontalLength()));
    if (!smooth) {
      setYaw(yaw);
      setPitch(pitch);
      prevYaw = yaw;
      prevPitch = pitch;
      return;
    }
    while (pitch - prevPitch < -180) prevPitch -= 360;
    while (pitch - prevPitch >= 180) prevPitch += 360;
    while (yaw - prevYaw < -180) prevYaw -= 360;
    while (yaw - prevYaw >= 180) prevYaw += 360;
    setPitch(MathHelper.lerp(.2f, prevPitch, pitch));
    setYaw(MathHelper.lerp(.2f, prevYaw, yaw));
  }

  private void clientTick() {
    if (isCritical()) {
      Vec3d v = getVelocity();
      for (int i = 0; i < 4; i++)
        world.addParticle(
            ParticleTypes.CRIT,
            getX() + v.x * i / 4,
            getY() + v.y * i / 4,
            getZ() + v.z * i / 4,
            -v.x,
            -v.y + .2,
            -v.z);
    }
    advance(false);
  }

  private boolean blockFree(Box box) {
    for (VoxelShape shape : world.getBlockCollisions(this, box))
      if (!shape.isEmpty()) return false;
    return true;
  }

  private void onEntityHit(Entity target) {
    if (!target.isAlive()) {
      discard();
      return;
    }
    float speed = (float) getVelocity().length();
    int amount = MathHelper.ceil(Math.min(Integer.MAX_VALUE, speed * damage));
    DamageSource source = new ProjectileDamageSource("arrow", this, getOwner()).setProjectile();
    onImpactEntity();
    boolean deferred = pendingExplosion;
    if (isOnFire() && !(target instanceof EndermanEntity)) target.setOnFireFor(5);
    if (target.damage(source, amount)) {
      if (!deferred && !(target instanceof EndermanEntity)) discard();
    } else if (!deferred) {
      setVelocity(getVelocity().multiply(-.1));
      setYaw(getYaw() + 180);
      prevYaw += 180;
      ticksInAir = 0;
      if (getVelocity().lengthSquared() < .001) discard();
    }
  }

  private void onBlockHit(BlockHitResult hit) {
    inTilePos = hit.getBlockPos();
    inTile = world.getBlockState(inTilePos);
    // Upstream ProjectileBlockInteraction: leaves, snow layers and glass break as pass-through.
    Material material = inTile.getMaterial();
    if ((material == Material.LEAVES || material == Material.GLASS || material == Material.SNOW_LAYER)
        && inTile.getHardness(world, inTilePos) >= 0) world.breakBlock(inTilePos, true, this);
    Vec3d motion = hit.getPos().subtract(getPos());
    setVelocity(motion);
    double length = motion.length();
    if (length > 0) setPosition(getPos().subtract(motion.multiply(.05 / length)));
    playSound(SoundEvents.ENTITY_ARROW_HIT, 1, 1.2f / (random.nextFloat() * .2f + .9f));
    inGround = true;
    arrowShake = 7;
    if (!inTile.isAir()) inTile.onEntityCollision(world, inTilePos, this);
  }

  private void onImpactEntity() {
    if (isExplosive()) explode();
    else playImpactAudio();
  }

  private void onImpactGround() {
    if (isExplosive()) explode();
    else {
      playImpactAudio();
      discard();
    }
  }

  private void playImpactAudio() {
    if (random.nextFloat() <= .5f) return;
    if (isAnvil())
      world.playSound(
          null, getX(), getY(), getZ(), SoundEvents.BLOCK_ANVIL_HIT, SoundCategory.BLOCKS, 1, 1);
    else playSound(SoundEvents.ENTITY_ARROW_HIT, 1, 1.2f / (random.nextFloat() * .2f + .9f));
  }

  /**
   * Upstream {@code explode()}: a terrain-damaging, non-incendiary explosion of {@code
   * explosionSize}. Over the world's per-tick explosion budget the fragment stops and retries
   * next tick.
   */
  public void explode() {
    if (exploding || !(world instanceof ServerWorld server)) return;
    if (!acquireExplosion(server)) {
      pendingExplosion = true;
      setVelocity(Vec3d.ZERO);
      return;
    }
    exploding = true;
    pendingExplosion = false;
    server.createExplosion(
        this, getX(), getY(), getZ(), explosionSize, Explosion.DestructionType.DESTROY);
    discard();
  }

  private static boolean acquireExplosion(ServerWorld world) {
    long[] slot = EXPLOSIONS.computeIfAbsent(world, w -> new long[] {Long.MIN_VALUE, 0});
    if (slot[0] != world.getTime()) {
      slot[0] = world.getTime();
      slot[1] = 0;
    }
    if (slot[1] >= MAX_EXPLOSIONS_PER_TICK) return false;
    slot[1]++;
    return true;
  }

  /** Secondary explosions already performed in this world during its current tick. */
  public static int explosionsThisTick(ServerWorld world) {
    long[] slot = EXPLOSIONS.get(world);
    return slot == null || slot[0] != world.getTime() ? 0 : (int) slot[1];
  }

  /** Upstream {@code applyEntityCollision}: explosive fragments go off when bumped early. */
  @Override
  public boolean isPushable() {
    return isExplosive() && life < 40 && !isRemoved();
  }

  @Override
  public void pushAwayFrom(Entity entity) {
    if (!world.isClient && isExplosive() && life < 40) explode();
  }

  @Override
  protected MoveEffect getMoveEffect() {
    return MoveEffect.NONE;
  }

  @Override
  protected void writeCustomDataToNbt(NbtCompound nbt) {
    nbt.putByte("shake", (byte) arrowShake);
    nbt.putBoolean("explosive", isExplosive());
    nbt.putBoolean("anvil", isAnvil());
    nbt.putBoolean("critical", isCritical());
    nbt.putDouble("damage", damage);
    nbt.putFloat("explosionSize", explosionSize);
    nbt.putBoolean("inGround", inGround);
    nbt.putBoolean("pendingExplosion", pendingExplosion);
    nbt.putInt("life", life);
    nbt.putInt("ticksInAir", ticksInAir);
    if (inTile != null) {
      nbt.put("inTile", NbtHelper.fromBlockState(inTile));
      nbt.putLong("inTilePos", inTilePos.asLong());
    }
    if (ownerUuid != null) nbt.putUuid("Owner", ownerUuid);
  }

  @Override
  protected void readCustomDataFromNbt(NbtCompound nbt) {
    arrowShake = nbt.getByte("shake") & 255;
    setExplosive(nbt.getBoolean("explosive"));
    setAnvil(nbt.getBoolean("anvil"));
    setCritical(nbt.getBoolean("critical"));
    if (nbt.contains("damage")) setDamage(nbt.getDouble("damage"));
    if (nbt.contains("explosionSize")) setExplosionSize(nbt.getFloat("explosionSize"));
    inGround = nbt.getBoolean("inGround");
    pendingExplosion = nbt.getBoolean("pendingExplosion");
    life = Math.max(0, Math.min(MAX_LIFETIME, nbt.getInt("life")));
    ticksInAir = Math.max(0, Math.min(MAX_LIFETIME, nbt.getInt("ticksInAir")));
    if (nbt.contains("inTile", 10)) {
      inTile = NbtHelper.toBlockState(nbt.getCompound("inTile"));
      inTilePos = BlockPos.fromLong(nbt.getLong("inTilePos"));
    }
    ownerUuid = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
    owner = null;
  }

  @Override
  public Packet<?> createSpawnPacket() {
    Entity shooter = getOwner();
    return new EntitySpawnS2CPacket(this, shooter == null ? 0 : shooter.getId());
  }
}

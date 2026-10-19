package dev.icbm.port.simulation;

import dev.icbm.port.payload.*;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.entity.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.World;

/** Server-thread adaptation of upstream feeding, starvation, pulling and merging logic. */
public final class RedmatterEntity extends Entity implements FlyingItemEntity {
  private static final TrackedData<Float> SIZE =
      DataTracker.registerData(RedmatterEntity.class, TrackedDataHandlerRegistry.FLOAT);
  private static final TrackedData<Integer> SEARCH_RADIUS =
      DataTracker.registerData(RedmatterEntity.class, TrackedDataHandlerRegistry.INTEGER);
  private static final TrackedData<Integer> CAPTURED =
      DataTracker.registerData(RedmatterEntity.class, TrackedDataHandlerRegistry.INTEGER);
  private static final TrackedData<Integer> CONSUMED =
      DataTracker.registerData(RedmatterEntity.class, TrackedDataHandlerRegistry.INTEGER);
  private static final double MAX_SWEPT_STEP = 8;
  private int lived, scanCursor, starved;
  private UUID owner;

  public RedmatterEntity(EntityType<? extends RedmatterEntity> type, World world) {
    super(type, world);
    setNoGravity(true);
    noClip = true;
  }

  @Override
  protected void initDataTracker() {
    dataTracker.startTracking(SIZE, 1f);
    dataTracker.startTracking(SEARCH_RADIUS, 1);
    dataTracker.startTracking(CAPTURED, 0);
    dataTracker.startTracking(CONSUMED, 0);
  }

  public float size() {
    return dataTracker.get(SIZE);
  }

  public void setSize(float size) {
    dataTracker.set(SIZE, Math.max(.25f, Math.min(70, size)));
  }

  public int searchRadius() {
    return dataTracker.get(SEARCH_RADIUS);
  }

  public int capturedBlocks() {
    return dataTracker.get(CAPTURED);
  }

  public int consumedBlocks() {
    return dataTracker.get(CONSUMED);
  }

  public void setOwner(Entity entity) {
    owner = dev.icbm.port.api.ActionSource.of(entity).owner();
  }

  public int livedTicks() {
    return lived;
  }

  @Override
  public ItemStack getStack() {
    return new ItemStack(Items.ENDER_EYE);
  }

  @Override
  public void tick() {
    super.tick();
    if (world.isClient) return;
    ServerWorld server = (ServerWorld) world;
    var config = PayloadConfig.forType(PayloadType.REDMATTER);
    if (!config.enabled() || ++lived >= config.durationTicks()) {
      discard();
      return;
    }
    double radius = Math.min(70, Math.max(searchRadius() + 2, size() * 3));
    int fed = 0;
    var entities = server.getOtherEntities(this, getBoundingBox().expand(radius), Entity::isAlive);
    for (int index = 0; index < Math.min(256, entities.size()); index++) {
      Entity entity = entities.get(index);
      if (entity instanceof PlayerEntity player && (player.isCreative() || player.isSpectator()))
        continue;
      double distance = entity.getPos().distanceTo(getPos());
      if (distance > radius) continue;
      if (entity instanceof RedmatterEntity other) {
        if (distance < Math.max(1, size() * .3) && getId() < other.getId()) {
          setSize(
              (float)
                  Math.min(
                      config.radius(), Math.cbrt(Math.pow(size(), 3) + Math.pow(other.size(), 3))));
          other.discard();
          fed++;
        } else if (distance > .01)
          addVelocity(
              (other.getX() - getX()) * .0005,
              (other.getY() - getY()) * .0005,
              (other.getZ() - getZ()) * .0005);
        continue;
      }
      if (closestApproach(entity) < killRange()) {
        if (entity instanceof FlyingBlockEntity flying) {
          flying.consume();
          dataTracker.set(CONSUMED, Math.min(1000000, consumedBlocks() + 1));
          fed++;
        } else {
          Entity attributed = owner == null ? null : server.getEntity(owner);
          DamageSource damage =
              attributed == null
                  ? DamageSource.OUT_OF_WORLD
                  : new net.minecraft.entity.damage.EntityDamageSource(
                          "icbm_port.redmatter", attributed)
                      .setBypassesArmor();
          entity.damage(damage, (float) config.entityDamage());
          if (entity instanceof ItemEntity) entity.discard();
        }
      } else if (distance > .001) {
        Vec3d pull =
            getPos()
                .subtract(entity.getPos())
                .normalize()
                .multiply(Math.min(1.5, 2 * size() / (distance * distance + 1)));
        entity.addVelocity(pull.x, pull.y, pull.z);
        entity.velocityModified = true;
      }
    }
    // Search expansion must not depend on growth: otherwise clearing the first shell
    // permanently disconnects a small core from all remaining terrain.
    int limit = Math.max(1, Math.min(70, config.radius()));
    int r = Math.min(limit, searchRadius());
    int shell = 24 * r * r + 2;
    scanCursor = Math.floorMod(scanCursor, shell);
    int captured = 0;
    for (int rays = 0; rays < 32 && captured < 4; rays++) {
      BlockPos target = getBlockPos().add(shellOffset(scanCursor, r));
      if (++scanCursor >= shell) {
        scanCursor = 0;
        r = r >= limit ? 1 : r + 1;
        shell = 24 * r * r + 2;
      }
      BlockPos pos = firstCaptureCandidate(server, target);
      if (pos == null) continue;
      Vec3d inward = getPos().subtract(Vec3d.ofCenter(pos)).normalize().multiply(.2);
      FlyingBlockEntity flying = FlyingBlockEntity.capture(server, pos, inward, 40);
      if (flying != null) {
        flying.bindToSingularity(getUuid());
        captured++;
        dataTracker.set(CAPTURED, Math.min(1000000, capturedBlocks() + 1));
      }
    }
    dataTracker.set(SEARCH_RADIUS, r);
    fed += captured;
    if (fed > 0) {
      starved = 0;
      setSize((float) Math.min(Math.max(1, config.radius()), size() + fed * .01));
    } else if (++starved % 20 == 0) {
      if (size() <= .251f) {
        discard();
        return;
      }
      setSize(size() * .99f);
    }
    Vec3d velocity = getVelocity().multiply(.98);
    if (velocity.lengthSquared() > .04) velocity = velocity.normalize().multiply(.2);
    BlockPos next = new BlockPos(getPos().add(velocity));
    if (server.isChunkLoaded(next)
        && !server.isOutOfHeightLimit(next)
        && server.getWorldBorder().contains(next)) move(MovementType.SELF, velocity);
    else velocity = Vec3d.ZERO;
    setVelocity(velocity);
    if (lived % 5 == 0)
      server.spawnParticles(
          ParticleTypes.PORTAL,
          getX(),
          getY(),
          getZ(),
          20,
          size() * .2,
          size() * .2,
          size() * .2,
          .03);
  }

  /** Upstream {@code KILL_SCALE} with its one-block floor. */
  private double killRange() {
    return Math.max(1, size() * .08);
  }

  /**
   * The pull can move an entity farther than the kill diameter in one tick, so the latest movement
   * step is tested instead of only its end point. Steps beyond {@link #MAX_SWEPT_STEP} are
   * teleports or unticked spawns whose previous position is stale.
   */
  private double closestApproach(Entity entity) {
    Vec3d end = entity.getPos();
    Vec3d step = end.subtract(entity.prevX, entity.prevY, entity.prevZ);
    double lengthSquared = step.lengthSquared();
    if (lengthSquared < 1.0E-7 || lengthSquared > MAX_SWEPT_STEP * MAX_SWEPT_STEP)
      return end.distanceTo(getPos());
    double t = MathHelper.clamp(getPos().subtract(end).dotProduct(step) / lengthSquared + 1, 0, 1);
    return end.subtract(step.multiply(1 - t)).distanceTo(getPos());
  }

  private static BlockPos shellOffset(int index, int radius) {
    int side = radius * 2 + 1, inner = side - 2, face = side * side;
    if (index < 2 * face) {
      int x = index < face ? -radius : radius;
      int n = index % face;
      return new BlockPos(x, n % side - radius, n / side - radius);
    }
    index -= 2 * face;
    face = inner * side;
    if (index < 2 * face) {
      int y = index < face ? -radius : radius;
      int n = index % face;
      return new BlockPos(n % inner - radius + 1, y, n / inner - radius);
    }
    index -= 2 * face;
    face = inner * inner;
    int z = index < face ? -radius : radius, n = index % face;
    return new BlockPos(n % inner - radius + 1, n / inner - radius + 1, z);
  }

  private BlockPos firstCaptureCandidate(ServerWorld world, BlockPos target) {
    Vec3d delta = Vec3d.ofCenter(target).subtract(getPos());
    int steps = Math.min(256, Math.max(1, (int) Math.ceil(delta.length() * 2)));
    for (int step = 1; step <= steps; step++) {
      BlockPos pos = new BlockPos(getPos().add(delta.multiply(step / (double) steps)));
      if (!world.isChunkLoaded(pos)
          || world.isOutOfHeightLimit(pos)
          || !world.getWorldBorder().contains(pos)) return null;
      BlockState state = world.getBlockState(pos);
      if (state.getHardness(world, pos) < 0) return null;
      if (!state.isAir() && state.getFluidState().isEmpty()) return pos;
    }
    return null;
  }

  @Override
  protected void writeCustomDataToNbt(NbtCompound n) {
    n.putFloat("size", size());
    n.putInt("lived", lived);
    n.putInt("scan", scanCursor);
    n.putInt("starved", starved);
    n.putInt("searchRadius", searchRadius());
    n.putInt("captured", capturedBlocks());
    n.putInt("consumed", consumedBlocks());
    if (owner != null) n.putUuid("owner", owner);
  }

  @Override
  protected void readCustomDataFromNbt(NbtCompound n) {
    float value = n.getFloat("size");
    setSize(Float.isFinite(value) ? value : 1);
    lived = Math.max(0, Math.min(72000, n.getInt("lived")));
    scanCursor = Math.max(0, n.getInt("scan"));
    starved = Math.max(0, Math.min(72000, n.getInt("starved")));
    dataTracker.set(SEARCH_RADIUS, Math.max(1, Math.min(70, n.getInt("searchRadius"))));
    dataTracker.set(CAPTURED, Math.max(0, Math.min(1000000, n.getInt("captured"))));
    dataTracker.set(CONSUMED, Math.max(0, Math.min(1000000, n.getInt("consumed"))));
    owner = n.containsUuid("owner") ? n.getUuid("owner") : null;
  }

  @Override
  public Packet<?> createSpawnPacket() {
    return new EntitySpawnS2CPacket(this);
  }

  @Override
  public boolean isInvulnerableTo(DamageSource source) {
    return source != DamageSource.OUT_OF_WORLD;
  }
}

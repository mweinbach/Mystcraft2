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
  }

  public float size() {
    return dataTracker.get(SIZE);
  }

  public void setSize(float size) {
    dataTracker.set(SIZE, Math.max(.25f, Math.min(70, size)));
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
    double radius = Math.min(70, Math.max(2, size() * 3));
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
      if (distance < Math.max(.6, size() * .08)) {
        if (entity instanceof FlyingBlockEntity flying) {
          flying.consume();
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
    // Bounded expanding cube-wall scan: captures at most four blocks per tick into conserved
    // entities.
    int r = Math.max(1, (int) Math.ceil(size())), side = 2 * r + 1, total = side * side * side;
    for (int check = 0; check < 128 && fed < 4; check++, scanCursor = (scanCursor + 1) % total) {
      int x = scanCursor % side - r,
          y = scanCursor / side % side - r,
          z = scanCursor / (side * side) - r;
      if (Math.abs(x) != r && Math.abs(y) != r && Math.abs(z) != r) continue;
      BlockPos pos = getBlockPos().add(x, y, z);
      if (!server.isChunkLoaded(pos)
          || server.isOutOfHeightLimit(pos)
          || !server.getWorldBorder().contains(pos)) continue;
      BlockState state = server.getBlockState(pos);
      if (state.isAir() || state.getHardness(server, pos) < 0) continue;
      Vec3d inward = getPos().subtract(Vec3d.ofCenter(pos)).normalize().multiply(.2);
      FlyingBlockEntity captured = FlyingBlockEntity.capture(server, pos, inward, 40);
      if (captured != null) fed++;
    }
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

  @Override
  protected void writeCustomDataToNbt(NbtCompound n) {
    n.putFloat("size", size());
    n.putInt("lived", lived);
    n.putInt("scan", scanCursor);
    n.putInt("starved", starved);
    if (owner != null) n.putUuid("owner", owner);
  }

  @Override
  protected void readCustomDataFromNbt(NbtCompound n) {
    float value = n.getFloat("size");
    setSize(Float.isFinite(value) ? value : 1);
    lived = Math.max(0, Math.min(72000, n.getInt("lived")));
    scanCursor = Math.max(0, n.getInt("scan"));
    starved = Math.max(0, Math.min(72000, n.getInt("starved")));
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

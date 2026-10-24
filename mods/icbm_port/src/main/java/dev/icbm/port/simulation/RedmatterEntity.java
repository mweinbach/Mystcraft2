package dev.icbm.port.simulation;

import dev.icbm.port.payload.*;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

/**
 * A red matter singularity. Adapted from upstream's feeding, starvation, pulling and merging
 * logic, with deliberate departures: terrain is consumed by a growing spherical event horizon
 * (a true sphere, so the crater footprint is a circle) up to the configured radius, and every
 * non-creative entity in its field, players included, is pulled in and killed at the core.
 *
 * <p>Server cost is bounded: horizon growth spends at most {@link #GROWTH_NANOS} and {@link
 * #GROWTH_BLOCKS} per server tick across all cores, at most {@link #DEBRIS_PER_TICK} visual debris
 * blocks are lifted per tick with at most {@link #MAX_DEBRIS} in flight, and the field scans a
 * capped, cached entity list.
 */
public final class RedmatterEntity extends Entity implements FlyingItemEntity {
  private static final TrackedData<Float> SIZE =
      DataTracker.registerData(RedmatterEntity.class, TrackedDataHandlerRegistry.FLOAT);
  private static final TrackedData<Integer> HORIZON =
      DataTracker.registerData(RedmatterEntity.class, TrackedDataHandlerRegistry.INTEGER);
  private static final TrackedData<Integer> CAPTURED =
      DataTracker.registerData(RedmatterEntity.class, TrackedDataHandlerRegistry.INTEGER);
  private static final TrackedData<Integer> CONSUMED =
      DataTracker.registerData(RedmatterEntity.class, TrackedDataHandlerRegistry.INTEGER);

  /** Server time all cores together may spend growing their horizons in one server tick. */
  static final long GROWTH_NANOS = 12_000_000L;

  /** Blocks all cores together may erase in one server tick. */
  static final int GROWTH_BLOCKS = 32768;

  static final int DEBRIS_PER_TICK = 3, MAX_DEBRIS = 48, MAX_FIELD_ENTITIES = 256;
  private static final int MAX_DEFERRED = 4096;

  private static int budgetTick = Integer.MIN_VALUE;
  private static long budgetNanos;
  private static int budgetBlocks;

  private int lived, starved, debrisCursor, inFlight;
  private int maxHorizon = -1;
  private Vec3d anchor;
  private LongArrayList band;
  private int bandIndex;
  private final LongOpenHashSet deferred = new LongOpenHashSet();
  private final SphereEraser.Stats stats = new SphereEraser.Stats();
  /** Sky light opened by horizon shells, written every {@link #SKY_FLUSH_TICKS} ticks. */
  private final SphereEraser.SkyBatch sky = new SphereEraser.SkyBatch();

  static final int SKY_FLUSH_TICKS = 20;
  private List<Entity> field = List.of();
  private UUID owner;

  public RedmatterEntity(EntityType<? extends RedmatterEntity> type, World world) {
    super(type, world);
    setNoGravity(true);
    noClip = true;
  }

  @Override
  protected void initDataTracker() {
    dataTracker.startTracking(SIZE, 1f);
    dataTracker.startTracking(HORIZON, 0);
    dataTracker.startTracking(CAPTURED, 0);
    dataTracker.startTracking(CONSUMED, 0);
  }

  public float size() {
    return dataTracker.get(SIZE);
  }

  public void setSize(float size) {
    dataTracker.set(SIZE, Math.max(.25f, Math.min(70, size)));
  }

  /** Radius of the event horizon: every breakable block within it has been consumed. */
  public int horizonRadius() {
    return dataTracker.get(HORIZON);
  }

  /** The radius the horizon grows to, the configured red matter radius unless overridden. */
  public int horizonLimit() {
    int configured = maxHorizon >= 0 ? maxHorizon : PayloadConfig.forType(PayloadType.REDMATTER).radius();
    return Math.max(0, Math.min(PayloadType.MAX_EXTREME_RADIUS, configured));
  }

  /** Caps this core's horizon, as a scaled blast or a test does. */
  public void setHorizonLimit(double radius) {
    maxHorizon =
        Double.isFinite(radius)
            ? (int) Math.max(0, Math.min(PayloadType.MAX_EXTREME_RADIUS, Math.ceil(radius)))
            : -1;
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
    if (!config.enabled()) {
      discard();
      return;
    }
    if (++lived >= config.durationTicks()) {
      collapse(server);
      return;
    }
    if (anchor == null) anchor = getPos();
    else if (getPos().squaredDistanceTo(anchor) > 4) reanchor(server);
    int limit = horizonLimit();
    if (horizonRadius() < limit) grow(server, limit);
    if (lived % 20 == 0 && !deferred.isEmpty()) eraseLoadedDeferred(server);
    if (lived % SKY_FLUSH_TICKS == 0 && !sky.isEmpty()) sky.flush(server);
    int fed = horizonRadius() < limit ? debris(server, limit) : 0;
    fed += field(server, config.entityDamage(), limit);
    // A larger horizon means a larger, angrier core; feeding adds to it, starving shrinks it.
    float grown = (float) Math.min(70, 1 + horizonRadius() * .15);
    if (fed > 0 || horizonRadius() < limit) {
      starved = 0;
      setSize(Math.max(size(), grown) + fed * .01f);
    } else if (++starved % 20 == 0) {
      if (size() <= .251f) {
        collapse(server);
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
    effects(server);
  }

  /** Grows the horizon one block at a time, at most one band per tick, within the shared budget. */
  private void grow(ServerWorld server, int limit) {
    int tick = server.getServer().getTicks();
    if (budgetTick != tick) {
      budgetTick = tick;
      budgetNanos = 0;
      budgetBlocks = 0;
    }
    if (budgetNanos >= GROWTH_NANOS || budgetBlocks >= GROWTH_BLOCKS) return;
    long started = System.nanoTime();
    int horizon = horizonRadius();
    if (band == null) {
      band = new LongArrayList();
      bandIndex = 0;
      int reach = ((horizon + 1) >> 4) + 1;
      int ccx = (int) Math.floor(anchor.x) >> 4, ccz = (int) Math.floor(anchor.z) >> 4;
      for (int dz = -reach; dz <= reach; dz++)
        for (int dx = -reach; dx <= reach; dx++)
          if (SphereEraser.touches(server, anchor, horizon, horizon + 1, ccx + dx, ccz + dz))
            band.add(ChunkPos.toLong(ccx + dx, ccz + dz));
    }
    long before = stats.blocks;
    while (bandIndex < band.size()) {
      long pos = band.getLong(bandIndex++);
      WorldChunk chunk =
          server
              .getChunkManager()
              .getWorldChunk(ChunkPos.getPackedX(pos), ChunkPos.getPackedZ(pos));
      if (chunk == null) {
        if (deferred.size() < MAX_DEFERRED) deferred.add(pos);
      } else {
        // The first band is a solid ball; later bands are one-block shells around it.
        SphereEraser.eraseChunk(
            server, chunk, anchor, horizon == 0 ? -1 : horizon, horizon + 1, true, stats, sky);
      }
      if (budgetNanos + System.nanoTime() - started >= GROWTH_NANOS
          || budgetBlocks + stats.blocks - before >= GROWTH_BLOCKS) break;
    }
    if (bandIndex >= band.size()) {
      band = null;
      dataTracker.set(HORIZON, horizon + 1);
    }
    budgetNanos += System.nanoTime() - started;
    budgetBlocks += (int) (stats.blocks - before);
  }

  /** Chunks the horizon passed while unloaded are cleared up to the horizon once they load. */
  private void eraseLoadedDeferred(ServerWorld server) {
    int done = 0;
    var iterator = deferred.iterator();
    while (iterator.hasNext() && done < 4) {
      long pos = iterator.nextLong();
      WorldChunk chunk =
          server
              .getChunkManager()
              .getWorldChunk(ChunkPos.getPackedX(pos), ChunkPos.getPackedZ(pos));
      if (chunk == null) continue;
      iterator.remove();
      done++;
      if (SphereEraser.eraseChunk(server, chunk, anchor, -1, horizonRadius(), false, stats) > 0)
        SphereEraser.resend(server, chunk);
    }
  }

  /** Moving cores restart their horizon around the new position; the old crater is finished. */
  private void reanchor(ServerWorld server) {
    sky.flush(server);
    Payloads.registerScar(server, anchor, horizonRadius(), deferred);
    deferred.clear();
    anchor = getPos();
    band = null;
    dataTracker.set(HORIZON, 0);
  }

  private void collapse(ServerWorld server) {
    sky.flush(server);
    if (anchor != null) Payloads.registerScar(server, anchor, horizonRadius(), deferred);
    deferred.clear();
    server.spawnParticles(
        ParticleTypes.REVERSE_PORTAL, getX(), getY(), getZ(), 200, size() * .3, size() * .3, size() * .3, .4);
    discard();
  }

  /** Lifts a few blocks just beyond the horizon so terrain visibly streams into the core. */
  private int debris(ServerWorld server, int limit) {
    if (lived % 100 == 0) {
      UUID id = getUuid();
      double reach = Math.min(limit, horizonRadius()) + 16;
      inFlight =
          server
              .getEntitiesByType(
                  SimulationContent.FLYING_BLOCK,
                  getBoundingBox().expand(reach),
                  e -> id.equals(e.boundTo()))
              .size();
    }
    int captured = 0;
    double from = horizonRadius() + .5, to = Math.min(limit, horizonRadius() + 6);
    for (int ray = 0; ray < 12 && captured < DEBRIS_PER_TICK && inFlight < MAX_DEBRIS; ray++) {
      // Golden-angle spiral directions cover the sphere evenly without randomness; an odd stride
      // visits all 1024 of them while spreading each tick's rays over the whole sphere.
      int index = (debrisCursor++ * 389) & 1023;
      double y = 1 - (index + .5) / 512.0, ring = Math.sqrt(Math.max(0, 1 - y * y));
      double angle = index * 2.399963229728653;
      Vec3d direction = new Vec3d(Math.cos(angle) * ring, y, Math.sin(angle) * ring);
      for (double distance = from; distance <= to; distance += 1) {
        BlockPos pos = new BlockPos(anchor.add(direction.multiply(distance)));
        if (!SphereEraser.inside(anchor, limit, pos)) break;
        if (!server.isChunkLoaded(pos)
            || server.isOutOfHeightLimit(pos)
            || !server.getWorldBorder().contains(pos)) break;
        BlockState state = server.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty()) continue;
        if (state.getHardness(server, pos) < 0) break;
        FlyingBlockEntity flying =
            FlyingBlockEntity.capture(server, pos, direction.multiply(-.2), 40);
        if (flying != null) {
          flying.bindToSingularity(getUuid());
          captured++;
          inFlight++;
          dataTracker.set(CAPTURED, Math.min(1000000, capturedBlocks() + 1));
        }
        break;
      }
    }
    return captured;
  }

  /** Pulls everything in the field toward the core and consumes what reaches it. */
  private int field(ServerWorld server, double damage, int limit) {
    double reach = Math.max(24, Math.min(limit, horizonRadius()) + 24);
    if (lived % 5 == 1 || field.isEmpty()) {
      List<Entity> found =
          server.getOtherEntities(
              this,
              getBoundingBox().expand(reach),
              e ->
                  e.isAlive()
                      && !(e instanceof ServerPlayerEntity)
                      && e.squaredDistanceTo(this) <= reach * reach);
      if (found.size() > MAX_FIELD_ENTITIES) {
        found = new ArrayList<>(found);
        found.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(this)));
        found = found.subList(0, MAX_FIELD_ENTITIES);
      }
      field = found;
    }
    List<Entity> entities = new ArrayList<>(field);
    // Players move every tick, so they are always looked up fresh.
    for (ServerPlayerEntity player : server.getPlayers())
      if (player.isAlive() && player.squaredDistanceTo(this) <= reach * reach) entities.add(player);
    int fed = 0;
    Entity attributed = owner == null ? null : server.getEntity(owner);
    for (Entity entity : entities) {
      if (entity.isRemoved() || !entity.isAlive() || immune(entity)) continue;
      double distance = entity.getPos().distanceTo(getPos());
      if (entity instanceof RedmatterEntity other) {
        fed += merge(other, distance);
        continue;
      }
      if (reached(entity)) {
        if (entity instanceof FlyingBlockEntity flying) {
          if (getUuid().equals(flying.boundTo())) inFlight = Math.max(0, inFlight - 1);
          flying.consume();
          dataTracker.set(CONSUMED, Math.min(1000000, consumedBlocks() + 1));
          fed++;
        } else if (entity instanceof LivingEntity living) {
          living.damage(new RedmatterDamage(attributed), (float) damage);
        } else if (!entity.isInvulnerable()) {
          entity.discard();
        }
        continue;
      }
      if (entity instanceof FlyingBlockEntity flying && flying.boundTo() != null) continue;
      if (distance < .001) continue;
      double falloff = 1 - Math.min(1, distance / reach);
      double pull = .02 + .18 * falloff * falloff;
      Vec3d velocity = entity.getVelocity().add(getPos().subtract(entity.getPos()).multiply(pull / distance));
      if (velocity.lengthSquared() > 9) velocity = velocity.normalize().multiply(3);
      entity.setVelocity(velocity);
      entity.fallDistance = 0;
      if (entity instanceof PlayerEntity) entity.velocityModified = true;
    }
    return fed;
  }

  /** Creative and spectator players are immune, the vanilla convention for hazards. */
  public static boolean immune(Entity entity) {
    return entity instanceof PlayerEntity player && (player.isCreative() || player.isSpectator());
  }

  private int merge(RedmatterEntity other, double distance) {
    if (distance < Math.max(1, size() * .3) && getId() < other.getId()) {
      setSize((float) Math.cbrt(Math.pow(size(), 3) + Math.pow(other.size(), 3)));
      maxHorizon = Math.max(horizonLimit(), other.horizonLimit());
      // The survivor's sphere has another anchor, so it would never redo the chunks the absorbed
      // core's horizon passed while they were unloaded; finish that crater as a scar instead.
      if (other.anchor != null && world instanceof ServerWorld server)
        Payloads.registerScar(server, other.anchor, other.horizonRadius(), other.deferred);
      other.deferred.clear();
      other.discard();
      return 1;
    }
    if (distance > .01)
      addVelocity(
          (other.getX() - getX()) * .0005,
          (other.getY() - getY()) * .0005,
          (other.getZ() - getZ()) * .0005);
    return 0;
  }

  /** Upstream {@code KILL_SCALE} with a floor, tested against the entity's body and latest step. */
  private boolean reached(Entity entity) {
    double range = Math.max(1, size() * .08);
    if (closestApproach(entity) < range) return true;
    Box box = entity.getBoundingBox();
    Vec3d core = getPos();
    double dx = MathHelper.clamp(core.x, box.minX, box.maxX) - core.x;
    double dy = MathHelper.clamp(core.y, box.minY, box.maxY) - core.y;
    double dz = MathHelper.clamp(core.z, box.minZ, box.maxZ) - core.z;
    return entity instanceof LivingEntity && dx * dx + dy * dy + dz * dz < range * range;
  }

  /**
   * The pull can move an entity farther than the kill diameter in one tick, so its latest physical
   * step is tested instead of only its end point. Teleports and other repositioning have no step
   * and keep upstream's end-point test.
   */
  private double closestApproach(Entity entity) {
    Vec3d end = entity.getPos();
    Vec3d start = MovementTrace.stepStart(entity);
    if (start == null) return end.distanceTo(getPos());
    Vec3d step = end.subtract(start);
    double lengthSquared = step.lengthSquared();
    if (lengthSquared < 1.0E-7) return end.distanceTo(getPos());
    double t = MathHelper.clamp(getPos().subtract(start).dotProduct(step) / lengthSquared, 0, 1);
    return start.add(step.multiply(t)).distanceTo(getPos());
  }

  private void effects(ServerWorld server) {
    if (lived % 2 != 0) return;
    double reach = 160 + horizonRadius();
    float spread = Math.max(.3f, size() * .15f);
    for (ServerPlayerEntity player : server.getPlayers()) {
      if (player.squaredDistanceTo(this) > reach * reach) continue;
      // Portal particles fly from their offset back to their origin, so they stream inward.
      server.spawnParticles(
          player, ParticleTypes.PORTAL, true, getX(), getY(), getZ(), 48, spread, spread, spread, 2 + size() * .25);
      if (lived % 10 == 0)
        server.spawnParticles(
            player, ParticleTypes.SQUID_INK, true, getX(), getY(), getZ(), 12, spread, spread, spread, .05);
    }
    if (lived % 40 == 0)
      server.playSound(
          null,
          getX(),
          getY(),
          getZ(),
          SoundEvents.BLOCK_PORTAL_AMBIENT,
          SoundCategory.HOSTILE,
          4 + size() / 4,
          .5f);
  }

  @Override
  public boolean shouldRender(double distance) {
    double reach = 256 + horizonRadius();
    return distance < reach * reach;
  }

  @Override
  protected void writeCustomDataToNbt(NbtCompound n) {
    n.putFloat("size", size());
    n.putInt("lived", lived);
    n.putInt("starved", starved);
    n.putInt("horizon", horizonRadius());
    n.putInt("maxHorizon", maxHorizon);
    n.putInt("debris", debrisCursor);
    if (anchor != null) {
      n.putDouble("anchorX", anchor.x);
      n.putDouble("anchorY", anchor.y);
      n.putDouble("anchorZ", anchor.z);
    }
    n.put("deferred", new NbtLongArray(deferred.toLongArray()));
    n.putInt("captured", capturedBlocks());
    n.putInt("consumed", consumedBlocks());
    if (owner != null) n.putUuid("owner", owner);
  }

  @Override
  protected void readCustomDataFromNbt(NbtCompound n) {
    float value = n.getFloat("size");
    setSize(Float.isFinite(value) ? value : 1);
    lived = Math.max(0, Math.min(72000, n.getInt("lived")));
    starved = Math.max(0, Math.min(72000, n.getInt("starved")));
    debrisCursor = Math.max(0, n.getInt("debris"));
    maxHorizon =
        n.contains("maxHorizon")
            ? Math.max(-1, Math.min(PayloadType.MAX_EXTREME_RADIUS, n.getInt("maxHorizon")))
            : -1;
    dataTracker.set(
        HORIZON, Math.max(0, Math.min(PayloadType.MAX_EXTREME_RADIUS, n.getInt("horizon"))));
    anchor = null;
    if (n.contains("anchorX")) {
      Vec3d saved = new Vec3d(n.getDouble("anchorX"), n.getDouble("anchorY"), n.getDouble("anchorZ"));
      if (Double.isFinite(saved.x + saved.y + saved.z)
          && Math.abs(saved.x) <= 30000000
          && Math.abs(saved.z) <= 30000000) anchor = saved;
    }
    deferred.clear();
    for (long pos : n.getLongArray("deferred")) if (deferred.size() < MAX_DEFERRED) deferred.add(pos);
    band = null;
    dataTracker.set(CAPTURED, Math.max(0, Math.min(1000000, n.getInt("captured"))));
    dataTracker.set(CONSUMED, Math.max(0, Math.min(1000000, n.getInt("consumed"))));
    owner = n.containsUuid("owner") ? n.getUuid("owner") : null;
  }

  @Override
  public Packet<?> createSpawnPacket() {
    return new EntitySpawnS2CPacket(this);
  }

  @Override
  public void remove(RemovalReason reason) {
    // Discarded, killed or unloaded: never leave a stretch of the crater without its sky light.
    if (world instanceof ServerWorld server && !sky.isEmpty()) sky.flush(server);
    super.remove(reason);
  }

  @Override
  public boolean isInvulnerableTo(DamageSource source) {
    return source != DamageSource.OUT_OF_WORLD;
  }
}

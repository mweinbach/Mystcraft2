package dev.icbm.port.entity;

import dev.icbm.port.block.LauncherBlock;
import dev.icbm.port.launcher.LauncherBlockEntity;
import java.util.List;
import java.util.Optional;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Invisible placeholder a player mounts to sit on a missile loaded in a launcher base.
 *
 * <p>Adapted from MIT ICBM-Classic {@code content/entity/EntityPlayerSeat.java} and the seat
 * handling in {@code content/blocks/launcher/base/TileLauncherBase.java} ({@code updateSeat}, every
 * three ticks) and {@code LauncherCapability.java} (riders move onto the launched missile). The
 * seat follows the launcher's launch face and upstream's default seat side, stays solid while
 * empty (0.5 x 2.5 column over the missile, 0.5 cube while ridden), cannot be pushed, damaged or
 * moved, persists the host position, and removes itself (dismounting riders) when the launcher is
 * broken, emptied, replaced or the seat falls below the world.
 */
public final class PlayerSeatEntity extends Entity {
  private static final TrackedData<Optional<BlockPos>> HOST =
      DataTracker.registerData(PlayerSeatEntity.class, TrackedDataHandlerRegistry.OPTIONAL_BLOCK_POS);

  /** Upstream {@code TickAction(3, ...)} cadence for seat creation/removal. */
  public static final int UPDATE_INTERVAL = 3;

  private float offsetX, offsetY, offsetZ;
  private Direction prevFace;
  private Direction prevRotation;
  private boolean prevRiding;

  public PlayerSeatEntity(EntityType<? extends PlayerSeatEntity> type, World world) {
    super(type, world);
    noClip = true;
  }

  @Override
  protected void initDataTracker() {
    dataTracker.startTracking(HOST, Optional.empty());
  }

  public void setHost(BlockPos host) {
    dataTracker.set(HOST, Optional.ofNullable(host).map(BlockPos::toImmutable));
    prevFace = null;
  }

  public BlockPos getHostPos() {
    return dataTracker.get(HOST).orElse(null);
  }

  /** Launch face of the host, falling back to upward when the host block is not a launcher. */
  public Direction launchFace() {
    BlockPos host = getHostPos();
    if (host == null) return Direction.UP;
    BlockState state = world.getBlockState(host);
    return state.getBlock() instanceof LauncherBlock && state.contains(LauncherBlock.FACING)
        ? state.get(LauncherBlock.FACING)
        : Direction.UP;
  }

  /** Upstream {@code TileLauncherBase#getSeatSide()} default for each launch face. */
  public static Direction seatSide(Direction face) {
    return face.getAxis() == Direction.Axis.Y ? Direction.NORTH : Direction.UP;
  }

  @Nullable
  private LauncherBlockEntity host() {
    BlockPos host = getHostPos();
    if (host == null || !world.isChunkLoaded(host)) return null;
    return world.getBlockEntity(host) instanceof LauncherBlockEntity launcher
            && hostsSeat(world.getBlockState(host))
        ? launcher
        : null;
  }

  /** Only tiered launcher bases (upstream {@code TileLauncherBase}) carry seats. */
  public static boolean hostsSeat(BlockState state) {
    return state.getBlock() instanceof LauncherBlock launcher && launcher.tier > 0 && !launcher.cruise;
  }

  @Override
  public void tick() {
    super.tick();
    if (!world.isClient) {
      LauncherBlockEntity host = host();
      if (host == null || host.getStack(0).isEmpty() || getY() < world.getBottomY() - 64) {
        removeAllPassengers();
        discard();
        return;
      }
    }
    Direction face = launchFace();
    Direction rotation = seatSide(face);
    if (face != prevFace || rotation != prevRotation || prevRiding != hasPassengers()) {
      prevFace = face;
      prevRotation = rotation;
      prevRiding = hasPassengers();
      updatePosition(face, rotation);
    }
  }

  private void updatePosition(Direction face, Direction rotation) {
    BlockPos host = getHostPos();
    if (host == null) return;
    float width = 0.5F;
    float height = hasPassengers() ? 0.5F : 2.5F;
    float boxWidth = face.getAxis() == Direction.Axis.Y ? width : height;
    offsetX = rotation.getOffsetX() * 0.2F + face.getOffsetX() * 0.5F;
    offsetY = rotation.getOffsetY() * 0.2F + face.getOffsetY() * 0.5F;
    offsetZ = rotation.getOffsetZ() * 0.2F + face.getOffsetZ() * 0.5F;
    switch (face) {
      case DOWN -> offsetY -= height;
      case EAST -> {
        offsetX += boxWidth / 2;
        offsetY -= 0.6F;
      }
      case WEST -> {
        offsetX -= boxWidth / 2;
        offsetY -= 0.6F;
      }
      case NORTH -> {
        offsetZ -= boxWidth / 2;
        offsetY -= 0.6F;
      }
      case SOUTH -> {
        offsetZ += boxWidth / 2;
        offsetY -= 0.6F;
      }
      default -> {}
    }
    setPosition(
        host.getX() + 0.5 + face.getOffsetX() * 0.5,
        host.getY() + 0.5 + face.getOffsetY() * 0.5,
        host.getZ() + 0.5 + face.getOffsetZ() * 0.5);
    setYaw(rotation.getAxis() == Direction.Axis.Y ? 0 : rotation.asRotation());
    setPitch(rotation == Direction.UP ? -90 : rotation == Direction.DOWN ? 90 : 0);
    prevYaw = getYaw();
    prevPitch = getPitch();
  }

  /** Oriented column from upstream {@code updateBox}: it grows out of the launch face. */
  @Override
  protected Box calculateBoundingBox() {
    Direction face = prevFace == null ? Direction.UP : prevFace;
    double a = 0.25;
    double b = hasPassengers() ? 0.5 : 2.5;
    double x = getX(), y = getY(), z = getZ();
    return switch (face) {
      case UP -> new Box(x - a, y, z - a, x + a, y + b, z + a);
      case DOWN -> new Box(x - a, y - b, z - a, x + a, y, z + a);
      case EAST -> new Box(x, y - a, z - a, x + b, y + a, z + a);
      case WEST -> new Box(x - b, y - a, z - a, x, y + a, z + a);
      case NORTH -> new Box(x - a, y - a, z - b, x + a, y + a, z);
      case SOUTH -> new Box(x - a, y - a, z, x + a, y + a, z + b);
    };
  }

  @Override
  public void move(MovementType type, Vec3d movement) {}

  @Override
  public void addVelocity(double x, double y, double z) {}

  @Override
  public void pushAwayFrom(Entity entity) {}

  @Override
  public boolean isPushable() {
    return false;
  }

  @Override
  public boolean isPushedByFluids() {
    return false;
  }

  @Override
  public boolean isImmuneToExplosion() {
    return true;
  }

  @Override
  public boolean damage(DamageSource source, float amount) {
    return false;
  }

  /** Solid while empty, like upstream's collision box; riders never collide with it. */
  @Override
  public boolean isCollidable() {
    return !hasPassengers();
  }

  @Override
  public boolean canHit() {
    return !isRemoved();
  }

  @Override
  public double getMountedHeightOffset() {
    return offsetY;
  }

  @Override
  public void updatePassengerPosition(Entity passenger) {
    if (!hasPassenger(passenger) || getHostPos() == null) return;
    Direction face = prevFace == null ? Direction.UP : prevFace;
    double heightOffset = passenger.getHeightOffset();
    passenger.setPosition(
        getX() + offsetX + heightOffset * face.getOffsetX(),
        getY() + offsetY + heightOffset * face.getOffsetY(),
        getZ() + offsetZ + heightOffset * face.getOffsetZ());
  }

  /** Upstream guards against riders warping when dismounting; keep them where they sat. */
  @Override
  public Vec3d updatePassengerForDismount(LivingEntity passenger) {
    Vec3d current = passenger.getPos();
    BlockPos at = new BlockPos(current);
    return world.getBlockState(at).getCollisionShape(world, at).isEmpty()
        ? current
        : super.updatePassengerForDismount(passenger);
  }

  @Override
  public ActionResult interact(PlayerEntity player, Hand hand) {
    if (player.shouldCancelInteraction()) return ActionResult.PASS;
    if (hasPassengers()) return ActionResult.SUCCESS;
    if (!world.isClient) {
      if (player.getAbilities().creativeMode && player.getStackInHand(hand).isOf(Items.MINECART)) {
        // Upstream creative testing aid: a minecart can occupy the seat instead of the player.
        AbstractMinecartEntity cart =
            AbstractMinecartEntity.create(
                world, getX(), getY(), getZ(), AbstractMinecartEntity.Type.RIDEABLE);
        if (world.spawnEntity(cart)) cart.startRiding(this, true);
        return ActionResult.CONSUME;
      }
      return player.startRiding(this) ? ActionResult.CONSUME : ActionResult.PASS;
    }
    return ActionResult.SUCCESS;
  }

  @Override
  protected void readCustomDataFromNbt(NbtCompound nbt) {
    setHost(nbt.contains("HostPos") ? BlockPos.fromLong(nbt.getLong("HostPos")) : null);
  }

  @Override
  protected void writeCustomDataToNbt(NbtCompound nbt) {
    BlockPos host = getHostPos();
    if (host != null) nbt.putLong("HostPos", host.asLong());
  }

  @Override
  public Packet<?> createSpawnPacket() {
    return new EntitySpawnS2CPacket(this);
  }

  // ---- Launcher integration ----------------------------------------------------------------

  /** Seats attached to {@code host}; normally zero or one. */
  public static List<PlayerSeatEntity> seatsFor(World world, BlockPos host) {
    return world.getEntitiesByClass(
        PlayerSeatEntity.class,
        new Box(host).expand(3),
        seat -> !seat.isRemoved() && host.equals(seat.getHostPos()));
  }

  /**
   * Upstream {@code TileLauncherBase#updateSeat}: create a seat while a missile is loaded, remove
   * it (dismounting riders) once the launcher is empty. Duplicate seats from old saves are pruned.
   */
  public static void syncLauncherSeat(ServerWorld world, BlockPos pos, LauncherBlockEntity host) {
    List<PlayerSeatEntity> seats = seatsFor(world, pos);
    boolean loaded = !host.getStack(0).isEmpty() && hostsSeat(world.getBlockState(pos));
    if (!loaded) {
      removeSeats(world, pos);
      return;
    }
    if (seats.isEmpty()) {
      PlayerSeatEntity seat = new PlayerSeatEntity(EntityContent.PLAYER_SEAT, world);
      seat.setHost(pos);
      seat.setPosition(Vec3d.ofCenter(pos));
      seat.updatePosition(seat.launchFace(), seatSide(seat.launchFace()));
      world.spawnEntity(seat);
      return;
    }
    for (int i = 1; i < seats.size(); i++) {
      PlayerSeatEntity extra = seats.get(i);
      if (!extra.hasPassengers()) extra.discard();
    }
  }

  public static void removeSeats(World world, BlockPos pos) {
    for (PlayerSeatEntity seat : seatsFor(world, pos)) {
      seat.removeAllPassengers();
      seat.discard();
    }
  }

  /**
   * Upstream {@code LauncherCapability}: after a successful launch, every seat rider starts riding
   * the missile. Returns the number of riders moved.
   */
  public static int transferRiders(World world, BlockPos pos, Entity missile) {
    int moved = 0;
    for (PlayerSeatEntity seat : seatsFor(world, pos)) {
      for (Entity rider : List.copyOf(seat.getPassengerList())) {
        rider.stopRiding();
        if (missile.getPassengerList().isEmpty() && rider.startRiding(missile, true)) moved++;
      }
      seat.discard();
    }
    return moved;
  }
}

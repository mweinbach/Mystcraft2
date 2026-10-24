package dev.icbm.port.simulation;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.*;
import net.minecraft.entity.data.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** Captured block ownership transfers world -> entity -> placed block or single item. */
public final class FlyingBlockEntity extends Entity implements FlyingItemEntity {
  private static final TrackedData<Integer> STATE =
      DataTracker.registerData(FlyingBlockEntity.class, TrackedDataHandlerRegistry.INTEGER);
  private NbtCompound blockEntityData;
  private BlockPos origin = BlockPos.ORIGIN;
  private int ticks, liftTicks;
  private boolean completed;
  private java.util.UUID singularity;
  private int coreLoadGrace;

  public FlyingBlockEntity(EntityType<? extends FlyingBlockEntity> type, World world) {
    super(type, world);
  }

  @Override
  protected void initDataTracker() {
    dataTracker.startTracking(STATE, Block.getRawIdFromState(Blocks.STONE.getDefaultState()));
  }

  public BlockState blockState() {
    return Block.getStateFromRawId(dataTracker.get(STATE));
  }

  public boolean completed() {
    return completed;
  }

  public int remainingLiftTicks() {
    return liftTicks;
  }

  @Override
  public ItemStack getStack() {
    return new ItemStack(blockState().getBlock());
  }

  @Nullable
  public static FlyingBlockEntity capture(
      ServerWorld world, BlockPos pos, Vec3d velocity, int liftTicks) {
    if (!world.isChunkLoaded(pos)
        || world.isOutOfHeightLimit(pos)
        || !world.getWorldBorder().contains(pos)) return null;
    BlockState state = world.getBlockState(pos);
    if (state.isAir() || !state.getFluidState().isEmpty() || state.getHardness(world, pos) < 0)
      return null;
    // Linked structures need an atomic multi-block transaction; do not split inventories or doors.
    if (state.getBlock() instanceof DoorBlock
        || state.getBlock() instanceof BedBlock
        || state.getBlock() instanceof net.minecraft.block.PistonExtensionBlock) return null;
    if (state.contains(net.minecraft.state.property.Properties.CHEST_TYPE)
        && state.get(net.minecraft.state.property.Properties.CHEST_TYPE)
            != net.minecraft.block.enums.ChestType.SINGLE) return null;
    FlyingBlockEntity entity = new FlyingBlockEntity(SimulationContent.FLYING_BLOCK, world);
    entity.dataTracker.set(STATE, Block.getRawIdFromState(state));
    entity.origin = pos.toImmutable();
    entity.refreshPositionAndAngles(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
    entity.setVelocity(velocity);
    entity.liftTicks = Math.max(0, Math.min(72000, liftTicks));
    BlockEntity be = world.getBlockEntity(pos);
    entity.blockEntityData = be == null ? null : be.createNbtWithId();
    if (!world.spawnEntity(entity)) return null;
    // Remove BE first: vanilla onStateReplaced must not scatter a second copy of its inventory.
    if (be != null) world.removeBlockEntity(pos);
    if (!world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3)) {
      entity.completed = true;
      entity.discard();
      if (be != null) world.addBlockEntity(be);
      return null;
    }
    return entity;
  }

  /** Builds cargo without consuming a world block; caller owns spawning/stack consumption. */
  public static FlyingBlockEntity fromCargo(
      ServerWorld world, BlockState state, ItemStack stack, Vec3d position) {
    FlyingBlockEntity entity = new FlyingBlockEntity(SimulationContent.FLYING_BLOCK, world);
    entity.dataTracker.set(STATE, Block.getRawIdFromState(state));
    entity.origin = new BlockPos(position);
    entity.refreshPositionAndAngles(position.x, position.y, position.z, 0, 0);
    if (stack.getSubNbt("BlockEntityTag") != null)
      entity.blockEntityData = stack.getSubNbt("BlockEntityTag").copy();
    return entity;
  }

  /** The red matter core this block is falling into, if any. */
  @Nullable
  public java.util.UUID boundTo() {
    return singularity;
  }

  public void bindToSingularity(java.util.UUID id) {
    singularity = id;
    coreLoadGrace = 0;
  }

  @Override
  public void tick() {
    super.tick();
    if (world.isClient || hasVehicle()) return;
    if (completed) {
      discard();
      return;
    }
    Entity attracting = singularity == null ? null : ((ServerWorld) world).getEntity(singularity);
    if (attracting instanceof RedmatterEntity core && !core.isRemoved()) {
      coreLoadGrace = 0;
      Vec3d delta = core.getPos().subtract(getPos());
      // Fast enough to cross a large event horizon; never overshoots the core.
      double speed = Math.min(3, delta.length() * .4);
      setVelocity(delta.lengthSquared() < .0001 ? Vec3d.ZERO : delta.normalize().multiply(speed));
      // Captured cargo belongs to the core, so intervening terrain cannot cause it
      // to land and recreate the crater while it is being drawn inward.
      noClip = true;
      liftTicks = Math.max(2, liftTicks);
    } else if (singularity != null && coreLoadGrace > 0) {
      // Entity NBT can load before its owning core's chunk/entity. Preserve the
      // transfer briefly without moving cargo or forcing either chunk to load.
      coreLoadGrace--;
      noClip = true;
      liftTicks = Math.max(2, liftTicks);
      setVelocity(Vec3d.ZERO);
      return;
    } else {
      singularity = null;
      noClip = false;
    }
    ticks++;
    if (liftTicks > 0) liftTicks--;
    Vec3d next = getPos().add(getVelocity());
    if (!world.isChunkLoaded(new BlockPos(next))
        || world.isOutOfHeightLimit(new BlockPos(next))
        || !world.getWorldBorder().contains(new BlockPos(next))) {
      settle();
      return;
    }
    if (liftTicks == 0) setVelocity(getVelocity().add(0, -.04, 0));
    move(MovementType.SELF, getVelocity());
    setVelocity(getVelocity().multiply(.98));
    if ((liftTicks == 0 && (isOnGround() || horizontalCollision || verticalCollision))
        || (ticks >= 4800 && liftTicks == 0)
        || ticks >= 72000) settle();
  }

  /** Resolve only once, also safe when invoked by collision handlers and tests. */
  public void settle() {
    if (world.isClient || completed) return;
    BlockPos target = getBlockPos();
    if (!world.isChunkLoaded(target)) return;
    completed = true;
    BlockState state = blockState();
    boolean placed = false;
    for (BlockPos candidate : new BlockPos[] {target, target.up()}) {
      if (world.isOutOfHeightLimit(candidate) || !world.getWorldBorder().contains(candidate))
        continue;
      if (world.getBlockState(candidate).getMaterial().isReplaceable()
          && state.canPlaceAt(world, candidate)
          && world.setBlockState(candidate, state, 3)) {
        restoreBlockEntity(candidate);
        placed = true;
        break;
      }
    }
    if (!placed && world.getGameRules().getBoolean(net.minecraft.world.GameRules.DO_ENTITY_DROPS)) {
      ItemStack stack = getStack();
      if (!stack.isEmpty()) {
        NbtCompound properties = new NbtCompound();
        for (var property : state.getProperties()) putProperty(properties, state, property);
        if (!properties.isEmpty()) stack.setSubNbt("BlockStateTag", properties);
        if (blockEntityData != null) stack.setSubNbt("BlockEntityTag", blockEntityData.copy());
        dropStack(stack);
      }
    }
    blockEntityData = null;
    discard();
  }

  private static <T extends Comparable<T>> void putProperty(
      NbtCompound n, BlockState state, Property<T> property) {
    n.putString(property.getName(), property.name(state.get(property)));
  }

  private void restoreBlockEntity(BlockPos pos) {
    if (blockEntityData == null) return;
    BlockEntity be = world.getBlockEntity(pos);
    if (be != null
        && be.createNbtWithId().getString("id").equals(blockEntityData.getString("id"))) {
      NbtCompound data = blockEntityData.copy();
      data.putInt("x", pos.getX());
      data.putInt("y", pos.getY());
      data.putInt("z", pos.getZ());
      be.readNbt(data);
      be.markDirty();
    }
  }

  public void consume() {
    if (!world.isClient && !completed) {
      completed = true;
      blockEntityData = null;
      discard();
    }
  }

  @Override
  protected void writeCustomDataToNbt(NbtCompound n) {
    n.put("state", NbtHelper.fromBlockState(blockState()));
    n.putLong("origin", origin.asLong());
    n.putInt("ticks", ticks);
    n.putInt("lift", liftTicks);
    n.putBoolean("completed", completed);
    if (singularity != null) n.putUuid("singularity", singularity);
    if (blockEntityData != null) n.put("blockEntity", blockEntityData);
  }

  @Override
  protected void readCustomDataFromNbt(NbtCompound n) {
    dataTracker.set(STATE, Block.getRawIdFromState(NbtHelper.toBlockState(n.getCompound("state"))));
    origin = BlockPos.fromLong(n.getLong("origin"));
    ticks = Math.max(0, Math.min(72000, n.getInt("ticks")));
    liftTicks = Math.max(0, Math.min(72000, n.getInt("lift")));
    completed = n.getBoolean("completed");
    singularity = n.containsUuid("singularity") ? n.getUuid("singularity") : null;
    coreLoadGrace = singularity == null ? 0 : 100;
    blockEntityData = n.contains("blockEntity", 10) ? n.getCompound("blockEntity").copy() : null;
  }

  @Override
  public Packet<?> createSpawnPacket() {
    return new EntitySpawnS2CPacket(this);
  }

  @Override
  public boolean isCollidable() {
    return !completed;
  }
}

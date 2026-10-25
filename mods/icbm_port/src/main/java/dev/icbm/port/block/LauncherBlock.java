package dev.icbm.port.block;

import dev.icbm.port.IcbmPort;
import dev.icbm.port.core.DirectFlight;
import dev.icbm.port.entity.MissileEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FacingBlock;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/** A one-missile, redstone-triggered launcher. State persists in the chunk. */
public class LauncherBlock extends FacingBlock implements net.minecraft.block.BlockEntityProvider {
  @Override
  public net.minecraft.block.entity.BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
    return new dev.icbm.port.launcher.LauncherBlockEntity(pos, state);
  }

  /** Upstream TileLauncherBase ticks {@code updateSeat} every three ticks; bases only. */
  @Override
  public <T extends net.minecraft.block.entity.BlockEntity>
      net.minecraft.block.entity.BlockEntityTicker<T> getTicker(
          World world, BlockState state, net.minecraft.block.entity.BlockEntityType<T> type) {
    if (world.isClient
        || tier == 0
        || cruise
        || type != dev.icbm.port.launcher.LauncherContent.BLOCK_ENTITY) return null;
    return (tickWorld, tickPos, tickState, entity) -> {
      if (tickWorld.getTime() % dev.icbm.port.entity.PlayerSeatEntity.UPDATE_INTERVAL == 0
          && tickWorld instanceof ServerWorld server
          && entity instanceof dev.icbm.port.launcher.LauncherBlockEntity launcher)
        dev.icbm.port.entity.PlayerSeatEntity.syncLauncherSeat(server, tickPos, launcher);
    };
  }

  public static final BooleanProperty LOADED = BooleanProperty.of("loaded");
  public static final BooleanProperty TRIGGERED = Properties.TRIGGERED;

  public final int tier;
  public final boolean cruise;

  public LauncherBlock(Settings settings) {
    this(settings, 0, false);
  }

  public LauncherBlock(Settings settings, int tier, boolean cruise) {
    super(settings);
    this.tier = tier;
    this.cruise = cruise;
    setDefaultState(
        stateManager
            .getDefaultState()
            .with(FACING, Direction.NORTH)
            .with(LOADED, false)
            .with(TRIGGERED, false));
  }

  public int range() {
    return tier == 0 ? 4000 : dev.icbm.port.flight.FlightSettings.current.maxRange;
  }

  public int capacity() {
    return tier == 0 ? 10000 : 1200;
  }

  public int launchCost() {
    return tier == 0 ? 1000 : 1200;
  }

  @Override
  protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
    builder.add(FACING, LOADED, TRIGGERED);
  }

  @Override
  public PistonBehavior getPistonBehavior(BlockState state) {
    return PistonBehavior.BLOCK;
  }

  @Override
  public BlockState getPlacementState(ItemPlacementContext context) {
    return getDefaultState()
        .with(
            FACING,
            tier > 0 && !cruise ? Direction.UP : context.getPlayerLookDirection().getOpposite());
  }

  @Override
  public ActionResult onUse(
      BlockState state,
      World world,
      BlockPos pos,
      PlayerEntity player,
      Hand hand,
      BlockHitResult hit) {
    ItemStack stack = player.getStackInHand(hand);
    if (!(world.getBlockEntity(pos) instanceof dev.icbm.port.launcher.LauncherBlockEntity launcher))
      return ActionResult.PASS;
    if (IcbmPort.isMissile(stack) && !state.get(LOADED)) {
      if (!world.isClient) {
        ItemStack ammo = stack.copy();
        ammo.setCount(1);
        launcher.setStack(0, ammo);
        if (!player.getAbilities().creativeMode) stack.decrement(1);
      }
    } else if (player.isSneaking() && stack.isEmpty() && state.get(LOADED)) {
      if (!world.isClient) {
        ItemStack ammo = launcher.removeStack(0);
        player.giveItemStack(ammo.isEmpty() ? new ItemStack(IcbmPort.CONDENSED_MISSILE) : ammo);
        world.setBlockState(pos, state.with(LOADED, false), 3);
      }
    } else if (stack.isOf(net.minecraft.item.Items.REDSTONE)) {
      if (!world.isClient && launcher.acceptCharge()) {
        if (!player.getAbilities().creativeMode) stack.decrement(1);
      }
    } else if (stack.isEmpty()) {
      if (!world.isClient) player.openHandledScreen(launcher);
    } else return ActionResult.PASS;
    return ActionResult.success(world.isClient);
  }

  @Override
  public void neighborUpdate(
      BlockState state,
      World world,
      BlockPos pos,
      Block source,
      BlockPos sourcePos,
      boolean notify) {
    updatePower(state, world, pos);
  }

  @Override
  public void onBlockAdded(
      BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
    if (!oldState.isOf(this)) updatePower(state, world, pos);
  }

  private void updatePower(BlockState state, World world, BlockPos pos) {
    if (world.isClient) return;
    boolean powered = world.isReceivingRedstonePower(pos);
    if (powered && !state.get(TRIGGERED)) {
      world.setBlockState(pos, state.with(TRIGGERED, true), Block.NOTIFY_ALL);
      world.createAndScheduleBlockTick(pos, this, 4);
    } else if (!powered && state.get(TRIGGERED)) {
      world.setBlockState(pos, state.with(TRIGGERED, false), Block.NOTIFY_ALL);
    }
  }

  @Override
  public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
    launch(world, pos);
  }

  public boolean launch(ServerWorld world, BlockPos pos) {
    BlockState state = world.getBlockState(pos);
    if (!state.isOf(this)) return false;
    if (dev.icbm.port.defense.DefenseContent.isEmpDisabled(world, pos)) return false;
    if (!(world.getBlockEntity(pos) instanceof dev.icbm.port.launcher.LauncherBlockEntity launcher))
      return false;
    if (!state.get(LOADED) && !launcher.pullNetworkAmmo()) return false;
    state = world.getBlockState(pos);
    ItemStack ammo = launcher.getStack(0);
    dev.icbm.port.delivery.ClusterPlan clusterPlan =
        dev.icbm.port.delivery.ClusterPlan.fromStack(ammo);
    if (ammo.isOf(dev.icbm.port.delivery.DeliveryContent.CLUSTER_MISSILE) && clusterPlan.isEmpty())
      return false;
    boolean held = dev.icbm.port.delivery.DeliveryContent.isLoadedHeldMissile(ammo);
    dev.icbm.port.payload.PayloadType payload = IcbmPort.payloadForMissile(ammo);
    if (payload == null && !ammo.isEmpty() && !held) return false;
    if (payload == null && !held)
      payload = IcbmPort.payloadForMissile(new ItemStack(IcbmPort.CONDENSED_MISSILE));
    if (tier > 0 && !cruise && !dev.icbm.port.launcher.LauncherNetwork.hasFrame(world, pos))
      return false;
    if (launcher.targeted()
        && !dev.icbm.port.launcher.LauncherContent.validTarget(world, pos, launcher.target()))
      return false;
    Direction facing = state.get(FACING);
    // Require a clear muzzle; no spawning through a wall in front of the launcher.
    BlockPos muzzle = pos.offset(facing);
    if (!world.isChunkLoaded(muzzle)
        || !world.getWorldBorder().contains(muzzle)
        || world.isOutOfHeightLimit(muzzle)
        || !world.getBlockState(muzzle).getCollisionShape(world, muzzle).isEmpty()) return false;
    launcher.shareEnergy();
    while (launcher.energy() < launchCost() && launcher.charge()) {}
    if (launcher.energy() < launchCost()) return false;
    Vec3d start =
        Vec3d.ofCenter(pos)
            .add(facing.getOffsetX() * 1.1, facing.getOffsetY() * 1.1, facing.getOffsetZ() * 1.1);
    double speed =
        tier > 0 ? dev.icbm.port.flight.FlightProfile.DIRECT_SPEED : IcbmPort.MISSILE_SPEED;
    DirectFlight.Velocity motion =
        DirectFlight.velocity(facing.getOffsetX(), facing.getOffsetY(), facing.getOffsetZ(), speed);
    if (launcher.targeted() && cruise) {
      Vec3d delta = Vec3d.ofCenter(launcher.target()).subtract(start);
      motion = DirectFlight.velocity(delta.x, delta.y, delta.z, speed);
    }
    if (held) {
      net.minecraft.entity.Entity heldMissile =
          dev.icbm.port.delivery.DeliveryContent.launchHeldEntity(
              world,
              null,
              ammo,
              start,
              new Vec3d(motion.x(), motion.y(), motion.z()),
              launcher.targeted() && !cruise ? launcher.target() : null);
      if (heldMissile == null) return false;
      dev.icbm.port.entity.PlayerSeatEntity.transferRiders(world, pos, heldMissile);
    } else {
      boolean clusterAmmo = ammo.isOf(dev.icbm.port.delivery.DeliveryContent.CLUSTER_MISSILE);
      MissileEntity missile =
          clusterAmmo
              ? dev.icbm.port.entity.ActionableMissileEntity.forCluster(world, ammo)
              : new MissileEntity(IcbmPort.MISSILE_ENTITY, world);
      if (missile == null) return false;
      if (cruise || tier > 0) missile.configureFuel(dev.icbm.port.flight.FlightProfile.CRUISE_FUEL);
      missile.setPosition(start);
      missile.setVelocity(motion.x(), motion.y(), motion.z());
      if (!clusterAmmo) missile.setPayload(payload);
      if (payload == dev.icbm.port.payload.PayloadType.ENDER
          && launcher.payloadDestination() != null
          && launcher.payloadDimension() != null)
        missile.configurePayloadDestination(
            launcher.payloadDimension(), launcher.payloadDestination());
      if (missile instanceof dev.icbm.port.entity.ActionableMissileEntity actionable
          && launcher.targeted())
        // Upstream CapabilityClusterMissileStack: release within 3 blocks of the target and never
        // on impact.
        actionable.setProximityTrigger(
            Vec3d.ofCenter(launcher.target()),
            dev.icbm.port.entity.ActionableMissileEntity.CLUSTER_TRIGGER_DISTANCE,
            false);
      if (launcher.targeted() && !cruise) {
        try {
          missile.configureTarget(launcher.target());
        } catch (IllegalArgumentException invalid) {
          return false;
        }
      }
      if (!world.spawnEntity(missile)) return false;
      dev.icbm.port.entity.PlayerSeatEntity.transferRiders(world, pos, missile);
    }
    launcher.consumeLaunch();
    world.setBlockState(pos, state.with(LOADED, !launcher.getStack(0).isEmpty()), Block.NOTIFY_ALL);
    world.playSound(
        null, pos, SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.BLOCKS, 1, 0.7F);
    return true;
  }

  @Override
  public void onStateReplaced(
      BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
    if (!state.isOf(newState.getBlock()) && !world.isClient) {
      dev.icbm.port.entity.PlayerSeatEntity.removeSeats(world, pos);
      if (world.getBlockEntity(pos)
          instanceof dev.icbm.port.launcher.LauncherBlockEntity launcher) {
        if (state.get(LOADED) && launcher.getStack(0).isEmpty())
          Block.dropStack(world, pos, new ItemStack(IcbmPort.CONDENSED_MISSILE));
        net.minecraft.util.ItemScatterer.spawn(world, pos, launcher);
      }
    }
    super.onStateReplaced(state, world, pos, newState, moved);
  }
}

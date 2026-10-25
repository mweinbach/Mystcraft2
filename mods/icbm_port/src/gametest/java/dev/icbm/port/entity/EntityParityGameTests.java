package dev.icbm.port.entity;

import dev.icbm.port.IcbmPort;
import dev.icbm.port.api.ActionSource;
import dev.icbm.port.api.IcbmApi;
import dev.icbm.port.block.LauncherBlock;
import dev.icbm.port.delivery.BombletEntity;
import dev.icbm.port.delivery.ClusterPlan;
import dev.icbm.port.delivery.DeliveryContent;
import dev.icbm.port.launcher.LauncherBlockEntity;
import dev.icbm.port.launcher.LauncherContent;
import dev.icbm.port.payload.PayloadConfig;
import dev.icbm.port.payload.PayloadType;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/** Seat, light beam and actionable missile behaviour ported from upstream ICBM-Classic. */
public class EntityParityGameTests implements FabricGameTest {
  private static final Identifier COUNTER_ACTION = new Identifier("icbm_port_gametest", "counter");
  private static final AtomicInteger COUNTER = new AtomicInteger();

  private static synchronized void ensureCounterAction() {
    if (IcbmApi.find(COUNTER_ACTION) == null)
      IcbmApi.registerAction(
          new IcbmApi.Descriptor(
              COUNTER_ACTION,
              1,
              false,
              context -> {
                COUNTER.incrementAndGet();
                return true;
              }));
  }

  private void check(TestContext context, boolean condition, String message) {
    if (!condition) context.throwGameTestException(message);
  }

  private LauncherBlockEntity tieredLauncher(TestContext context, BlockPos pos) {
    context
        .getWorld()
        .setBlockState(
            pos,
            LauncherContent.BASE_TIER_1.getDefaultState().with(LauncherBlock.FACING, Direction.UP));
    return (LauncherBlockEntity) context.getWorld().getBlockEntity(pos);
  }

  private PigEntity pig(TestContext context, BlockPos pos) {
    PigEntity pig = EntityType.PIG.create(context.getWorld());
    check(context, pig != null, "Test pig must exist");
    pig.refreshPositionAndAngles(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
    context.getWorld().spawnEntity(pig);
    return pig;
  }

  private ItemStack clusterStack(PayloadType... payloads) {
    ItemStack stack = new ItemStack(DeliveryContent.CLUSTER_MISSILE);
    new ClusterPlan(List.of(payloads)).writeTo(stack);
    return stack;
  }

  private List<BombletEntity> bomblets(TestContext context, Vec3d center) {
    return context
        .getWorld()
        .getEntitiesByClass(BombletEntity.class, new Box(center, center).expand(8), e -> true);
  }

  // ---- Launcher seat --------------------------------------------------------------------------

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void seatFollowsLoadedTieredLauncher(TestContext context) {
    BlockPos pos = context.getAbsolutePos(new BlockPos(2, 2, 2));
    LauncherBlockEntity launcher = tieredLauncher(context, pos);
    PlayerSeatEntity.syncLauncherSeat(context.getWorld(), pos, launcher);
    check(
        context,
        PlayerSeatEntity.seatsFor(context.getWorld(), pos).isEmpty(),
        "An empty launcher must not offer a seat");
    launcher.setStack(0, new ItemStack(IcbmPort.CONDENSED_MISSILE));
    PlayerSeatEntity.syncLauncherSeat(context.getWorld(), pos, launcher);
    PlayerSeatEntity.syncLauncherSeat(context.getWorld(), pos, launcher);
    List<PlayerSeatEntity> seats = PlayerSeatEntity.seatsFor(context.getWorld(), pos);
    check(context, seats.size() == 1, "A loaded tiered launcher must offer exactly one seat");
    PlayerSeatEntity seat = seats.get(0);
    check(
        context,
        seat.getY() >= pos.getY() + 1 - 1e-6 && seat.isCollidable() && seat.canHit(),
        "Empty seat must sit on the launch face and be clickable");
    NbtCompound saved = new NbtCompound();
    seat.writeNbt(saved);
    PlayerSeatEntity restored = new PlayerSeatEntity(EntityContent.PLAYER_SEAT, context.getWorld());
    restored.readNbt(saved);
    check(context, pos.equals(restored.getHostPos()), "Seat host must survive reload");
    launcher.setStack(0, ItemStack.EMPTY);
    PlayerSeatEntity.syncLauncherSeat(context.getWorld(), pos, launcher);
    check(
        context,
        PlayerSeatEntity.seatsFor(context.getWorld(), pos).isEmpty() && seat.isRemoved(),
        "Unloading the launcher must remove its seat");
    context.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void prototypeAndCruiseLaunchersHaveNoSeat(TestContext context) {
    BlockPos pos = context.getAbsolutePos(new BlockPos(2, 2, 2));
    context
        .getWorld()
        .setBlockState(pos, IcbmPort.LAUNCHER.getDefaultState().with(LauncherBlock.FACING, Direction.UP));
    LauncherBlockEntity launcher = (LauncherBlockEntity) context.getWorld().getBlockEntity(pos);
    launcher.setStack(0, new ItemStack(IcbmPort.CONDENSED_MISSILE));
    PlayerSeatEntity.syncLauncherSeat(context.getWorld(), pos, launcher);
    check(
        context,
        PlayerSeatEntity.seatsFor(context.getWorld(), pos).isEmpty()
            && !PlayerSeatEntity.hostsSeat(LauncherContent.CRUISE.getDefaultState()),
        "Only tiered launch bases host seats");
    context.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 40)
  public void seatTickerSpawnsSeatAutomatically(TestContext context) {
    BlockPos pos = context.getAbsolutePos(new BlockPos(2, 2, 2));
    LauncherBlockEntity launcher = tieredLauncher(context, pos);
    launcher.setStack(0, new ItemStack(IcbmPort.CONDENSED_MISSILE));
    context.runAtTick(
        10,
        () -> {
          check(
              context,
              PlayerSeatEntity.seatsFor(context.getWorld(), pos).size() == 1,
              "Launcher ticker must create the seat within a few ticks");
          context.getWorld().setBlockState(pos, Blocks.AIR.getDefaultState());
          context.complete();
        });
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void breakingLauncherDismountsSeatRider(TestContext context) {
    BlockPos pos = context.getAbsolutePos(new BlockPos(2, 2, 2));
    LauncherBlockEntity launcher = tieredLauncher(context, pos);
    launcher.setStack(0, new ItemStack(IcbmPort.CONDENSED_MISSILE));
    PlayerSeatEntity.syncLauncherSeat(context.getWorld(), pos, launcher);
    PlayerSeatEntity seat = PlayerSeatEntity.seatsFor(context.getWorld(), pos).get(0);
    PigEntity rider = pig(context, pos.up());
    check(context, rider.startRiding(seat, true), "A mob must be able to sit in the seat");
    check(context, !seat.isCollidable(), "An occupied seat stops blocking movement");
    context.getWorld().setBlockState(pos, Blocks.AIR.getDefaultState());
    check(
        context,
        seat.isRemoved() && !rider.hasVehicle(),
        "Breaking the launcher must remove the seat and dismount its rider");
    rider.discard();
    context.killAllEntities();
    context.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void launchMovesSeatRiderOntoMissile(TestContext context) {
    BlockPos pos = context.getAbsolutePos(new BlockPos(2, 2, 2));
    LauncherBlockEntity launcher = tieredLauncher(context, pos);
    context.getWorld().setBlockState(pos.east(), LauncherContent.FRAME.getDefaultState());
    launcher.setStack(0, new ItemStack(IcbmPort.CONDENSED_MISSILE));
    NbtCompound energy = new NbtCompound();
    launcher.writeNbt(energy);
    energy.putInt("Energy", 100000);
    launcher.readNbt(energy);
    PlayerSeatEntity.syncLauncherSeat(context.getWorld(), pos, launcher);
    PlayerSeatEntity seat = PlayerSeatEntity.seatsFor(context.getWorld(), pos).get(0);
    PigEntity rider = pig(context, pos.up());
    rider.startRiding(seat, true);
    check(
        context,
        LauncherContent.BASE_TIER_1.launch(context.getWorld(), pos),
        "Loaded tiered launcher must fire");
    List<MissileEntity> missiles =
        context
            .getWorld()
            .getEntitiesByClass(MissileEntity.class, new Box(pos).expand(4), e -> true);
    check(context, missiles.size() == 1, "Launch must spawn one missile");
    MissileEntity missile = missiles.get(0);
    check(
        context,
        rider.getVehicle() == missile && seat.isRemoved(),
        "Seat rider must transfer onto the launched missile");
    PlayerSeatEntity probe = new PlayerSeatEntity(EntityContent.PLAYER_SEAT, context.getWorld());
    check(
        context,
        !missile.canHit(rider) && !missile.canHit(probe),
        "Missiles must not collide with their riders or launcher seats");
    missile.discard();
    rider.discard();
    context.complete();
  }

  // ---- Light beam -----------------------------------------------------------------------------

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void lightBeamOpensClosesAndPersists(TestContext context) {
    Vec3d pos = Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(2, 2, 2)));
    LightBeamEntity beam = new LightBeamEntity(EntityContent.LIGHT_BEAM, context.getWorld());
    beam.setPosition(pos);
    beam.setColor(0x33AAFF);
    beam.setTargetProgress(0.2F);
    for (int i = 0; i < 10; i++) beam.tick();
    check(
        context,
        Math.abs(beam.getProgress() - 0.2F) < 1e-4,
        "Beam progress must grow by 0.05 per tick and stop at its target");
    NbtCompound saved = new NbtCompound();
    beam.writeNbt(saved);
    LightBeamEntity restored = new LightBeamEntity(EntityContent.LIGHT_BEAM, context.getWorld());
    restored.readNbt(saved);
    check(
        context,
        restored.getColor() == 0x33AAFF
            && Math.abs(restored.getProgress() - 0.2F) < 1e-4
            && Math.abs(restored.getTargetProgress() - 0.2F) < 1e-4
            && !restored.isClosing()
            && restored.getBeamAge() == 10,
        "Beam colour, progress, target and age must survive reload");
    beam.close();
    for (int i = 0; i < 3; i++) beam.tick();
    check(context, !beam.isRemoved(), "Closing beam must animate shut before removal");
    for (int i = 0; i < 3; i++) beam.tick();
    check(context, beam.isRemoved(), "Closed beam must remove itself");
    float[] rgb = restored.colorComponents();
    check(
        context,
        Math.abs(rgb[0] - 0x33 / 255F) < 1e-4 && Math.abs(rgb[2] - 1F) < 1e-4,
        "Colour components must be unpacked for the renderer");
    context.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 60)
  public void lightBeamSpawnApiExpires(TestContext context) {
    Vec3d pos = Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(2, 2, 2)));
    LightBeamEntity beam = LightBeamEntity.spawn(context.getWorld(), pos, 0xFF4400, 5);
    check(context, beam != null && !beam.isRemoved(), "Spawn API must place a beam");
    check(
        context,
        beam.getColor() == 0xFF4400
            && beam.getBeamSize() == 1
            && beam.getGlowSize() == 2
            && LightBeamEntity.near(context.getWorld(), pos, 1).contains(beam),
        "Spawned beam must carry its colour and default sizes");
    check(
        context,
        LightBeamEntity.spawn(context.getWorld(), new Vec3d(Double.NaN, 0, 0), 0, 5) == null,
        "Invalid positions must be rejected");
    context.runAtTick(
        30,
        () -> {
          check(context, beam.isRemoved(), "Timed beam must close and despawn on its own");
          context.complete();
        });
  }

  // ---- Actionable missile ---------------------------------------------------------------------

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void actionableMissileStateSurvivesReload(TestContext context) {
    ActionableMissileEntity missile =
        ActionableMissileEntity.forCluster(
            context.getWorld(), clusterStack(PayloadType.CONDENSED, PayloadType.INCENDIARY));
    check(context, missile != null && missile.isCluster(), "Cluster stack must build a missile");
    Vec3d target = Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(2, 40, 2)));
    missile.setProximityTrigger(target, 3, false);
    NbtCompound saved = new NbtCompound();
    missile.writeNbt(saved);
    ActionableMissileEntity restored =
        new ActionableMissileEntity(EntityContent.ACTIONABLE_MISSILE, context.getWorld());
    restored.readNbt(saved);
    check(
        context,
        restored.getAction() instanceof ActionableMissileEntity.ClusterAction cluster
            && cluster.plan().payloads().size() == 2
            && restored.isCluster()
            && restored.getOriginalStack().isOf(DeliveryContent.CLUSTER_MISSILE)
            && target.equals(restored.getTriggerTarget())
            && restored.getTriggerDistance() == 3
            && !restored.hasImpactTrigger()
            && !restored.hasTriggered(),
        "Action, original stack and trigger settings must survive reload");
    var api = new ActionableMissileEntity.ApiAction(new Identifier("icbm_port", "condensed"), -1);
    check(
        context,
        api.equals(ActionableMissileEntity.readAction(ActionableMissileEntity.writeAction(api)))
            && api.resolvedSize()
                == Math.min(
                    PayloadType.CONDENSED.maxRadius(),
                    PayloadConfig.forType(PayloadType.CONDENSED).radius()),
        "API actions must round-trip and default to the configured radius");
    NbtCompound unknown = new NbtCompound();
    unknown.putString("Type", "icbm_port_gametest:missing");
    check(
        context,
        ActionableMissileEntity.readAction(unknown) == null,
        "Unknown action types must load as no action");
    context.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void apiActionRunsExactlyOnce(TestContext context) {
    ensureCounterAction();
    int before = COUNTER.get();
    ActionableMissileEntity missile =
        ActionableMissileEntity.forApiAction(
            context.getWorld(), COUNTER_ACTION, 2, ItemStack.EMPTY);
    Vec3d pos = Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(2, 2, 2)));
    missile.setPosition(pos);
    check(context, missile.runAction(pos), "Registered API action must run");
    check(context, !missile.runAction(pos), "Actions must never run twice");
    check(context, COUNTER.get() == before + 1, "Action must execute exactly once");
    context.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void explosionOrFireDamageSetsOffPayload(TestContext context) {
    ensureCounterAction();
    int before = COUNTER.get();
    Vec3d pos = Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(2, 4, 2)));
    ActionableMissileEntity blasted =
        ActionableMissileEntity.forApiAction(context.getWorld(), COUNTER_ACTION, 1, ItemStack.EMPTY);
    blasted.setPosition(pos);
    blasted.damage(DamageSource.explosion((LivingEntity) null), 100);
    ActionableMissileEntity burned =
        ActionableMissileEntity.forApiAction(context.getWorld(), COUNTER_ACTION, 1, ItemStack.EMPTY);
    burned.setPosition(pos);
    burned.damage(DamageSource.IN_FIRE, 100);
    ActionableMissileEntity shot =
        ActionableMissileEntity.forApiAction(context.getWorld(), COUNTER_ACTION, 1, ItemStack.EMPTY);
    shot.setPosition(pos);
    shot.damage(DamageSource.GENERIC, 100);
    check(
        context,
        blasted.isRemoved() && burned.isRemoved() && shot.isRemoved(),
        "Destroyed missiles must be removed");
    check(
        context,
        COUNTER.get() == before + 2 && blasted.hasTriggered() && !shot.hasTriggered(),
        "Only explosive or fire damage may set off the payload");
    context.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void clusterProximityTriggerAndImpactDud(TestContext context) {
    Vec3d target = Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(2, 3, 2)));
    ActionableMissileEntity missile =
        ActionableMissileEntity.forCluster(context.getWorld(), clusterStack(PayloadType.CONDENSED));
    missile.setProximityTrigger(target, ActionableMissileEntity.CLUSTER_TRIGGER_DISTANCE, false);
    missile.setPosition(target.add(0, 10, 0));
    check(context, !missile.shouldTriggerInFlight(), "Missile far from target must keep flying");
    missile.setPosition(target.add(0, 2, 0));
    check(context, missile.shouldTriggerInFlight(), "Missile within three blocks must release");

    Vec3d far = target.add(0, 30, 0);
    ActionableMissileEntity dud =
        ActionableMissileEntity.forCluster(context.getWorld(), clusterStack(PayloadType.CONDENSED));
    dud.setProximityTrigger(far, ActionableMissileEntity.CLUSTER_TRIGGER_DISTANCE, false);
    dud.setPosition(target);
    dud.onImpact(target);
    check(
        context,
        dud.isRemoved() && !dud.hasTriggered() && bomblets(context, target).isEmpty(),
        "Impact short of the trigger sphere must be a dud");

    ActionableMissileEntity hit =
        ActionableMissileEntity.forCluster(context.getWorld(), clusterStack(PayloadType.CONDENSED));
    hit.setProximityTrigger(target, ActionableMissileEntity.CLUSTER_TRIGGER_DISTANCE, false);
    hit.setPosition(target);
    hit.onImpact(target);
    List<BombletEntity> released = bomblets(context, target);
    check(
        context,
        hit.isRemoved() && hit.hasTriggered() && !released.isEmpty(),
        "Impact inside the trigger sphere must release the cluster");
    released.forEach(BombletEntity::discard);
    context.complete();
  }

  /**
   * Review fix: a direct rocket-launcher cluster only takes aim points it can reach before {@code
   * MISSILE_LIFETIME} expires; a 500-block upstream aim point would never release.
   */
  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void directClusterAimPointIsWithinFlightRange(TestContext context) {
    Vec3d eye = Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(2, 3, 2)));
    Vec3d direction = new Vec3d(1, 0, 0);
    Vec3d muzzle = eye.add(direction);
    check(
        context,
        releaseTick(context, muzzle, eye.add(direction.multiply(500))) < 0,
        "An aim point past the flight range never releases (the reviewed bug)");
    int tick =
        releaseTick(
            context,
            muzzle,
            eye.add(
                direction.multiply(
                    dev.icbm.port.delivery.RocketLauncherItem.DIRECT_CLUSTER_AIM_RANGE)));
    check(
        context,
        tick > 0 && tick <= IcbmPort.MISSILE_LIFETIME,
        "The farthest direct aim point must release before the rocket expires: " + tick);
    context.complete();
  }

  /** Flies a direct cluster rocket straight at MISSILE_SPEED; the tick it releases, or -1. */
  private int releaseTick(TestContext context, Vec3d muzzle, Vec3d aim) {
    ActionableMissileEntity missile =
        ActionableMissileEntity.forCluster(context.getWorld(), clusterStack(PayloadType.CONDENSED));
    missile.setProximityTrigger(aim, ActionableMissileEntity.CLUSTER_TRIGGER_DISTANCE, false);
    Vec3d step = aim.subtract(muzzle).normalize().multiply(IcbmPort.MISSILE_SPEED);
    for (int tick = 1; tick <= IcbmPort.MISSILE_LIFETIME; tick++) {
      missile.setPosition(muzzle.add(step.multiply(tick)));
      if (missile.shouldTriggerInFlight()) return tick;
    }
    return -1;
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void targetedLauncherClusterUsesProximityTrigger(TestContext context) {
    BlockPos pos = context.getAbsolutePos(new BlockPos(2, 2, 2));
    context
        .getWorld()
        .setBlockState(pos, IcbmPort.LAUNCHER.getDefaultState().with(LauncherBlock.FACING, Direction.UP));
    LauncherBlockEntity launcher = (LauncherBlockEntity) context.getWorld().getBlockEntity(pos);
    BlockPos target = pos.east(40);
    check(context, launcher.configure(target, true, 7), "Target must be accepted");
    launcher.setStack(0, clusterStack(PayloadType.CONDENSED));
    check(context, IcbmPort.LAUNCHER.launch(context.getWorld(), pos), "Cluster must launch");
    List<ActionableMissileEntity> missiles =
        context
            .getWorld()
            .getEntitiesByClass(ActionableMissileEntity.class, new Box(pos).expand(4), e -> true);
    check(context, missiles.size() == 1, "Launcher must fire an actionable cluster missile");
    ActionableMissileEntity missile = missiles.get(0);
    check(
        context,
        Vec3d.ofCenter(target).equals(missile.getTriggerTarget())
            && missile.getTriggerDistance() == ActionableMissileEntity.CLUSTER_TRIGGER_DISTANCE
            && !missile.hasImpactTrigger(),
        "Targeted cluster must release near the target and never on impact");
    missile.discard();
    context.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void apiProjectileBuildsActionableCluster(TestContext context) {
    Vec3d pos = Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(2, 5, 2)));
    boolean launched =
        IcbmApi.launch(
            new Identifier("icbm_port", "explosive_missile"),
            new IcbmApi.ProjectileContext(
                context.getWorld(),
                pos,
                new Vec3d(0, 0.5, 0),
                clusterStack(PayloadType.CONDENSED),
                ActionSource.of(null)));
    List<ActionableMissileEntity> missiles =
        context
            .getWorld()
            .getEntitiesByClass(
                ActionableMissileEntity.class, new Box(pos, pos).expand(4), e -> true);
    check(
        context,
        launched && missiles.size() == 1 && missiles.get(0).isCluster(),
        "Explosive-missile projectile API must build actionable cluster missiles");
    missiles.forEach(MissileEntity::discard);
    context.complete();
  }
}

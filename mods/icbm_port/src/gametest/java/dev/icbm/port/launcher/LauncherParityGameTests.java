package dev.icbm.port.launcher;

import dev.icbm.port.IcbmPort;
import dev.icbm.port.block.LauncherBlock;
import dev.icbm.port.entity.MissileEntity;
import dev.icbm.port.radio.RadioMessage;
import dev.icbm.port.radio.RadioNetwork;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Launcher, launcher network and detonator parity with MIT ICBM-Classic {@code LauncherCapability},
 * {@code TileLauncherBase}, {@code TileLauncherScreen}, {@code RadioScreen}, {@code RadioCruise},
 * {@code ItemRemoteDetonator} and {@code ItemLaserDetonator}. Radio is dimension-wide, so each test
 * owns a 406x channel; the batch is separate because the laser fixture reaches outside the
 * structure.
 */
public class LauncherParityGameTests implements FabricGameTest {
  private static final String BATCH = "pr5Launchers";

  private static void check(TestContext context, boolean condition, String message) {
    if (!condition) context.throwGameTestException(message);
  }

  /** Records radio replies (upstream {@code IRadioSender#onMessageCallback}). */
  static final class Probe implements RadioNetwork.Sender, RadioNetwork.Receiver {
    final BlockPos pos;
    final int channel;
    final List<String> replies = new ArrayList<>();
    final List<RadioMessage> heard = new ArrayList<>();

    Probe(BlockPos pos, int channel) {
      this.pos = pos.toImmutable();
      this.channel = channel;
    }

    @Override
    public BlockPos radioPosition() {
      return pos;
    }

    @Override
    public int radioChannel() {
      return channel;
    }

    @Override
    public boolean radioEnabled() {
      return true;
    }

    @Override
    public void onMessage(RadioNetwork.Sender sender, RadioMessage message) {
      heard.add(message);
    }

    @Override
    public void onCallback(RadioNetwork.Receiver from, RadioMessage.Text text) {
      replies.add(text.key());
    }

    String last() {
      return replies.isEmpty() ? "" : replies.get(replies.size() - 1);
    }
  }

  /** The legacy one-missile launcher: no frame rule, 1000 energy preloaded, 4000 range. */
  private static LauncherBlockEntity legacy(TestContext context, BlockPos relative) {
    BlockPos pos = context.getAbsolutePos(relative);
    context
        .getWorld()
        .setBlockState(
            pos, IcbmPort.LAUNCHER.getDefaultState().with(LauncherBlock.FACING, Direction.UP));
    var launcher = (LauncherBlockEntity) context.getWorld().getBlockEntity(pos);
    launcher.setStack(0, new ItemStack(IcbmPort.CONDENSED_MISSILE));
    return launcher;
  }

  private static List<MissileEntity> missiles(TestContext context, BlockPos around, int radius) {
    return context
        .getWorld()
        .getEntitiesByType(IcbmPort.MISSILE_ENTITY, new Box(around).expand(radius), e -> true);
  }

  private static void clearMissiles(TestContext context, BlockPos around) {
    missiles(context, around, 96).forEach(Entity::discard);
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = BATCH)
  public void launcherRangeRulesFollowUpstream(TestContext context) {
    var launcher = legacy(context, new BlockPos(2, 2, 2));
    BlockPos pos = launcher.getPos();
    check(context, launcher.configure(pos.east(5).north(5), true, 0), "Near target is storable");
    var status = IcbmPort.LAUNCHER.fire(context.getWorld(), pos, 1);
    check(
        context,
        status == LauncherStatus.ERROR_MIN_RANGE
            && launcher.lastStatus() == LauncherStatus.ERROR_MIN_RANGE
            && !launcher.getStack(0).isEmpty(),
        "Targets within 10 blocks on both X and Z must be refused without consuming ammo");
    int range = IcbmPort.LAUNCHER.range();
    check(
        context,
        LauncherContent.validTarget(context.getWorld(), pos, pos.add(range, 0, range))
            && !LauncherContent.validTarget(context.getWorld(), pos, pos.add(range + 1, 0, 0)),
        "Maximum range is a per-axis square, as upstream");
    context.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = BATCH)
  public void launcherAppliesInaccuracyAndLockHeight(TestContext context) {
    var launcher = legacy(context, new BlockPos(2, 2, 2));
    BlockPos pos = launcher.getPos(), target = pos.east(300);
    launcher.configure(target, true, 0);
    check(context, launcher.configureLaunch(0, 20), "Lock height 20 must be accepted");
    var status = IcbmPort.LAUNCHER.fire(context.getWorld(), pos, 3);
    var fired = missiles(context, pos, 4);
    check(context, status == LauncherStatus.LAUNCHED && fired.size() == 1, "Salvo must launch");
    var plan = fired.get(0).flightPlan();
    double spread =
        LauncherRules.inaccuracy(
            Vec3d.ofCenter(pos).squaredDistanceTo(Vec3d.ofCenter(target)),
            IcbmPort.LAUNCHER.range(),
            3);
    double dx = plan.target().getX() - target.getX(), dz = plan.target().getZ() - target.getZ();
    check(
        context,
        plan.target().getY() == target.getY() && Math.sqrt(dx * dx + dz * dz) <= spread + 1.5,
        "Impact point must stay within the upstream inaccuracy radius");
    check(context, plan.lockHeight() == 20, "Launcher lock height must reach the missile");
    clearMissiles(context, pos);
    context.complete();
  }

  /** Review fix: held-item missiles fired from a targeted base also climb to its lock height. */
  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = BATCH)
  public void heldItemMissileUsesLauncherLockHeight(TestContext context) {
    var launcher = legacy(context, new BlockPos(2, 2, 2));
    BlockPos pos = launcher.getPos(), target = pos.east(300);
    ItemStack ammo = new ItemStack(dev.icbm.port.delivery.DeliveryContent.HELD_ITEM_MISSILE);
    dev.icbm.port.delivery.HeldItemMissileItem.setCargo(
        ammo, new ItemStack(net.minecraft.item.Items.COBBLESTONE));
    launcher.setStack(0, ammo);
    launcher.configure(target, true, 0);
    check(context, launcher.configureLaunch(0, 24), "Lock height 24 must be accepted");
    var status = IcbmPort.LAUNCHER.fire(context.getWorld(), pos, 1);
    var fired =
        context
            .getWorld()
            .getEntitiesByType(
                dev.icbm.port.delivery.DeliveryContent.HELD_MISSILE,
                new Box(pos).expand(4),
                e -> true);
    try {
      check(
          context,
          status == LauncherStatus.LAUNCHED && fired.size() == 1,
          "The held-item missile must launch: " + status);
      check(
          context,
          fired.get(0).flightPlan().targeted() && fired.get(0).flightPlan().lockHeight() == 24,
          "The launcher's lock height must reach a held-item missile's flight plan");
    } finally {
      fired.forEach(Entity::discard);
    }
    context.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 40)
  public void firingDelayQueuesPersistsAndFires(TestContext context) {
    var launcher = legacy(context, new BlockPos(2, 2, 2));
    BlockPos pos = launcher.getPos();
    launcher.configure(pos.east(50), true, 0);
    launcher.configureLaunch(5, 3);
    check(
        context,
        IcbmPort.LAUNCHER.fire(context.getWorld(), pos, 1) == LauncherStatus.DELAYED
            && launcher.hasPendingLaunch()
            && launcher.pendingCountdown() == 5
            && !launcher.getStack(0).isEmpty(),
        "A firing delay must queue a firing package without consuming the missile");
    check(
        context,
        IcbmPort.LAUNCHER.fire(context.getWorld(), pos, 1) == LauncherStatus.DELAYED
            && launcher.pendingCountdown() == 5,
        "A queued launcher must block further launches");
    NbtCompound saved = launcher.createNbt();
    var restored = new LauncherBlockEntity(pos, launcher.getCachedState());
    restored.readNbt(saved);
    check(
        context,
        restored.hasPendingLaunch()
            && restored.pendingCountdown() == 5
            && restored.firingDelay() == 5,
        "The firing package and delay must survive a reload");
    context.runAtTick(
        10,
        () -> {
          check(
              context,
              launcher.getStack(0).isEmpty()
                  && !launcher.hasPendingLaunch()
                  && launcher.lastStatus() == LauncherStatus.LAUNCHED,
              "The queued launch must fire when the countdown ends");
          clearMissiles(context, pos);
          context.complete();
        });
  }

  /**
   * The firing package path (upstream {@code FiringWithDelay}) must spawn through the same step as
   * an immediate launch, so a seated rider still moves onto the missile when the countdown ends.
   */
  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = BATCH)
  public void delayedLaunchCarriesSeatRider(TestContext context) {
    var world = context.getWorld();
    BlockPos pos = context.getAbsolutePos(new BlockPos(2, 2, 2));
    world.setBlockState(
        pos, LauncherContent.BASE_TIER_1.getDefaultState().with(LauncherBlock.FACING, Direction.UP));
    world.setBlockState(pos.east(), LauncherContent.FRAME.getDefaultState());
    var launcher = (LauncherBlockEntity) world.getBlockEntity(pos);
    launcher.setStack(0, new ItemStack(IcbmPort.CONDENSED_MISSILE));
    NbtCompound energy = launcher.createNbt();
    energy.putInt("Energy", 100_000);
    launcher.readNbt(energy);
    launcher.configure(pos.east(60), true, 0);
    launcher.configureLaunch(4, 3);
    dev.icbm.port.entity.PlayerSeatEntity.syncLauncherSeat(world, pos, launcher);
    var seats = dev.icbm.port.entity.PlayerSeatEntity.seatsFor(world, pos);
    check(context, seats.size() == 1, "A loaded tiered base must offer a seat");
    var rider = net.minecraft.entity.EntityType.PIG.create(world);
    rider.refreshPositionAndAngles(pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5, 0, 0);
    world.spawnEntity(rider);
    check(context, rider.startRiding(seats.get(0), true), "The rider must sit in the seat");
    check(
        context,
        LauncherContent.BASE_TIER_1.fire(world, pos, 1) == LauncherStatus.DELAYED
            && rider.getVehicle() == seats.get(0),
        "Queueing the launch must keep the rider seated until the countdown ends");
    // Drive the base's own ticker through the 4-tick countdown so the check sees the exact moment
    // the firing package spawns its missile.
    for (int i = 0; i < 4; i++)
      LauncherBlockEntity.tick(world, pos, world.getBlockState(pos), launcher);
    Entity vehicle = rider.getVehicle();
    check(
        context,
        !launcher.hasPendingLaunch()
            && launcher.lastStatus() == LauncherStatus.LAUNCHED
            && vehicle instanceof MissileEntity
            && seats.get(0).isRemoved(),
        "The delayed launch must carry the seated rider (status="
            + launcher.lastStatus()
            + ", vehicle="
            + vehicle
            + ")");
    // Checked synchronously: other tests in this batch discard missiles within 96 blocks when they
    // clean up, so a later check would observe their cleanup rather than this launch.
    rider.stopRiding();
    rider.discard();
    vehicle.discard();
    context.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = BATCH)
  public void launcherSettingsFailClosed(TestContext context) {
    var launcher = legacy(context, new BlockPos(2, 2, 2));
    check(
        context,
        LauncherContent.CONFIGURE_PACKET_BYTES == 33
            && LauncherContent.validLaunchSettings(0, 3)
            && LauncherContent.validLaunchSettings(1200, 64)
            && !LauncherContent.validLaunchSettings(1201, 3)
            && !LauncherContent.validLaunchSettings(-1, 3)
            && !LauncherContent.validLaunchSettings(0, 2)
            && !LauncherContent.validLaunchSettings(0, 65),
        "Screen packet bounds must fail closed");
    launcher.configureLaunch(40, 12);
    check(
        context,
        !launcher.configureLaunch(40, 99)
            && launcher.firingDelay() == 40
            && launcher.lockHeight() == 12,
        "Invalid launch settings must not mutate the launcher");
    NbtCompound nbt = launcher.createNbt();
    nbt.putInt("FiringDelay", -5);
    nbt.putInt("LockHeight", 999);
    launcher.readNbt(nbt);
    check(
        context,
        launcher.firingDelay() == 0 && launcher.lockHeight() == LauncherRules.MAX_LOCK_HEIGHT,
        "Saved launch settings must be clamped on load");
    check(
        context,
        launcher.properties.size() == LauncherBlockEntity.PROPERTY_COUNT
            && launcher.properties.get(12) == LauncherRules.MAX_LOCK_HEIGHT,
        "Screen properties must expose launch settings");
    context.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 30)
  public void screenStrongRedstoneFiresWholeNetwork(TestContext context) {
    var world = context.getWorld();
    var first = legacy(context, new BlockPos(1, 2, 2));
    var second = legacy(context, new BlockPos(3, 2, 2));
    BlockPos screen = first.getPos().east();
    world.setBlockState(screen, LauncherContent.SCREEN_BLOCK.getDefaultState());
    world.setBlockState(screen.up(), Blocks.REDSTONE_BLOCK.getDefaultState());
    context.runAtTick(
        4,
        () -> {
          check(
              context,
              !first.getStack(0).isEmpty() && !second.getStack(0).isEmpty(),
              "Weak redstone must not trigger a launcher screen");
          world.setBlockState(screen.up(), Blocks.AIR.getDefaultState());
          world.setBlockState(
              screen.north(),
              Blocks.LEVER
                  .getDefaultState()
                  .with(
                      net.minecraft.block.LeverBlock.FACE,
                      net.minecraft.block.enums.WallMountLocation.WALL)
                  .with(net.minecraft.block.LeverBlock.FACING, Direction.NORTH)
                  .with(net.minecraft.block.LeverBlock.POWERED, true));
        });
    context.runAtTick(
        7,
        () -> {
          check(
              context,
              first.getStack(0).isEmpty()
                  && second.getStack(0).isEmpty()
                  && first.lastStatus() == LauncherStatus.LAUNCHED
                  && second.lastStatus() == LauncherStatus.LAUNCHED,
              "Strong redstone on the screen must fire every networked launcher");
          world.setBlockState(screen.north(), Blocks.AIR.getDefaultState());
          clearMissiles(context, screen);
          context.complete();
        });
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = BATCH)
  public void radioTargetAndTriggerDriveBaseLauncher(TestContext context) {
    var world = context.getWorld();
    var launcher = legacy(context, new BlockPos(2, 2, 2));
    BlockPos pos = launcher.getPos();
    launcher.configure(pos, false, 4061);
    RadioNetwork.register(world, launcher);
    var remote = new Probe(pos.up(4), 4061);
    RadioNetwork.broadcast(world, remote, new RadioMessage.Target(4061, Vec3d.ofCenter(pos.east(60))));
    check(
        context,
        launcher.target().equals(pos.east(60))
            && launcher.targeted()
            && remote.last().equals("message.icbm_port.radio.target_set"),
        "A target message must retarget the launcher and confirm");
    RadioNetwork.broadcast(
        world, remote, new RadioMessage.Target(4061, Vec3d.ofCenter(pos.east(5000))));
    check(
        context,
        launcher.target().equals(pos.east(60))
            && remote.last().equals("message.icbm_port.radio.target_invalid"),
        "An unreachable radio target must be refused");
    RadioNetwork.broadcast(world, remote, new RadioMessage.Trigger(4061));
    check(
        context,
        launcher.getStack(0).isEmpty()
            && remote.last().equals("message.icbm_port.radio.launch_success"),
        "A trigger must fire the launcher and report success");
    RadioNetwork.broadcast(world, remote, new RadioMessage.Trigger(4061));
    check(
        context,
        remote.last().equals("message.icbm_port.radio.launch_failed"),
        "An empty launcher must report the failed launch");
    RadioNetwork.broadcast(world, remote, new RadioMessage.Trigger(4060));
    check(context, remote.replies.size() == 4, "Other channels must not reach the launcher");
    clearMissiles(context, pos);
    context.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 30)
  public void cruiseRadioQueuesExactlyOneLaunch(TestContext context) {
    var world = context.getWorld();
    BlockPos pos = context.getAbsolutePos(new BlockPos(2, 2, 2));
    world.setBlockState(
        pos, LauncherContent.CRUISE.getDefaultState().with(LauncherBlock.FACING, Direction.NORTH));
    var cruise = (LauncherBlockEntity) world.getBlockEntity(pos);
    cruise.setStack(0, new ItemStack(IcbmPort.CONDENSED_MISSILE));
    cruise.energyBuffer().setAmount(cruise.capacity());
    cruise.configure(pos.north(15), true, 4062);
    check(
        context,
        LauncherContent.CRUISE.fire(world, pos, 1) == LauncherStatus.ERROR_MIN_RANGE,
        "Cruise targets closer than 20 blocks must be refused");
    cruise.configure(pos.north(40), true, 4062);
    RadioNetwork.register(world, cruise);
    var remote = new Probe(pos.up(4), 4062);
    RadioNetwork.broadcast(world, remote, new RadioMessage.Trigger(4062));
    check(
        context,
        cruise.hasPendingLaunch()
            && remote.last().equals("message.icbm_port.radio.launch_triggered"),
        "A cruise trigger must queue a firing package");
    RadioNetwork.broadcast(world, remote, new RadioMessage.Trigger(4062));
    check(
        context,
        remote.last().equals(LauncherStatus.ERROR_QUEUED.translationKey())
            && remote.replies.size() == 2,
        "A second trigger must report the queued launch");
    context.runAtTick(
        4,
        () -> {
          check(
              context,
              cruise.getStack(0).isEmpty() && cruise.lastStatus() == LauncherStatus.LAUNCHED,
              "The queued cruise launch must fire");
          clearMissiles(context, pos);
          context.complete();
        });
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = BATCH)
  public void remoteDetonatorCopiesChannelAndTriggers(TestContext context) {
    var world = context.getWorld();
    var launcher = legacy(context, new BlockPos(2, 2, 2));
    BlockPos pos = launcher.getPos();
    launcher.configure(pos, false, 4063);
    RadioNetwork.register(world, launcher);
    var player = context.createMockPlayer();
    player.setPosition(Vec3d.ofCenter(pos.west(2)));
    ItemStack detonator = new ItemStack(LauncherContent.RADIO_DETONATOR);
    player.setStackInHand(Hand.MAIN_HAND, detonator);
    detonator.getItem().use(world, player, Hand.MAIN_HAND);
    check(
        context,
        !launcher.getStack(0).isEmpty() && RadioItem.channel(detonator) == 0,
        "An untuned detonator must not transmit");
    detonator
        .getItem()
        .useOnBlock(
            new ItemUsageContext(
                player,
                Hand.MAIN_HAND,
                new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false)));
    check(
        context,
        RadioItem.channel(detonator) == 4063,
        "Using the detonator on a radio block must copy its channel");
    detonator.getItem().use(world, player, Hand.MAIN_HAND);
    check(
        context,
        launcher.getStack(0).isEmpty()
            && launcher.lastStatus() == LauncherStatus.LAUNCHED
            && player.getItemCooldownManager().isCoolingDown(detonator.getItem()),
        "The detonator must trigger every receiver on its channel, then cool down");
    clearMissiles(context, pos);
    context.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = BATCH)
  public void laserDetonatorTargetsWhatItHits(TestContext context) {
    var world = context.getWorld();
    var launcher = legacy(context, new BlockPos(2, 2, 2));
    BlockPos pos = launcher.getPos(), mark = pos.east(12);
    launcher.configure(pos, false, 4064);
    RadioNetwork.register(world, launcher);
    var listener = new Probe(pos.up(6), 4064);
    RadioNetwork.register(world, listener);
    world.setBlockState(mark, Blocks.STONE.getDefaultState());
    var player = context.createMockPlayer();
    ItemStack laser = new ItemStack(LauncherContent.LASER_DESIGNATOR);
    RadioItem.setChannel(laser, 4064);
    player.setStackInHand(Hand.MAIN_HAND, laser);

    Vec3d above = Vec3d.ofCenter(pos.up(3));
    player.refreshPositionAndAngles(above.x, above.y, above.z, 0, 90);
    laser.getItem().use(world, player, Hand.MAIN_HAND);
    check(
        context,
        listener.heard.isEmpty() && !player.getItemCooldownManager().isCoolingDown(laser.getItem()),
        "A ray that lands on a launcher must be ignored");

    player.refreshPositionAndAngles(above.x, above.y, above.z, 0, -90);
    laser.getItem().use(world, player, Hand.MAIN_HAND);
    check(context, listener.heard.isEmpty(), "A miss must not transmit");

    Vec3d overMark = Vec3d.ofCenter(mark.up(3));
    player.refreshPositionAndAngles(overMark.x, overMark.y, overMark.z, 0, 90);
    laser.getItem().use(world, player, Hand.MAIN_HAND);
    check(
        context,
        listener.heard.size() == 1
            && listener.heard.get(0) instanceof RadioMessage.TriggerTarget aim
            && new BlockPos(aim.target()).equals(mark.up()),
        "A hit must broadcast trigger-with-target at the hit point");
    check(
        context,
        launcher.target().equals(mark.up())
            && launcher.targeted()
            && launcher.getStack(0).isEmpty()
            && launcher.lastStatus() == LauncherStatus.LAUNCHED,
        "Launchers on the channel must retarget to the hit and fire");
    RadioNetwork.remove(world, listener);
    world.setBlockState(mark, Blocks.AIR.getDefaultState());
    clearMissiles(context, pos);
    context.complete();
  }
}

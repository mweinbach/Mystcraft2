package dev.icbm.port.defense;

import dev.icbm.port.IcbmPort;
import dev.icbm.port.entity.MissileEntity;
import dev.icbm.port.radio.RadioMessage;
import dev.icbm.port.radio.RadioNetwork;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Radar station, EMP tower and radio parity with MIT ICBM-Classic {@code TileRadarStation}, {@code
 * TileEMPTower}, {@code RadioRadar}, {@code RadioEmpTower} and {@code lib/radio/RadioRegistry}.
 * Radio is dimension-wide, so every test uses its own channel (40xx). EMP-firing tests run in their
 * own batch so the real EMP field cannot disable other fixtures.
 */
public class DefenseParityGameTests implements FabricGameTest {
  private static final String EMP_BATCH = "pr5EmpParity";
  /**
   * The radar scans a full-height square column of half-width 100, which covers every neighbouring
   * GameTest structure in a batch. In the default batch, other fixtures' missiles (for example the
   * SAM radio test at the same height) entered the column and changed the incoming count and the
   * nearest track, which is why these tests failed in the default batch: the original strict
   * assertions at the original heights passed once isolated in this batch. They now run alone here
   * and still compare against the radar's own lists rather than fixed counts.
   */
  private static final String RADAR_BATCH = "pr5RadarParity";

  private static void check(TestContext context, boolean condition, String message) {
    if (!condition) context.throwGameTestException(message);
  }

  /** A plain radio that records what it hears and the replies it gets. */
  static class Probe implements RadioNetwork.Receiver, RadioNetwork.Sender {
    final BlockPos pos;
    final int channel;
    final List<RadioMessage> heard = new ArrayList<>();
    final List<String> replies = new ArrayList<>();

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
  }

  private static MissileEntity missile(TestContext context, Vec3d at, Vec3d velocity) {
    MissileEntity missile = new MissileEntity(IcbmPort.MISSILE_ENTITY, context.getWorld());
    missile.setPosition(at);
    missile.setVelocity(velocity);
    missile.setNoGravity(true);
    missile.age = 5;
    context.getWorld().spawnEntity(missile);
    return missile;
  }

  private static DefenseMachineBlockEntity place(
      TestContext context, BlockPos pos, net.minecraft.block.Block block) {
    context.getWorld().setBlockState(pos, block.getDefaultState());
    return (DefenseMachineBlockEntity) context.getWorld().getBlockEntity(pos);
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = RADAR_BATCH)
  public void radarSeparatesIncomingFromDetected(TestContext context) {
    BlockPos pos = context.getAbsolutePos(new BlockPos(2, 4, 2));
    var radar = place(context, pos, DefenseContent.RADAR_STATION);
    radar.energyBuffer().setAmount(DefenseMachineBlockEntity.RADAR_CAPACITY);
    Vec3d center = Vec3d.ofCenter(pos);
    var closing = missile(context, center.add(20, 10, 0), new Vec3d(-1, 0, 0));
    var leaving = missile(context, center.add(-20, 10, 0), new Vec3d(-1, 0, 0));
    var fresh = missile(context, center.add(0, 10, 20), new Vec3d(0, 0, -1));
    fresh.age = 1;
    // Square column of half-width = range (100): far on X but inside on Z, at any height.
    var outside = missile(context, center.add(101, 0, 0), new Vec3d(-1, 0, 0));
    var high = missile(context, center.add(0, 60, 30), new Vec3d(0, 0, -1));
    radar.scan();
    check(
        context,
        radar.incomingThreats().contains(closing) && radar.incomingThreats().contains(high),
        "Closing missiles anywhere in the column must be incoming");
    check(
        context,
        radar.detectedThreats().contains(leaving) && !radar.incomingThreats().contains(leaving),
        "Receding missiles are detected but not incoming");
    check(
        context,
        !radar.detectedThreats().contains(fresh) && !radar.incomingThreats().contains(fresh),
        "Missiles that flew one tick or less are ignored");
    check(
        context,
        !radar.detectedThreats().contains(outside) && !radar.incomingThreats().contains(outside),
        "The detection area is a square column of half-width range");
    DefenseMachineBlockEntity.tick(context.getWorld(), pos, context.getWorld().getBlockState(pos), radar);
    var state = context.getWorld().getBlockState(pos);
    // GameTests share one world: other fixtures' missiles may also fly through this column, so the
    // output is compared against the radar's own incoming list rather than a fixed count.
    int expected = Math.min(15, radar.incomingThreats().size());
    check(
        context,
        radar.radarState() == DefenseMachineBlockEntity.RadarState.DANGER
            && radar.incomingThreats().contains(closing)
            && expected >= 2
            && state.get(DefenseMachineBlock.POWER) == expected,
        "Incoming missiles set DANGER and redstone strength = incoming count (state="
            + radar.radarState()
            + ", incoming="
            + radar.incomingThreats().size()
            + ", power="
            + state.get(DefenseMachineBlock.POWER)
            + ", emp="
            + DefenseContent.isEmpDisabled(context.getWorld(), pos)
            + ")");
    check(
        context,
        state.getStrongRedstonePower(context.getWorld(), pos, Direction.UP) == expected,
        "Radar strong power must equal its weak power");
    check(context, !radar.toggleRedstoneOutput(), "Toggle must switch redstone output off");
    DefenseMachineBlockEntity.tick(context.getWorld(), pos, context.getWorld().getBlockState(pos), radar);
    check(
        context,
        context.getWorld().getBlockState(pos).get(DefenseMachineBlock.POWER) == 0,
        "Disabled redstone output must stay dark while tracking");
    NbtCompound saved = radar.createNbt();
    var restored = new DefenseMachineBlockEntity(pos, DefenseContent.RADAR_STATION.getDefaultState());
    restored.readNbt(saved);
    check(context, !restored.outputsRedstone(), "Redstone output toggle must persist");
    List.of(closing, leaving, fresh, outside, high).forEach(MissileEntity::discard);
    context.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = RADAR_BATCH)
  public void radarRadioAlertsAfterSamDelay(TestContext context) {
    var world = context.getWorld();
    BlockPos pos = context.getAbsolutePos(new BlockPos(2, 6, 2));
    var radar = place(context, pos, DefenseContent.RADAR_STATION);
    DefenseGameTests.tune(radar, 4041);
    radar.energyBuffer().setAmount(DefenseMachineBlockEntity.RADAR_CAPACITY);
    var probe = new Probe(pos.up(5), 4041);
    RadioNetwork.register(world, probe);
    var target = missile(context, Vec3d.ofCenter(pos).add(0, 20, 0), new Vec3d(0, -0.5, 0));
    DefenseMachineBlockEntity.tick(world, pos, world.getBlockState(pos), radar);
    // The alert names the nearest incoming track; other fixtures' missiles may be nearer.
    var nearest = radar.incomingThreats().isEmpty() ? null : radar.incomingThreats().get(0);
    check(
        context,
        radar.incomingThreats().contains(target)
            && probe.heard.size() == 1
            && probe.heard.get(0) instanceof RadioMessage.IncomingMissile incoming
            && incoming.missile().equals(nearest.getUuid())
            && incoming.shouldTrigger(),
        "Radar must broadcast an incoming-missile trigger on its channel (incoming="
            + radar.incomingThreats().size()
            + ", heard="
            + probe.heard.size()
            + ", emp="
            + DefenseContent.isEmpDisabled(world, pos)
            + ")");
    DefenseMachineBlockEntity.tick(world, pos, world.getBlockState(pos), radar);
    check(context, probe.heard.size() == 1, "Radar must wait the SAM delay between alerts");
    var restored = new DefenseMachineBlockEntity(pos, DefenseContent.RADAR_STATION.getDefaultState());
    restored.readNbt(radar.createNbt());
    check(
        context,
        restored.createNbt().getInt("SamDelay") == radar.createNbt().getInt("SamDelay")
            && radar.createNbt().getInt("SamDelay") > 0,
        "Radar alert delay must persist");
    RadioNetwork.remove(world, probe);
    target.discard();
    context.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void radioRegistryFollowsLifecycleAndBoundsRecursion(TestContext context) {
    var world = context.getWorld();
    BlockPos pos = context.getAbsolutePos(new BlockPos(2, 120, 2));
    var radar = place(context, pos, DefenseContent.RADAR_STATION);
    // Either the Fabric load event or the first tick registers the receiver.
    DefenseMachineBlockEntity.tick(world, pos, world.getBlockState(pos), radar);
    check(context, RadioNetwork.isRegistered(world, radar), "Loaded radio blocks must register");
    world.removeBlock(pos, false);
    check(context, !RadioNetwork.isRegistered(world, radar), "Removed radio blocks must unregister");

    // Two radios that answer every message by broadcasting again are cut off at MAX_DEPTH.
    class Echo extends Probe {
      Echo(BlockPos at) {
        super(at, 4042);
      }

      @Override
      public void onMessage(RadioNetwork.Sender sender, RadioMessage message) {
        super.onMessage(sender, message);
        RadioNetwork.broadcast(world, this, message);
      }
    }
    var a = new Echo(pos.up(2));
    var b = new Echo(pos.up(3));
    RadioNetwork.register(world, a);
    RadioNetwork.register(world, b);
    RadioNetwork.broadcast(world, a, new RadioMessage.Trigger(4042));
    check(
        context,
        a.heard.size() + b.heard.size() == RadioNetwork.MAX_DEPTH,
        "Nested broadcasts must stop at the depth bound");
    check(
        context,
        RadioNetwork.broadcast(world, a, new RadioMessage.Trigger(0)) == 0,
        "Channel 0 means unset and never transmits");
    RadioNetwork.remove(world, a);
    RadioNetwork.remove(world, b);
    context.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void defenseScreenButtonsAreValidatedServerSide(TestContext context) {
    BlockPos radarPos = context.getAbsolutePos(new BlockPos(2, 2, 2));
    BlockPos empPos = radarPos.east(2);
    var radar = place(context, radarPos, DefenseContent.RADAR_STATION);
    var emp = place(context, empPos, DefenseContent.EMP_TOWER);
    var player = context.createMockPlayer();
    player.setPosition(Vec3d.ofCenter(radarPos.up()));
    var radarScreen = new DefenseScreenHandler(1, player.getInventory(), radar);
    var empScreen = new DefenseScreenHandler(2, player.getInventory(), emp);
    check(context, radarScreen.onButtonClick(player, 14), "+100 channel must be accepted");
    check(context, radar.radioChannel() == 100, "+100 must move the channel by one hundred");
    check(context, radarScreen.onButtonClick(player, 13), "-100 channel must be accepted");
    check(context, radarScreen.onButtonClick(player, 9), "Radar must accept redstone toggle");
    check(context, !radar.outputsRedstone(), "Radar redstone button must toggle output");
    check(context, !empScreen.onButtonClick(player, 9), "Only radars have a redstone toggle");
    check(
        context,
        !radarScreen.onButtonClick(player, DefenseMachineBlockEntity.MAX_BUTTON + 1)
            && !radarScreen.onButtonClick(player, -1),
        "Unknown buttons must fail closed");
    player.setPosition(Vec3d.ofCenter(radarPos.up(20)));
    check(context, !radarScreen.onButtonClick(player, 14), "Distant players must be rejected");
    check(
        context,
        radar.properties().size() == DefenseMachineBlockEntity.PROPERTY_COUNT,
        "Server property count must match the client delegate");
    context.complete();
  }

  /**
   * One test method in its own batch: every real EMP fired here would disable fixtures of
   * concurrently running tests. Tower A is shrunk to range 1 so its own field cannot reach tower B.
   */
  /**
   * Lowering an EMP tower's radius shrinks its capacity but keeps the stored energy (upstream); a
   * chunk reload must keep it too instead of clamping it to the new capacity.
   */
  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = EMP_BATCH)
  public void empTowerKeepsOverCapacityEnergyAcrossReload(TestContext context) {
    BlockPos pos = context.getAbsolutePos(new BlockPos(1, 40, 1));
    var tower = place(context, pos, DefenseContent.EMP_TOWER);
    long full = tower.energyBuffer().getCapacity();
    tower.energyBuffer().setAmount(full);
    for (int i = 0; i < 6; i++) tower.configure(0);
    check(
        context,
        tower.range() == 1 && tower.energyBuffer().getCapacity() < full,
        "Range 1 must shrink the capacity");
    check(context, tower.energyBuffer().getAmount() == full, "Shrinking keeps the stored energy");
    NbtCompound saved = tower.createNbt();
    tower.readNbt(saved);
    check(
        context,
        tower.energyBuffer().getAmount() == full,
        "A reload must keep over-capacity energy: " + tower.energyBuffer().getAmount());
    var copy = place(context, pos.east(3), DefenseContent.EMP_TOWER);
    copy.readNbt(saved);
    check(
        context,
        copy.range() == 1 && copy.energyBuffer().getAmount() == full,
        "A fresh tower loading the save must keep it too");
    context.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = EMP_BATCH)
  public void empTowerParity(TestContext context) {
    var world = context.getWorld();
    BlockPos aPos = context.getAbsolutePos(new BlockPos(1, 60, 1)), bPos = aPos.east(5);
    var a = place(context, aPos, DefenseContent.EMP_TOWER);
    var b = place(context, bPos, DefenseContent.EMP_TOWER);
    check(
        context,
        b.energyBuffer().getCapacity() == 420_000,
        "Range 60 capacity must be 60^2*100 firing cost plus 100 ticks of 600 upkeep");
    try (var tx = Transaction.openOuter()) {
      check(
          context,
          b.energyBuffer().insert(50_000, tx) == DefenseMachineBlockEntity.EMP_INPUT,
          "EMP must accept at most 10,000 per insertion");
    }

    for (int i = 0; i < 6; i++) a.configure(0);
    check(context, a.range() == 1 && a.empFiringCost() == 100, "Range 1 fixture must be cheap");
    a.energyBuffer().setAmount(500);
    DefenseMachineBlockEntity.tick(world, aPos, world.getBlockState(aPos), a);
    check(
        context,
        a.energyBuffer().getAmount() == 500 - a.empTickingCost(),
        "EMP must pay range x 10 upkeep every tick");
    world.setBlockState(aPos.up(), Blocks.REDSTONE_BLOCK.getDefaultState());
    DefenseMachineBlockEntity.tick(world, aPos, world.getBlockState(aPos), a);
    check(context, a.cooldown() == 0, "Weak redstone must not fire the EMP tower");
    world.setBlockState(aPos.up(), Blocks.AIR.getDefaultState());
    // A powered lever on the tower's own face strongly powers it.
    world.setBlockState(
        aPos.north(),
        Blocks.LEVER
            .getDefaultState()
            .with(
                net.minecraft.block.LeverBlock.FACE,
                net.minecraft.block.enums.WallMountLocation.WALL)
            .with(net.minecraft.block.LeverBlock.FACING, Direction.NORTH)
            .with(net.minecraft.block.LeverBlock.POWERED, true));
    check(
        context,
        world.getReceivedStrongRedstonePower(aPos) > 0,
        "Fixture must strongly power the tower");
    DefenseMachineBlockEntity.tick(world, aPos, world.getBlockState(aPos), a);
    check(
        context,
        a.cooldown() == DefenseMachineBlockEntity.EMP_COOLDOWN,
        "Strong redstone must fire the EMP tower");
    try (var tx = Transaction.openOuter()) {
      check(
          context,
          a.energyBuffer().insert(100, tx) == 0 && !a.energyBuffer().supportsInsertion(),
          "A cooling EMP tower must refuse energy");
    }
    world.setBlockState(aPos.north(), Blocks.AIR.getDefaultState());

    check(context, !DefenseContent.isEmpDisabled(world, bPos), "Tower B must be outside A's EMP");
    for (int i = 0; i < 4; i++) b.configure(0);
    DefenseGameTests.tune(b, 4051);
    b.energyBuffer().setAmount(b.energyBuffer().getCapacity());
    RadioNetwork.register(world, b);
    var radar = new Probe(bPos.up(3), 4051);
    Vec3d center = Vec3d.ofCenter(bPos);
    RadioNetwork.broadcast(
        world,
        radar,
        new RadioMessage.IncomingMissile(4051, UUID.randomUUID(), center.add(200, 0, 0), true));
    check(context, b.cooldown() == 0, "Missiles outside the EMP range must not fire it");
    RadioNetwork.broadcast(
        world,
        radar,
        new RadioMessage.IncomingMissile(4051, UUID.randomUUID(), center.add(10, 0, 0), false));
    check(context, b.cooldown() == 0, "Non-trigger alerts must not fire it");
    RadioNetwork.broadcast(
        world,
        radar,
        new RadioMessage.IncomingMissile(4051, UUID.randomUUID(), center.add(10, 0, 0), true));
    check(
        context,
        b.cooldown() == DefenseMachineBlockEntity.EMP_COOLDOWN
            && radar.replies.contains("message.icbm_port.radio.emp_fired"),
        "An in-range incoming-missile trigger must fire and reply");
    RadioNetwork.broadcast(world, radar, new RadioMessage.Trigger(4051));
    check(context, radar.replies.size() == 1, "A disabled, cooling tower must not reply again");
    context.complete();
  }
}

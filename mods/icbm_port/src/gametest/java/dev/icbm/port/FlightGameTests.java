package dev.icbm.port;

import dev.icbm.port.entity.MissileEntity;
import dev.icbm.port.flight.*;
import dev.icbm.port.launcher.*;
import dev.icbm.port.payload.PayloadType;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class FlightGameTests implements FabricGameTest {
  private void check(TestContext context, boolean condition, String message) {
    if (!condition) context.throwGameTestException(message);
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void warmupAndHealthSurviveReload(TestContext context) {
    BlockPos pos = context.getAbsolutePos(new BlockPos(2, 20, 2));
    MissileEntity missile = new MissileEntity(IcbmPort.MISSILE_ENTITY, context.getWorld());
    missile.setPosition(Vec3d.ofCenter(pos));
    missile.setPayload(PayloadType.EMP);
    missile.configureTarget(pos.east(40));
    for (int i = 0; i < 12; i++) missile.flightPlan().nextVelocity(missile);
    missile.damage(DamageSource.GENERIC, 30);
    NbtCompound saved = missile.writeNbt(new NbtCompound());
    MissileEntity restored = new MissileEntity(IcbmPort.MISSILE_ENTITY, context.getWorld());
    restored.readNbt(saved);
    check(
        context,
        restored.flightPlan().stage() == StagedFlight.Stage.WARMUP
            && restored.flightPlan().stageTicks() == 12,
        "Restart must preserve staged warmup progress");
    check(
        context,
        restored.getMissileHealth() == 70,
        "Source tier-three health and remaining damage must persist");
    for (int i = 0; i < 28; i++) restored.flightPlan().nextVelocity(restored);
    check(
        context,
        restored.flightPlan().stage() == StagedFlight.Stage.CLIMB,
        "Warmup must finish after original forty total ticks");
    context.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void fuelExhaustionEnablesGravity(TestContext context) {
    BlockPos pos = context.getAbsolutePos(new BlockPos(2, 20, 2));
    MissileEntity missile = new MissileEntity(IcbmPort.MISSILE_ENTITY, context.getWorld());
    missile.setPosition(Vec3d.ofCenter(pos));
    missile.configureFuel(2);
    context.getWorld().spawnEntity(missile);
    context.runAtTick(
        4,
        () -> {
          check(
              context,
              missile.getFuel() == 0 && !missile.hasNoGravity(),
              "Cruise fuel exhaustion must enter falling flight");
          missile.discard();
          context.complete();
        });
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 40)
  public void savedVirtualFlightReentersOnce(TestContext context) {
    BlockPos pos = context.getAbsolutePos(new BlockPos(2, 20, 2));
    MissileEntity missile = new MissileEntity(IcbmPort.MISSILE_ENTITY, context.getWorld());
    missile.setPosition(Vec3d.ofCenter(pos));
    missile.configureTarget(pos.east(2));
    context.getWorld().spawnEntity(missile);
    NbtCompound oldEntity = missile.writeNbt(new NbtCompound());
    var uuid = missile.getUuid();
    check(
        context,
        FlightLedger.tryCapture(missile, missile.flightPlan()),
        "Virtual flight admission must succeed");
    FlightLedger ledger = FlightLedger.get(context.getWorld());
    FlightLedger restored = FlightLedger.read(ledger.writeNbt(new NbtCompound()));
    check(
        context,
        restored.generation(uuid) == 1 && restored.pendingCount() > 0,
        "Flight generation and pending entity must survive world-state reload");
    MissileEntity stale = new MissileEntity(IcbmPort.MISSILE_ENTITY, context.getWorld());
    stale.readNbt(oldEntity);
    check(
        context,
        FlightLedger.suppressDuplicate(stale, stale.flightPlan()),
        "Stale entity save must be suppressed after transfer");
    context.runAtTick(
        8,
        () -> {
          var reentry = context.getWorld().getEntity(uuid);
          check(
              context,
              reentry instanceof MissileEntity
                  && ((MissileEntity) reentry).flightPlan().stage() == StagedFlight.Stage.DESCENT,
              "Virtual flight must reenter through its registered entity type");
          reentry.discard();
          context.complete();
        });
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void energyPoolSplitsImmediately(TestContext context) {
    // Upstream NetworkEnergyStorage: energy offered to a frame/connector is split across the
    // connected launchers; launchers never pull from each other.
    BlockPos pos = context.getAbsolutePos(new BlockPos(2, 2, 2));
    context.getWorld().setBlockState(pos, LauncherContent.BASE_TIER_1.getDefaultState());
    context.getWorld().setBlockState(pos.east(), LauncherContent.CONNECTOR.getDefaultState());
    context.getWorld().setBlockState(pos.east(2), LauncherContent.BASE_TIER_1.getDefaultState());
    LauncherBlockEntity first = (LauncherBlockEntity) context.getWorld().getBlockEntity(pos),
        second = (LauncherBlockEntity) context.getWorld().getBlockEntity(pos.east(2));
    var network =
        team.reborn.energy.api.EnergyStorage.SIDED.find(
            context.getWorld(), pos.east(), net.minecraft.util.math.Direction.UP);
    check(context, network != null, "Connector must expose the network energy view");
    try (var tx = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
      check(context, network.insert(2400, tx) == 2400, "Network must accept two launches");
      check(context, network.extract(100, tx) == 0, "Network view must be insert-only");
      tx.commit();
    }
    check(
        context,
        first.energy() == 1200 && second.energy() == 1200,
        "Network insertion must split evenly without duplication");
    first.energyBuffer().setAmount(0);
    second.energyBuffer().setAmount(0);
    context.getWorld().removeBlock(pos.east(), false);
    check(
        context,
        team.reborn.energy.api.EnergyStorage.SIDED.find(
                context.getWorld(), pos.east(), net.minecraft.util.math.Direction.UP)
            == null,
        "Removing the connector must remove its network view");
    context.getWorld().setBlockState(pos.east(), LauncherContent.FRAME.getDefaultState());
    context.getWorld().setBlockState(pos.east(2), net.minecraft.block.Blocks.AIR.getDefaultState());
    var alone =
        team.reborn.energy.api.EnergyStorage.SIDED.find(
            context.getWorld(), pos.east(), net.minecraft.util.math.Direction.UP);
    try (var tx = net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()) {
      alone.insert(2400, tx);
      tx.commit();
    }
    check(
        context,
        first.energy() == 1200,
        "A split network must only charge the launchers still connected");
    context.complete();
  }
}

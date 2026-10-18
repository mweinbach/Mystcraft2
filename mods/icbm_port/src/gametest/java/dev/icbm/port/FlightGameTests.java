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
    BlockPos pos = context.getAbsolutePos(new BlockPos(2, 2, 2));
    context.getWorld().setBlockState(pos, LauncherContent.BASE_TIER_1.getDefaultState());
    context.getWorld().setBlockState(pos.east(), LauncherContent.CONNECTOR.getDefaultState());
    context.getWorld().setBlockState(pos.east(2), LauncherContent.BASE_TIER_1.getDefaultState());
    LauncherBlockEntity receiver = (LauncherBlockEntity) context.getWorld().getBlockEntity(pos),
        donor = (LauncherBlockEntity) context.getWorld().getBlockEntity(pos.east(2));
    donor.energyBuffer().setAmount(1200);
    receiver.shareEnergy();
    check(
        context,
        receiver.energy() == 1200 && donor.energy() == 0,
        "Connected bases must share source-default launch energy without duplication");
    receiver.energyBuffer().setAmount(0);
    donor.energyBuffer().setAmount(1200);
    context.getWorld().removeBlock(pos.east(), false);
    receiver.shareEnergy();
    check(
        context,
        receiver.energy() == 0 && donor.energy() == 1200,
        "Removing connector must immediately split energy pools");
    context.complete();
  }
}

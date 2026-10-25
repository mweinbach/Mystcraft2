package dev.icbm.port;

import dev.icbm.port.payload.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.test.*;
import net.minecraft.util.math.*;

public class PayloadGameTests implements FabricGameTest {
  private static void check(TestContext c, boolean condition, String message) {
    if (!condition) c.throwGameTestException(message);
  }

  // Shrapnel, anvil and fragmentation bursts are covered by payload.FragmentParityGameTests.

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void mutationConvertsPigs(TestContext c) {
    var pig = EntityType.PIG.create(c.getWorld());
    BlockPos pos = c.getAbsolutePos(new BlockPos(2, 2, 2));
    pig.refreshPositionAndAngles(pos.getX(), pos.getY(), pos.getZ(), 0, 0);
    c.getWorld().spawnEntity(pig);
    Payloads.detonate(c.getWorld(), pig.getPos(), PayloadType.MUTATION, null);
    check(c, pig.isRemoved(), "Mutation must replace the original pig");
    var mutants =
        c.getWorld()
            .getEntitiesByType(EntityType.ZOMBIFIED_PIGLIN, new Box(pos).expand(2), e -> true);
    check(c, mutants.size() == 1, "Mutation must create a zombified piglin");
    mutants.forEach(e -> e.discard());
    c.complete();
  }

  // defaultBatch places every test in one world. Mutation's radius is 80 and other
  // blasts overlap this subject, and an unpinned pig can walk off the structure.
  // A separate batch and a stationary target keep those races out of the assertion.
  @GameTest(
      templateName = FabricGameTest.EMPTY_STRUCTURE,
      tickLimit = 80,
      batchId = "icbmDebilitation")
  public void debilitationLeavesLivingTargetsButAppliesDistinctEffects(TestContext c) {
    var pig = EntityType.PIG.create(c.getWorld());
    BlockPos pos = c.getAbsolutePos(new BlockPos(2, 2, 2));
    pig.setAiDisabled(true);
    pig.setNoGravity(true);
    pig.refreshPositionAndAngles(pos.getX(), pos.getY(), pos.getZ(), 0, 0);
    c.getWorld().spawnEntity(pig);
    Payloads.detonate(c.getWorld(), pig.getPos(), PayloadType.DEBILITATION, null);
    c.runAtTick(
        35,
        () -> {
          dev.icbm.port.payload.PayloadQueueAssertions.removeTestDebilitation(
              c, new Vec3d(pos.getX(), pos.getY(), pos.getZ()));
          check(c, pig.isAlive(), "Debilitation must not directly kill target");
          check(
              c,
              pig.hasStatusEffect(StatusEffects.POISON)
                  && pig.hasStatusEffect(StatusEffects.MINING_FATIGUE),
              "Debilitation must persist its gas effects");
          pig.discard();
          c.complete();
        });
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void savedTerrainWorkResumes(TestContext c) {
    dev.icbm.port.payload.PayloadQueueAssertions.savedTerrainWorkResumes(c);
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void queuedWorldWorkStaysWithinBudget(TestContext c) {
    dev.icbm.port.payload.PayloadQueueAssertions.worldBudgetBoundsWork(c);
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void malformedSavedWorkAndDeferredAdmissionStayBounded(TestContext c) {
    dev.icbm.port.payload.PayloadQueueAssertions.malformedSaveAndDeferredAdmissionAreBounded(c);
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void queuedFieldsKeepElapsedLifetimeAcrossReload(TestContext c) {
    dev.icbm.port.payload.PayloadQueueAssertions.loadedQueuesKeepRealTimeFieldLifetimes(c);
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void capturedInventoryRestoresOnceAfterReload(TestContext c) {
    dev.icbm.port.payload.SimulationAssertions.flyingInventoryHasOneOwnerAcrossReload(c);
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void rayEnergySurvivesReloadAndRespectsShielding(TestContext c) {
    dev.icbm.port.payload.SimulationAssertions.raysResumeAndRespectShielding(c);
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void singularityConsumesAndPersists(TestContext c) {
    dev.icbm.port.payload.SimulationAssertions.singularityPersistsAndConsumesCapturedBlocks(c);
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void redmatterSweepsMovesButNotTeleports(TestContext c) {
    dev.icbm.port.payload.SimulationAssertions.redmatterSweepsMovesButNotTeleports(c);
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void gasRespectsEnclosureAfterReload(TestContext c) {
    dev.icbm.port.payload.SimulationAssertions.gasCannotPassSolidEnclosure(c);
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 240)
  public void placedRedmatterConsumesTerrainAndItems(TestContext c) {
    dev.icbm.port.payload.SimulationAssertions.placedRedmatterReachesDistantTerrain(c);
  }

  // Same shared-world race as debilitation: a passing health check was lost to
  // collateral from a neighbor running in defaultBatch. This batch runs alone.
  @GameTest(
      templateName = FabricGameTest.EMPTY_STRUCTURE,
      tickLimit = 80,
      batchId = "icbmAntimatter")
  public void antimatterNeutralizationSuppressesCollateralPulse(TestContext c) {
    dev.icbm.port.payload.SimulationAssertions.antimatterNeutralizesWithoutEntityPulse(c);
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void capturedBlockSurvivesCoreLoadOrdering(TestContext c) {
    dev.icbm.port.payload.SimulationAssertions.restoredCaptureWaitsForItsCore(c);
  }
}

package dev.icbm.port;

import dev.icbm.port.payload.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.test.*;
import net.minecraft.util.math.*;

public class PayloadGameTests implements FabricGameTest {
  private static void check(TestContext c, boolean condition, String message) {
    if (!condition) c.throwGameTestException(message);
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void shrapnelCreatesThirtyBurningProjectiles(TestContext c) {
    Vec3d center = Vec3d.ofCenter(c.getAbsolutePos(new BlockPos(2, 3, 2)));
    Payloads.detonate(c.getWorld(), center, PayloadType.SHRAPNEL, null);
    var arrows =
        c.getWorld()
            .getEntitiesByClass(ArrowEntity.class, new Box(center, center).expand(2), e -> true);
    check(c, arrows.size() == 30, "Shrapnel must spawn 30 fragments");
    check(
        c,
        arrows.stream().allMatch(e -> e.isOnFire() && e.getDamage() == 11),
        "Shrapnel fragments retain damage11 and incendiary flag");
    arrows.forEach(ArrowEntity::discard);
    c.complete();
  }

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

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 80)
  public void debilitationLeavesLivingTargetsButAppliesDistinctEffects(TestContext c) {
    var pig = EntityType.PIG.create(c.getWorld());
    BlockPos pos = c.getAbsolutePos(new BlockPos(2, 2, 2));
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
  public void gasRespectsEnclosureAfterReload(TestContext c) {
    dev.icbm.port.payload.SimulationAssertions.gasCannotPassSolidEnclosure(c);
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 240)
  public void placedRedmatterConsumesTerrainAndItems(TestContext c) {
    dev.icbm.port.payload.SimulationAssertions.placedRedmatterReachesDistantTerrain(c);
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 80)
  public void antimatterNeutralizationSuppressesCollateralPulse(TestContext c) {
    dev.icbm.port.payload.SimulationAssertions.antimatterNeutralizesWithoutEntityPulse(c);
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void capturedBlockSurvivesCoreLoadOrdering(TestContext c) {
    dev.icbm.port.payload.SimulationAssertions.restoredCaptureWaitsForItsCore(c);
  }
}

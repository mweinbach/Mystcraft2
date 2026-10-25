package dev.icbm.port.payload;

import static dev.icbm.port.payload.BlastTestSupport.*;

import dev.icbm.port.exposure.FalloutTestHooks;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Parity with upstream {@code BlastThermobaric} and {@code BlastNuclear}: the ray sphere, the
 * 50-block damage pulse and the nuclear aftermath (fallout and mutation). Each runs alone in its
 * batch and high above its structure, because the pulse and aftermath reach far beyond it.
 */
public final class ThermalBlastParityGameTests implements FabricGameTest {
  @GameTest(
      templateName = FabricGameTest.EMPTY_STRUCTURE,
      batchId = "icbmBlastThermobaric",
      tickLimit = 60)
  public void thermobaricClearsItsSphereAndPulsesDamage(TestContext c) {
    BlockPos origin = c.getAbsolutePos(new BlockPos(2, 40, 2));
    Vec3d center = Vec3d.ofCenter(origin);
    c.getWorld().setBlockState(origin.down(), Blocks.STONE.getDefaultState(), 2);
    c.getWorld().setBlockState(origin.east(2), Blocks.BEDROCK.getDefaultState(), 2);
    var pig = still(c, EntityType.PIG, center.add(0, 0, 3));
    try {
      check(c, run(c, job(c, PayloadType.THERMOBARIC, center, 3, null), 40), "Blast must end");
      check(c, c.getWorld().isAir(origin.down()), "Thermobaric rays must clear nearby stone");
      check(
          c,
          c.getWorld().getBlockState(origin.east(2)).isOf(Blocks.BEDROCK),
          "Unbreakable blocks must survive");
      check(c, !pig.isAlive(), "The thermobaric pulse must be lethal close to the blast");
    } finally {
      pig.discard();
      c.getWorld().removeBlock(origin.east(2), false);
      clearDebris(c, center, 8);
    }
    c.complete();
  }

  @GameTest(
      templateName = FabricGameTest.EMPTY_STRUCTURE,
      batchId = "icbmBlastNuclear",
      tickLimit = 60)
  public void nuclearAftermathQueuesFalloutAndMutatesMobs(TestContext c) {
    BlockPos origin = c.getAbsolutePos(new BlockPos(2, 40, 2));
    Vec3d center = Vec3d.ofCenter(origin);
    c.getWorld().setBlockState(origin.down(), Blocks.STONE.getDefaultState(), 2);
    var pig = still(c, EntityType.PIG, center.add(0, 0, 3));
    int fallout = 0;
    try {
      var job = job(c, PayloadType.NUCLEAR, center, 3, null);
      // Without the pulse the pig survives long enough to show the mutation pass.
      job.suppressEntityDamage = true;
      check(c, run(c, job, 40), "Nuclear blast must end");
      fallout =
          FalloutTestHooks.removeAt(
              c.getWorld(), new BlockPos(center), (int) ThermalBlast.NUCLEAR_AFTERMATH_RADIUS);
      check(c, fallout == 1, "Nuclear blasts must queue radius-80 fallout");
      check(c, c.getWorld().isAir(origin.down()), "Nuclear rays must clear nearby stone");
      check(c, pig.isRemoved(), "The aftermath must mutate the pig");
      var mutants =
          c.getWorld()
              .getEntitiesByType(
                  EntityType.ZOMBIFIED_PIGLIN, new Box(origin).expand(5), e -> true);
      check(c, mutants.size() == 1, "The pig must become a zombified piglin");
      mutants.forEach(Entity::discard);
    } finally {
      if (fallout == 0)
        FalloutTestHooks.removeAt(
            c.getWorld(), new BlockPos(center), (int) ThermalBlast.NUCLEAR_AFTERMATH_RADIUS);
      pig.discard();
      clearDebris(c, center, 8);
    }
    c.complete();
  }
}

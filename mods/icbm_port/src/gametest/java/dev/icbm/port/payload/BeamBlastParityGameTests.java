package dev.icbm.port.payload;

import static dev.icbm.port.payload.BlastTestSupport.*;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Parity with upstream {@code BlastBeam}, {@code BlastExothermic} and {@code BlastEndothermic}:
 * the block transformations and the endothermic blast's phases (beam progress reported through
 * {@link BeamEffects}, the five-second wait, and the mob effects on completion).
 */
public final class BeamBlastParityGameTests implements FabricGameTest {
  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "icbmBlastBeam")
  public void heatAndColdTransformBlocksAsUpstream(TestContext c) {
    BlockPos pos = c.getAbsolutePos(new BlockPos(2, 30, 2));
    var w = c.getWorld();
    try {
      w.setBlockState(pos, Blocks.WATER.getDefaultState(), 2);
      BeamBlast.cool(w, pos, 0, 10);
      check(c, w.getBlockState(pos).isOf(Blocks.ICE), "Cold must freeze water");
      w.setBlockState(pos, Blocks.LAVA.getDefaultState(), 2);
      BeamBlast.cool(w, pos, 0, 10);
      check(c, w.getBlockState(pos).isOf(Blocks.OBSIDIAN), "Cold must set lava sources");
      w.setBlockState(pos, Blocks.LAVA.getDefaultState().with(FluidBlock.LEVEL, 4), 2);
      BeamBlast.cool(w, pos, 0, 10);
      check(c, w.getBlockState(pos).isOf(Blocks.SNOW), "Cold must turn flowing lava to snow");
      w.setBlockState(pos, Blocks.NETHERRACK.getDefaultState(), 2);
      BeamBlast.cool(w, pos, 0, 10);
      check(c, w.getBlockState(pos).isOf(Blocks.DIRT), "Cold must turn netherrack to dirt");
      w.setBlockState(pos, Blocks.MAGMA_BLOCK.getDefaultState(), 2);
      BeamBlast.cool(w, pos, 0, 10);
      check(c, w.getBlockState(pos).isOf(Blocks.STONE), "Cold must cool magma to stone");
      w.setBlockState(pos, Blocks.WATER.getDefaultState(), 2);
      BeamBlast.heat(w, pos, 9.9, 10);
      check(c, w.isAir(pos), "Heat must boil water away at any distance");
      w.setBlockState(pos, Blocks.ICE.getDefaultState(), 2);
      BeamBlast.heat(w, pos, 9.9, 10);
      check(c, w.isAir(pos), "Heat must melt ice away at any distance");
      w.setBlockState(pos, Blocks.DIRT.getDefaultState(), 2);
      BeamBlast.heat(w, pos, 0, 10);
      check(c, w.getBlockState(pos).isOf(Blocks.NETHERRACK), "Heat must scorch soil");
    } finally {
      for (BlockPos p : BlockPos.iterate(pos.add(-1, -1, -1), pos.add(1, 1, 1)))
        w.setBlockState(p, Blocks.AIR.getDefaultState(), 2);
    }
    c.complete();
  }

  @GameTest(
      templateName = FabricGameTest.EMPTY_STRUCTURE,
      batchId = "icbmBlastEndothermic",
      tickLimit = 40)
  public void endothermicRunsItsBeamPhasesAndChillsMobs(TestContext c) {
    // In open air above the structure: the opening explosion and scans find nothing to break.
    BlockPos origin = c.getAbsolutePos(new BlockPos(2, 40, 2));
    Vec3d center = Vec3d.ofCenter(origin);
    List<Float> progress = new ArrayList<>();
    boolean[] died = {false};
    BeamEffects.Listener installed = BeamEffects.listener();
    BeamEffects.setListener(
        (world, at, id, r, g, b, value, dying) -> {
          if (at.equals(center)) {
            progress.add(value);
            died[0] |= dying;
          }
        });
    // An iron golem shrugs off the opening explosion and stays to receive the chill.
    var golem = still(c, EntityType.IRON_GOLEM, center.add(0, -1, 2));
    try {
      var job = job(c, PayloadType.ENDOTHERMIC, center, 3, null);
      int calls = 0;
      while (!serve(c, job) && calls < 400) calls++;
      check(
          c,
          calls >= BeamBlast.WAIT_TICKS && calls < 400,
          "The second scan must wait five seconds, then finish");
      check(
          c,
          progress.equals(List.of(.1f, .2f, .5f, .8f, 1f, 0f)) && died[0],
          "Beam progress must follow upstream's phases and end dying: " + progress);
      check(
          c,
          golem.hasStatusEffect(StatusEffects.SLOWNESS)
              && golem.hasStatusEffect(StatusEffects.MINING_FATIGUE)
              && golem.hasStatusEffect(StatusEffects.POISON),
          "Endothermic completion must chill mobs in range");
    } finally {
      BeamEffects.setListener(installed);
      golem.discard();
      clearDebris(c, center, 8);
    }
    c.complete();
  }
}

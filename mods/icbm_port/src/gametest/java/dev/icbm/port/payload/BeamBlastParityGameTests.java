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

  /**
   * Review fix: the exothermic conversion of the block below a stone or aggregate block must not
   * replace an unbreakable block (bedrock) that the scan never validated.
   */
  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void exothermicNeverConvertsUnbreakableBlocksBelow(TestContext c) {
    var world = c.getWorld();
    java.util.List<BlockPos> tops = new java.util.ArrayList<>();
    for (int x = 0; x < 8; x++)
      for (int z = 0; z < 8; z++) {
        BlockPos floor = c.getAbsolutePos(new BlockPos(x, 1, z));
        world.setBlockState(floor, net.minecraft.block.Blocks.BEDROCK.getDefaultState(), 2);
        world.setBlockState(
            floor.up(),
            (x < 4 ? net.minecraft.block.Blocks.STONE : net.minecraft.block.Blocks.SAND)
                .getDefaultState(),
            2);
        tops.add(floor.up());
      }
    // Distance 0 always converts; repeat until every top block has taken one branch or another.
    for (int round = 0; round < 32; round++)
      for (BlockPos top : tops) {
        var state = world.getBlockState(top);
        if (state.isOf(net.minecraft.block.Blocks.STONE)
            || state.isOf(net.minecraft.block.Blocks.SAND)) BeamBlast.heat(world, top, 0, 10);
      }
    for (BlockPos top : tops)
      check(
          c,
          world.getBlockState(top.down()).isOf(net.minecraft.block.Blocks.BEDROCK),
          "Exothermic conversion replaced bedrock below " + top.toShortString());
    for (BlockPos top : tops) {
      world.setBlockState(top, net.minecraft.block.Blocks.AIR.getDefaultState(), 2);
      world.setBlockState(top.up(), net.minecraft.block.Blocks.AIR.getDefaultState(), 2);
    }
    c.complete();
  }
}

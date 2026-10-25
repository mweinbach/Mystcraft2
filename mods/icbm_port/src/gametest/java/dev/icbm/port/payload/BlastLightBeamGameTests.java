package dev.icbm.port.payload;

import static dev.icbm.port.payload.BlastTestSupport.*;

import dev.icbm.port.entity.LightBeamEntity;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * The exothermic/endothermic blasts drive a real {@link LightBeamEntity} through the {@link
 * BeamEffects} listener installed at mod init, as upstream {@code BlastBeam} drives {@code
 * EntityLightBeam}: spawn in the blast colour, step the target progress, then close.
 */
public final class BlastLightBeamGameTests implements FabricGameTest {
  private void runBeamBlast(TestContext c, PayloadType type, float[] expectedRgb) {
    BlockPos origin = c.getAbsolutePos(new BlockPos(2, 40, 2));
    Vec3d center = Vec3d.ofCenter(origin);
    List<Float> targets = new ArrayList<>();
    List<LightBeamEntity> seen = new ArrayList<>();
    BeamEffects.Listener installed = BeamEffects.listener();
    BeamEffects.setListener(
        (world, at, id, r, g, b, value, dying) -> {
          installed.update(world, at, id, r, g, b, value, dying);
          if (!at.equals(center)) return;
          for (LightBeamEntity beam : LightBeamEntity.near(world, center, 1)) {
            if (!beam.isBlastBeam(id)) continue;
            targets.add(beam.getTargetProgress());
            if (!seen.contains(beam)) seen.add(beam);
          }
        });
    try {
      var job = job(c, type, center, 3, null);
      check(c, run(c, job, 400), type + " blast must finish");
    } finally {
      BeamEffects.setListener(installed);
      clearDebris(c, center, 8);
    }
    check(c, seen.size() == 1, type + " must drive exactly one light beam, saw " + seen.size());
    LightBeamEntity beam = seen.get(0);
    check(
        c,
        targets.equals(List.of(.1f, .2f, .5f, .8f, 1f, 0f)),
        "Beam target must follow upstream's 0.1/0.2/0.5/0.8/1 steps, then 0: " + targets);
    float[] rgb = beam.colorComponents();
    check(
        c,
        Math.abs(rgb[0] - expectedRgb[0]) < .01
            && Math.abs(rgb[1] - expectedRgb[1]) < .01
            && Math.abs(rgb[2] - expectedRgb[2]) < .01
            && beam.getBeamSize() == 1
            && beam.getGlowSize() == 2,
        "Beam must use the blast colour and upstream sizes");
    check(c, beam.isClosing(), "A finished blast must start the beam's death cycle");
    c.runAtTick(
        5,
        () -> {
          check(c, beam.isRemoved(), "The closed beam must despawn once shut");
          c.complete();
        });
  }

  @GameTest(
      templateName = FabricGameTest.EMPTY_STRUCTURE,
      batchId = "icbmBlastLightBeam",
      tickLimit = 40)
  public void exothermicBlastOpensAndClosesOrangeBeam(TestContext c) {
    runBeamBlast(c, PayloadType.EXOTHERMIC, new float[] {.7f, .3f, 0});
  }

  @GameTest(
      templateName = FabricGameTest.EMPTY_STRUCTURE,
      batchId = "icbmBlastLightBeam",
      tickLimit = 40)
  public void endothermicBlastOpensAndClosesBlueBeam(TestContext c) {
    runBeamBlast(c, PayloadType.ENDOTHERMIC, new float[] {0, .3f, .7f});
  }
}

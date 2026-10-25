package dev.icbm.port.payload;

import static dev.icbm.port.payload.BlastTestSupport.*;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Review fix: the queue serves at most eight jobs per world tick, so with a busy queue each job is
 * served only intermittently. Every blast delay and lifetime must count world ticks
 * ({@code Job.elapsedTicks}), not service calls. These tests fill the queue with never-ending jobs
 * so each blast is served every other tick and check the delays in world ticks.
 */
public final class QueueContentionGameTests implements FabricGameTest {
  private static final int FILLERS = 14;

  /** A queued job that never finishes and does no work, to occupy queue slots. */
  private static Payloads.Job filler(Vec3d at) {
    var job = new Payloads.Job(PayloadType.CONDENSED, at, 0);
    job.work =
        new BlastWork() {
          @Override
          public boolean tick(ServerWorld world, Payloads.Job job, int budget) {
            return false;
          }

          @Override
          public NbtCompound write() {
            return new NbtCompound();
          }
        };
    return job;
  }

  private static Payloads.QueueState busyQueue(Vec3d at, int fillers) {
    var state = new Payloads.QueueState();
    for (int i = 0; i < fillers; i++) state.enqueue(filler(at), false);
    return state;
  }

  private static void clearEntities(TestContext c, Vec3d center, double range) {
    c.getWorld()
        .getOtherEntities(null, new Box(center, center).expand(range), e -> !(e instanceof PlayerEntity))
        .forEach(Entity::discard);
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "icbmQueueContentionBeam")
  public void beamDelayAndEnderLifetimeCountWorldTicksInABusyQueue(TestContext c) {
    Vec3d beamAt = Vec3d.ofCenter(c.getAbsolutePos(new BlockPos(1, 40, 1)));
    Vec3d enderAt = Vec3d.ofCenter(c.getAbsolutePos(new BlockPos(3, 40, 3)));
    var state = busyQueue(beamAt, FILLERS);
    var beam = job(c, PayloadType.ENDOTHERMIC, beamAt, 3, null);
    var ender = job(c, PayloadType.ENDER, enderAt, 3, null);
    state.enqueue(beam, false);
    state.enqueue(ender, false);
    int beamWaitDone = -1, enderDone = -1;
    try {
      for (int tick = 1; tick <= 400 && (beamWaitDone < 0 || enderDone < 0); tick++) {
        state.tick(c.getWorld());
        if (beamWaitDone < 0 && ((BeamBlast) beam.work).phase() >= 3) beamWaitDone = tick;
        if (enderDone < 0 && !state.jobs.contains(ender)) enderDone = tick;
      }
      check(
          c,
          beam.age < beam.elapsedTicks,
          "The busy queue must serve the beam less than once per tick: " + beam.age);
      check(
          c,
          beamWaitDone > BeamBlast.WAIT_TICKS && beamWaitDone <= BeamBlast.WAIT_TICKS + 16,
          "The beam's five-second wait must last 100 world ticks, not 100 services: "
              + beamWaitDone);
      check(
          c,
          enderDone > EnderBlast.DURATION && enderDone <= EnderBlast.DURATION + 4,
          "The ender blast must last 160 world ticks, not 160 services: " + enderDone);
    } finally {
      clearEntities(c, beamAt, 16);
      clearEntities(c, enderAt, 16);
      clearDebris(c, beamAt, 8);
    }
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "icbmQueueContentionSonic")
  public void sonicWaveCountsWorldTicksInABusyQueue(TestContext c) {
    BlockPos origin = c.getAbsolutePos(new BlockPos(2, 40, 2));
    Vec3d center = Vec3d.ofCenter(origin);
    c.getWorld().setBlockState(origin.down(), Blocks.STONE.getDefaultState(), 2);
    var state = busyQueue(center, FILLERS + 1);
    var sonic = job(c, PayloadType.SONIC, center, 10, null);
    state.enqueue(sonic, false);
    int done = -1;
    try {
      for (int tick = 1; tick <= 1200 && done < 0; tick++) {
        state.tick(c.getWorld());
        if (!state.jobs.contains(sonic)) done = tick;
      }
      int wave = ((SonicBlast) sonic.work).waveStart();
      int radius = (int) BlastSupport.blastRadius(10);
      check(c, done > 0 && wave >= 0, "The sonic blast must finish: " + done);
      check(
          c,
          done - wave <= radius + 4,
          "The sonic wave must last about radius world ticks, not radius services: "
              + (done - wave));
      check(c, c.getWorld().isAir(origin.down()), "The wave must still strip the stone");
    } finally {
      clearEntities(c, center, 16);
      clearDebris(c, center, 12);
    }
    c.complete();
  }
}

package dev.icbm.port.payload;

import static dev.icbm.port.payload.BlastTestSupport.*;

import dev.icbm.port.simulation.FlyingBlockEntity;
import dev.icbm.port.simulation.SimulationContent;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Parity with upstream {@code BlastAntiGravitational} and the {@code EntityFlyingBlock} features it
 * relies on (per-block gravity, spin, entity impact).
 */
public final class AntiGravityParityGameTests implements FabricGameTest {
  private static List<FlyingBlockEntity> flying(TestContext c, Vec3d center) {
    return c.getWorld()
        .getEntitiesByClass(FlyingBlockEntity.class, new Box(center, center).expand(12), e -> true);
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "icbmBlastAntigravity")
  public void antigravityLiftsOneBlockPerTickAndRestoresGravity(TestContext c) {
    // In open air so the rays reach only these three blocks.
    BlockPos origin = c.getAbsolutePos(new BlockPos(2, 40, 2));
    Vec3d center = Vec3d.ofCenter(origin);
    BlockPos floor = origin.down();
    // Dirt: a radius-3 pass, like upstream's, has too little energy to lift stone.
    for (BlockPos pos : List.of(floor, floor.east(), floor.west()))
      c.getWorld().setBlockState(pos, Blocks.DIRT.getDefaultState(), 2);
    var pig = still(c, EntityType.PIG, center.add(0, 0, 2));
    try {
      var job = job(c, PayloadType.ANTI_GRAVITATIONAL, center, 3, null);
      for (int tick = 0; tick < 4; tick++)
        check(c, !job.tick(c.getWorld(), 512), "Antigravity lasts its configured duration");
      var lifted = flying(c, center);
      check(c, lifted.size() == 3, "One block must be lifted per tick: " + lifted.size());
      check(
          c,
          lifted.stream().allMatch(b -> b.gravity() == 0 && b.getVelocity().y == 1),
          "Lifted blocks float without gravity, thrown up at one block per tick");
      check(
          c,
          pig.getVelocity().y > .4 && pig.getVelocity().y < .5,
          "Mobs rise by 0.15 per tick until moving upward at 0.4: " + pig.getVelocity().y);
      // The lifted block ids survive a reload, so a restored blast still lets them fall.
      var restored = new Payloads.Job(PayloadType.ANTI_GRAVITATIONAL, center, 3);
      restored.work = BlastWork.read(PayloadType.ANTI_GRAVITATIONAL, job.work.write(), 3);
      // Only world ticks count: a job served once (age 1) on a busy queue still expires on time.
      restored.elapsedTicks =
          PayloadConfig.forType(PayloadType.ANTI_GRAVITATIONAL).durationTicks() + 1;
      check(c, restored.tick(c.getWorld(), 512), "The blast must end after its duration");
      check(c, restored.age == 1, "The restored job was served once");
      check(
          c,
          lifted.stream().allMatch(b -> b.gravity() == FlyingBlockEntity.DEFAULT_GRAVITY),
          "Ending the blast must give every lifted block its gravity back");
    } finally {
      pig.discard();
      flying(c, center).forEach(Entity::discard);
      for (BlockPos pos : List.of(floor, floor.east(), floor.west()))
        c.getWorld().removeBlock(pos, false);
    }
    c.complete();
  }

  /**
   * A lifted block carries the rest of the field's lifetime, so it regains gravity on time by
   * itself: a long configured field can lift more blocks than the blast tracks, and a tracked block
   * may be unloaded when the field ends.
   */
  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "icbmBlastAntigravity")
  public void antigravityBlocksFallOnTheirOwnFieldTimer(TestContext c) {
    BlockPos origin = c.getAbsolutePos(new BlockPos(2, 60, 2));
    Vec3d center = Vec3d.ofCenter(origin);
    BlockPos floor = origin.down();
    c.getWorld().setBlockState(floor, Blocks.DIRT.getDefaultState(), 2);
    int duration = PayloadConfig.forType(PayloadType.ANTI_GRAVITATIONAL).durationTicks();
    try {
      var job = job(c, PayloadType.ANTI_GRAVITATIONAL, center, 3, null);
      // Served late in the field: three world ticks remain.
      for (int tick = 0; tick < 4 && flying(c, center).isEmpty(); tick++) {
        job.elapsedTicks = duration - 2;
        job.tick(c.getWorld(), 512);
      }
      var lifted = flying(c, center);
      check(c, lifted.size() == 1, "The dirt block must be lifted: " + lifted.size());
      FlyingBlockEntity block = lifted.get(0);
      check(
          c,
          block.gravity() == 0 && block.fieldTicks() == 3,
          "A lifted block carries the rest of the field: " + block.fieldTicks());
      NbtCompound saved = new NbtCompound();
      block.writeNbt(saved);
      var reloaded = new FlyingBlockEntity(SimulationContent.FLYING_BLOCK, c.getWorld());
      reloaded.readNbt(saved);
      check(c, reloaded.fieldTicks() == 3, "The field timer must survive a reload");
      // The blast is never served again (lost track of it); the block falls by itself.
      for (int tick = 0; tick < 2; tick++) block.tick();
      check(c, block.gravity() == 0, "The block floats until its field runs out");
      block.tick();
      check(
          c,
          block.gravity() == FlyingBlockEntity.DEFAULT_GRAVITY && block.fieldTicks() == 0,
          "The block must regain gravity when its field runs out");
    } finally {
      flying(c, center).forEach(Entity::discard);
      c.getWorld().removeBlock(floor, false);
    }
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "icbmFlyingBlock")
  public void flyingBlockGravityPersistsAndImpactsHurtMobs(TestContext c) {
    BlockPos pos = c.getAbsolutePos(new BlockPos(2, 30, 2));
    c.getWorld().setBlockState(pos, Blocks.STONE.getDefaultState(), 2);
    var block = FlyingBlockEntity.capture(c.getWorld(), pos, Vec3d.ZERO, 0);
    check(c, block != null, "Stone must be capturable");
    var pig = still(c, EntityType.PIG, block.getPos().add(.6, 0, 0));
    try {
      block.setGravity(0);
      block.setSpin(20, 40);
      NbtCompound saved = new NbtCompound();
      block.writeNbt(saved);
      var restored = new FlyingBlockEntity(SimulationContent.FLYING_BLOCK, c.getWorld());
      restored.readNbt(saved);
      check(c, restored.gravity() == 0, "Per-block gravity must survive a reload");
      restored.restoreGravity();
      check(
          c,
          restored.gravity() == FlyingBlockEntity.DEFAULT_GRAVITY,
          "Restoring gravity returns the default fall rate");
      // Wait out the launch grace without moving, then fly into the pig.
      for (int tick = 0; tick < FlyingBlockEntity.LAUNCH_GRACE; tick++) block.tick();
      check(c, !block.isRemoved() && block.getPos().y == pos.getY(), "Zero gravity must hover");
      float health = pig.getHealth();
      block.setVelocity(.5, 0, 0);
      block.tick();
      check(c, block.isRemoved(), "Hitting a mob must end the flying block");
      check(c, pig.getHealth() == health - 1, "Impact damage is ceil(speed * 2)");
      check(
          c,
          !c.getWorld()
              .getEntitiesByClass(
                  ItemEntity.class, new Box(pos).expand(3), e -> e.getStack().isOf(Items.STONE))
              .isEmpty(),
          "The block must drop as an item after hitting a mob");
      check(c, c.getWorld().isAir(pos), "An impacted block must not also be placed back");
    } finally {
      pig.discard();
      block.discard();
      clearDebris(c, Vec3d.ofCenter(pos), 4);
    }
    c.complete();
  }
}

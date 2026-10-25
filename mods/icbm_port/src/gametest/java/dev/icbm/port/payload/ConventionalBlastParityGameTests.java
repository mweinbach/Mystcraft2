package dev.icbm.port.payload;

import static dev.icbm.port.payload.BlastTestSupport.*;

import dev.icbm.port.IcbmPort;
import dev.icbm.port.entity.PrimedExplosiveEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.state.property.Properties;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Parity with upstream {@code BlastTNT} (condensed, repulsive, attractive), {@code BlastFire},
 * {@code BlastSonic} and {@code BlastBreach}. Each area blast runs in its own batch so its blocks
 * and entity effects cannot reach another test's structure.
 */
public final class ConventionalBlastParityGameTests implements FabricGameTest {
  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void tntRaysStartOnTheCubeSurface(TestContext c) {
    int n = TntBlast.RAYS_PER_AXIS, rays = 0;
    for (int i = 0; i < n * n * n; i++) {
      Vec3d ray = TntBlast.cubeRay(n, i);
      if (ray == null) continue;
      rays++;
      check(c, Math.abs(ray.length() - 1) < 1e-9, "Every ray direction must be a unit vector");
    }
    check(c, rays == n * n * n - (n - 2) * (n - 2) * (n - 2), "Only cube-surface rays may march");
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "icbmBlastCondensed")
  public void condensedBreaksWeakBlocksSparesObsidianAndHurtsMobs(TestContext c) {
    BlockPos origin = c.getAbsolutePos(new BlockPos(2, 4, 2));
    Vec3d center = Vec3d.ofCenter(origin);
    c.getWorld().setBlockState(origin.east(), Blocks.DIRT.getDefaultState(), 2);
    c.getWorld().setBlockState(origin.west(), Blocks.OBSIDIAN.getDefaultState(), 2);
    var pig = still(c, EntityType.PIG, center.add(0, 0, 2));
    float health = pig.getHealth();
    try {
      var job = job(c, PayloadType.CONDENSED, center, 3, null);
      check(c, run(c, job, 40), "Condensed blast must finish");
      check(c, c.getWorld().isAir(origin.east()), "Rays must break dirt next to the charge");
      check(
          c,
          c.getWorld().getBlockState(origin.west()).isOf(Blocks.OBSIDIAN),
          "Obsidian's blast resistance must stop the rays");
      check(c, !pig.isAlive() || pig.getHealth() < health, "Nearby mobs must take blast damage");
    } finally {
      pig.discard();
      c.getWorld().removeBlock(origin.west(), false);
      clearDebris(c, center, 6);
    }
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "icbmBlastPush")
  public void repulsivePushesAwayAndAttractivePullsIn(TestContext c) {
    Vec3d center = Vec3d.ofCenter(c.getAbsolutePos(new BlockPos(2, 4, 2)));
    var pig = still(c, EntityType.PIG, center.add(3, 0, 0));
    try {
      check(c, run(c, job(c, PayloadType.REPULSIVE, center, 2, null), 40), "Repulsive must end");
      check(c, pig.getVelocity().x > 1, "Repulsive charges throw mobs away from the centre");
      float health = pig.getHealth();
      pig.setVelocity(Vec3d.ZERO);
      pig.refreshPositionAndAngles(center.x + 3, center.y, center.z, 0, 0);
      check(c, run(c, job(c, PayloadType.ATTRACTIVE, center, 2, null), 40), "Attractive must end");
      check(c, pig.getVelocity().x < -.5, "Attractive charges pull mobs towards the centre");
      check(c, pig.getHealth() == health, "Push charges deal no blast damage, as upstream");
    } finally {
      pig.discard();
      clearDebris(c, center, 6);
    }
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "icbmBlastFire")
  public void incendiaryLightsFiresAndMeltsIce(TestContext c) {
    BlockPos origin = c.getAbsolutePos(new BlockPos(2, 3, 2));
    Vec3d center = Vec3d.ofCenter(origin);
    for (int x = -2; x <= 2; x++)
      for (int z = -2; z <= 2; z++)
        c.getWorld().setBlockState(origin.add(x, -1, z), Blocks.STONE.getDefaultState(), 2);
    c.getWorld().setBlockState(origin.up(), Blocks.ICE.getDefaultState(), 2);
    int fires = 0;
    try {
      check(c, run(c, job(c, PayloadType.INCENDIARY, center, 4, null), 40), "Fire blast must end");
      for (BlockPos pos : BlockPos.iterate(origin.add(-2, 0, -2), origin.add(2, 0, 2)))
        if (c.getWorld().getBlockState(pos).isOf(Blocks.FIRE)) fires++;
      check(c, fires > 0, "Incendiary rays must set the ground around the charge alight");
      check(c, c.getWorld().isAir(origin.up()), "Incendiary rays must melt nearby ice");
    } finally {
      for (BlockPos pos : BlockPos.iterate(origin.add(-5, -1, -5), origin.add(5, 4, 5)))
        c.getWorld().setBlockState(pos, Blocks.AIR.getDefaultState(), 2);
    }
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "icbmBlastSonic")
  public void sonicStripsBlocksPrimesExplosivesAndThrowsMobs(TestContext c) {
    // High above the structure so the radius-10 rays find only the blocks placed here.
    BlockPos origin = c.getAbsolutePos(new BlockPos(2, 40, 2));
    Vec3d center = Vec3d.ofCenter(origin);
    c.getWorld().setBlockState(origin.down(), Blocks.STONE.getDefaultState(), 2);
    var explosive = IcbmPort.EXPLOSIVE_BLOCKS.get(PayloadType.CONDENSED);
    c.getWorld().setBlockState(origin.north(2), explosive.getDefaultState(), 2);
    var pig = still(c, EntityType.PIG, center.add(2, 1, 0));
    try {
      var job = job(c, PayloadType.SONIC, center, 10, null);
      check(c, !serve(c, job), "Sonic blasts last several ticks");
      check(c, pig.getVelocity().y > 0, "The first pulse must throw nearby mobs upward");
      // Under a contended time budget the ray pass takes 512 units per call (about 160 calls at
      // radius 10); the wave's radius-call lifetime only starts after it.
      check(c, run(c, job, 400), "Sonic blast must end once collection and the wave finish");
      check(c, c.getWorld().isAir(origin.down()), "The widening pulse must strip stone");
      check(c, c.getWorld().isAir(origin.north(2)), "The pulse must remove the explosive block");
      var primed =
          c.getWorld()
              .getEntitiesByClass(
                  PrimedExplosiveEntity.class, new Box(origin).expand(4), e -> e.isAlive());
      check(c, primed.size() == 1, "Explosive blocks in the pulse must be primed, not erased");
      primed.forEach(PrimedExplosiveEntity::discard);
    } finally {
      pig.discard();
      clearDebris(c, center, 12);
    }
    c.complete();
  }

  /** Review fix: a ray pass that outlasts radius calls must still strip terrain afterwards. */
  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "icbmBlastSonicLate")
  public void sonicWaitsForSlowRayCollectionBeforeExpiring(TestContext c) {
    BlockPos origin = c.getAbsolutePos(new BlockPos(2, 40, 2));
    Vec3d center = Vec3d.ofCenter(origin);
    c.getWorld().setBlockState(origin.down(), Blocks.STONE.getDefaultState(), 2);
    try {
      var job = job(c, PayloadType.SONIC, center, 10, null);
      // As if collection had been starved for 100 world ticks, far past the radius lifetime.
      job.elapsedTicks = 100;
      check(c, !serve(c, job), "A late-collected sonic blast must not expire at once");
      check(c, run(c, job, 400), "Sonic blast must end once collection and the wave finish");
      check(c, c.getWorld().isAir(origin.down()), "A late-collected pulse must still strip stone");
    } finally {
      clearDebris(c, center, 12);
    }
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "icbmBlastBreach")
  public void breachingBoresSevenBlocksAlongItsDirection(TestContext c) {
    BlockPos origin = c.getAbsolutePos(new BlockPos(1, 4, 2));
    Vec3d center = Vec3d.ofCenter(origin);
    for (int i = 1; i <= 8; i++)
      c.getWorld()
          .setBlockState(
              origin.east(i),
              (i == 3 ? Blocks.OBSIDIAN : Blocks.STONE).getDefaultState(),
              2);
    c.getWorld().setBlockState(origin.up(), Blocks.STONE.getDefaultState(), 2);
    var carrier = new PrimedExplosiveEntity(IcbmPort.PRIMED_EXPLOSIVE, c.getWorld());
    carrier.setBlastDirection(Direction.EAST);
    try {
      check(c, BreachBlast.direction(null) == Direction.DOWN, "Down is the fallback direction");
      check(
          c,
          BreachBlast.direction(carrier) == Direction.EAST,
          "A primed charge must breach in its stored direction");
      var job = job(c, PayloadType.BREACHING, center, 0, carrier);
      job.suppressEntityDamage = true;
      check(c, run(c, job, 40), "Breaching blast must finish");
      for (int i = 1; i < BreachBlast.DEPTH; i++)
        check(c, c.getWorld().isAir(origin.east(i)), "The bore must clear block " + i);
      check(
          c,
          c.getWorld().getBlockState(origin.east(BreachBlast.DEPTH)).isOf(Blocks.STONE),
          "The bore must stop at its configured depth");
      check(
          c,
          c.getWorld().getBlockState(origin.up()).isOf(Blocks.STONE),
          "A breach must not spread sideways like a plain explosion");
    } finally {
      for (int i = 0; i <= 8; i++) c.getWorld().removeBlock(origin.east(i), false);
      c.getWorld().removeBlock(origin.up(), false);
      carrier.discard();
      clearDebris(c, center.add(4, 0, 0), 8);
    }
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void placedBreachingChargeAimsIntoItsSurface(TestContext c) {
    BlockPos pos = c.getAbsolutePos(new BlockPos(2, 2, 2));
    var block = IcbmPort.EXPLOSIVE_BLOCKS.get(PayloadType.BREACHING);
    // Placed against a wall's west face: it faces west and breaches east into the wall.
    BlockState state = block.getDefaultState().with(Properties.FACING, Direction.WEST);
    c.getWorld().setBlockState(pos, state, 2);
    check(c, block.prime(c.getWorld(), pos, 200), "Breaching charge must prime");
    var primed =
        c.getWorld()
            .getEntitiesByClass(PrimedExplosiveEntity.class, new Box(pos).expand(1), e -> true);
    try {
      check(c, primed.size() == 1, "Priming must spawn one charge");
      PrimedExplosiveEntity charge = primed.get(0);
      check(c, charge.getBlastDirection() == Direction.EAST, "Charge must aim into its surface");
      NbtCompound saved = new NbtCompound();
      charge.writeNbt(saved);
      var restored = new PrimedExplosiveEntity(IcbmPort.PRIMED_EXPLOSIVE, c.getWorld());
      restored.readNbt(saved);
      check(
          c,
          restored.getBlastDirection() == Direction.EAST,
          "The breach direction must survive a reload");
    } finally {
      primed.forEach(PrimedExplosiveEntity::discard);
      c.getWorld().removeBlock(pos, false);
    }
    c.complete();
  }
}

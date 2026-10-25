package dev.icbm.port.payload;

import static dev.icbm.port.payload.BlastTestSupport.*;

import dev.icbm.port.IcbmPort;
import dev.icbm.port.api.ActionSource;
import dev.icbm.port.block.EnderExplosiveBlock;
import dev.icbm.port.entity.PrimedExplosiveEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Parity with upstream {@code BlastEnder} with {@code EnderBlastCustomization}, {@code
 * ActionEmpArea} with its entity receivers, and the {@code BlastRot}/{@code BlastMutation} sizes.
 */
public final class UtilityBlastParityGameTests implements FabricGameTest {
  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "icbmBlastEnder")
  public void enderSendsNearbyMobsToItsTargetThenSpawnsEndermen(TestContext c) {
    Vec3d center = Vec3d.ofCenter(c.getAbsolutePos(new BlockPos(2, 3, 2)));
    Vec3d target = Vec3d.ofBottomCenter(c.getAbsolutePos(new BlockPos(2, 2, 8)));
    NbtCompound saved = new NbtCompound();
    saved.putString("targetWorld", c.getWorld().getRegistryKey().getValue().toString());
    saved.putDouble("tx", target.x);
    saved.putDouble("ty", target.y);
    saved.putDouble("tz", target.z);
    var near = still(c, EntityType.PIG, center.add(1, 0, 0));
    var far = still(c, EntityType.PIG, center.add(0, 0, -5));
    try {
      var job = new Payloads.Job(PayloadType.ENDER, center, 6);
      job.work = BlastWork.read(PayloadType.ENDER, saved, 6);
      check(c, !job.tick(c.getWorld(), 512), "Ender blasts last eight seconds");
      check(
          c,
          near.getPos().squaredDistanceTo(target) < .01,
          "Mobs within four blocks must be teleported to the target");
      check(
          c,
          far.getPos().squaredDistanceTo(target) > 1 && far.getVelocity().z > 0,
          "Mobs further out are only drawn towards the centre");
      job.age = EnderBlast.DURATION;
      check(c, job.tick(c.getWorld(), 512), "Ender blast must end after its duration");
      var endermen =
          c.getWorld()
              .getEntitiesByType(EntityType.ENDERMAN, new Box(center, center).expand(1), e -> true);
      check(c, endermen.size() == 8, "Eight endermen must appear when the blast ends");
      endermen.forEach(Entity::discard);
    } finally {
      near.discard();
      far.discard();
    }
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
  public void placedEnderChargeCarriesItsGpsTarget(TestContext c) {
    BlockPos pos = c.getAbsolutePos(new BlockPos(2, 2, 2));
    BlockPos destination = new BlockPos(100, 70, -40);
    Identifier dimension = new Identifier("minecraft", "the_nether");
    var block = IcbmPort.EXPLOSIVE_BLOCKS.get(PayloadType.ENDER);
    c.getWorld().setBlockState(pos, block.getDefaultState(), 2);
    check(
        c,
        c.getWorld().getBlockEntity(pos) instanceof EnderExplosiveBlock.Target,
        "Placed ender explosives keep a target");
    ((EnderExplosiveBlock.Target) c.getWorld().getBlockEntity(pos)).set(dimension, destination);
    check(c, block.prime(c.getWorld(), pos, 200), "Ender charge must prime");
    var primed =
        c.getWorld()
            .getEntitiesByClass(PrimedExplosiveEntity.class, new Box(pos).expand(1), e -> true);
    try {
      check(c, primed.size() == 1, "Priming must spawn one charge");
      NbtCompound nbt = new NbtCompound();
      primed.get(0).writeNbt(nbt);
      var restored = new PrimedExplosiveEntity(IcbmPort.PRIMED_EXPLOSIVE, c.getWorld());
      restored.readNbt(nbt);
      check(
          c,
          destination.equals(restored.getTargetPosition())
              && dimension.equals(restored.getTargetDimension()),
          "The primed charge must keep the GPS target across a reload");
      NbtCompound work =
          EnderBlast.create(c.getWorld(), Vec3d.ofCenter(pos), 20, restored).write();
      check(
          c,
          work.getDouble("tx") == 100.5
              && work.getDouble("ty") == 71
              && work.getDouble("tz") == -39.5
              && work.getString("targetWorld").equals(dimension.toString()),
          "The blast must aim on top of the recorded block in its dimension");
      NbtCompound legacy = new NbtCompound();
      legacy.putLong("destination", destination.asLong());
      legacy.putString("destinationWorld", dimension.toString());
      check(
          c,
          EnderBlast.read(legacy, 20).write().getDouble("ty") == 71,
          "Queued ender blasts saved before this port must keep their destination");
    } finally {
      primed.forEach(PrimedExplosiveEntity::discard);
      c.getWorld().removeBlock(pos, false);
    }
    c.complete();
  }

  // Own batch, and it outlives the 200-tick defence EMP field the registered handlers raise, so
  // no later launcher test inherits a disabled zone.
  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "icbmBlastEmp", tickLimit = 260)
  public void empChargesCreepersAndFizzlesPrimedExplosives(TestContext c) {
    Vec3d center = Vec3d.ofCenter(c.getAbsolutePos(new BlockPos(2, 3, 2)));
    CreeperEntity creeper = still(c, EntityType.CREEPER, center.add(2, 0, 0));
    var charge = new PrimedExplosiveEntity(IcbmPort.PRIMED_EXPLOSIVE, c.getWorld());
    charge.setPayload(PayloadType.CONDENSED);
    charge.setFuse(200);
    charge.setNoGravity(true);
    charge.setPosition(center.add(0, 0, 2));
    c.getWorld().spawnEntity(charge);
    try {
      check(
          c,
          EmpBlast.detonate(c.getWorld(), center, 4, null, ActionSource.of(null)),
          "EMP must be accepted");
      check(c, creeper.shouldRenderOverlay(), "An EMP must charge creepers like lightning");
      check(c, charge.isRemoved(), "An EMP must fizzle primed explosives without detonating");
      check(
          c,
          c.getWorld()
              .getEntitiesByType(EntityType.LIGHTNING_BOLT, new Box(center, center).expand(6), e -> true)
              .isEmpty(),
          "The charging strike must not spawn real lightning");
    } finally {
      creeper.discard();
      charge.discard();
    }
    c.runAtTick(
        210,
        () -> {
          check(
              c,
              !dev.icbm.port.defense.EmpPersistentState.get(c.getWorld())
                  .contains(center, c.getWorld().getTime()),
              "The EMP field must have expired before the batch ends");
          c.complete();
        });
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = "icbmBlastMutation")
  public void mutationUsesUpstreamThreeBlockCube(TestContext c) {
    check(
        c,
        PayloadConfig.forType(PayloadType.MUTATION).radius() == 3
            && PayloadConfig.forType(PayloadType.ROT).radius() == 3
            && PayloadConfig.forType(PayloadType.ENDER).radius() == 20,
        "Rot, mutation and ender default to upstream sizes");
    Vec3d center = Vec3d.ofCenter(c.getAbsolutePos(new BlockPos(2, 3, 2)));
    var inside = still(c, EntityType.PIG, center.add(2, 0, 2));
    var outside = still(c, EntityType.PIG, center.add(5, 0, 0));
    try {
      Payloads.detonate(c.getWorld(), center, PayloadType.MUTATION, null);
      check(c, inside.isRemoved(), "Pigs inside the cube must mutate");
      check(c, outside.isAlive(), "Pigs beyond three blocks must not");
      c.getWorld()
          .getEntitiesByType(EntityType.ZOMBIFIED_PIGLIN, new Box(center, center).expand(4), e -> true)
          .forEach(Entity::discard);
    } finally {
      inside.discard();
      outside.discard();
    }
    c.complete();
  }
}

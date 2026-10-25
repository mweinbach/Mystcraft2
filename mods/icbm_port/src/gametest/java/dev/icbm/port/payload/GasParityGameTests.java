package dev.icbm.port.payload;

import dev.icbm.port.exposure.ExposureContent;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Gas-cloud parity with upstream {@code BlastGasBase}: run cadence, radius schedule, one pathing
 * layer per radius change, cloud-local hit counts, protection, toxicity and expiry. Runs in its
 * own batch so the clouds never reach entities owned by other tests.
 */
public final class GasParityGameTests implements FabricGameTest {
  private static final String BATCH = "icbmGasParity";

  private static void check(TestContext c, boolean value, String message) {
    if (!value) c.throwGameTestException(message);
  }

  /** A cell in the empty test area with air on all sides (clouds here stay within radius 3). */
  private static BlockPos openCenter(TestContext c) {
    return c.getAbsolutePos(new BlockPos(2, 3, 2));
  }

  private static boolean run(
      TestContext c,
      GasWork gas,
      PayloadType type,
      BlockPos center,
      int radius,
      int duration,
      int from,
      int to) {
    boolean expired = false;
    for (int elapsed = from; elapsed <= to; elapsed++)
      expired =
          gas.tick(c.getWorld(), type, Vec3d.ofCenter(center), radius, duration, elapsed, 1024);
    return expired;
  }

  private static <T extends LivingEntity> T still(TestContext c, EntityType<T> type, BlockPos pos) {
    T entity = type.create(c.getWorld());
    if (entity instanceof net.minecraft.entity.mob.MobEntity mob) mob.setAiDisabled(true);
    entity.setNoGravity(true);
    entity.refreshPositionAndAngles(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 0, 0);
    c.getWorld().spawnEntity(entity);
    return entity;
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = BATCH)
  public void gasRadiusGrowsOneLayerPerUpstreamStep(TestContext c) {
    BlockPos center = openCenter(c);
    GasWork gas = new GasWork(center);
    // size 20, duration 600: the first run (t=1) has radius floor(20 * 0.1033) = 2.
    run(c, gas, PayloadType.COLOR, center, 20, 600, 1, 1);
    check(c, gas.currentRadius() == 2, "First run must use upstream radius 2");
    check(c, gas.contains(center) && gas.contains(center.east()), "First layer seeds neighbours");
    check(c, !gas.contains(center.east(2)), "Only one layer may be pathed per radius change");
    check(c, gas.cellCount() == 7, "First layer must hold the origin and six neighbours");
    run(c, gas, PayloadType.COLOR, center, 20, 600, 2, 15);
    check(c, !gas.contains(center.east(2)), "Runs at t=6 and t=11 keep radius 2 and path nothing");
    run(c, gas, PayloadType.COLOR, center, 20, 600, 16, 16);
    check(c, gas.currentRadius() == 3, "Run at t=16 must grow radius to 3");
    check(
        c,
        gas.contains(center.east(2)) && !gas.contains(center.east(3)),
        "A radius change paths exactly one more layer");
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = BATCH)
  public void gasHitsEveryFifthTickAndHonoursProtection(TestContext c) {
    BlockPos center = openCenter(c);
    var exposed = still(c, EntityType.PIG, center);
    var masked = still(c, EntityType.PIG, center.north());
    masked.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
    ExposureContent.config.armorRatings.put("minecraft:iron_helmet", 1f);
    try {
      GasWork gas = new GasWork(center);
      run(c, gas, PayloadType.DEBILITATION, center, 20, 600, 1, 11);
      check(c, gas.hitCount(exposed.getUuid()) == 3, "Runs at t=1, 6 and 11 must hit three times");
      check(
          c,
          exposed.hasStatusEffect(StatusEffects.SLOWNESS)
              && exposed.getStatusEffect(StatusEffects.SLOWNESS).getAmplifier() == 2,
          "Debilitation applies upstream slowness III");
      check(c, gas.contains(masked.getBlockPos()), "Masked target stands inside the cloud");
      check(
          c,
          gas.hitCount(masked.getUuid()) == 0 && !masked.hasStatusEffect(StatusEffects.POISON),
          "Full rated protection must block the gas");
    } finally {
      ExposureContent.config.armorRatings.remove("minecraft:iron_helmet");
      exposed.discard();
      masked.discard();
    }
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = BATCH)
  public void chemicalToxicityStartsAfterTwentyHits(TestContext c) {
    BlockPos center = openCenter(c);
    var cow = still(c, EntityType.COW, center);
    float full = cow.getHealth();
    ExposureContent.applyGas(cow, PayloadType.CHEMICAL, 20);
    check(c, cow.getHealth() == full, "Twenty chemical hits must not deal damage yet");
    check(
        c,
        cow.hasStatusEffect(StatusEffects.POISON) && cow.hasStatusEffect(StatusEffects.NAUSEA),
        "Chemical gas applies poison and nausea");
    ExposureContent.applyGas(cow, PayloadType.CHEMICAL, 21);
    check(c, Math.abs(full - 2.1f - cow.getHealth()) < .01f, "Hit 21 must deal 21 * 0.1 damage");
    check(
        c,
        cow.getRecentDamageSource() == ExposureContent.CHEMICAL_DAMAGE,
        "Chemical damage must use its own damage source");
    cow.discard();
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = BATCH)
  public void contagiousToxicityAndMutationFollowUpstream(TestContext c) {
    BlockPos center = openCenter(c);
    var cow = still(c, EntityType.COW, center);
    float full = cow.getHealth();
    ExposureContent.applyGas(cow, PayloadType.CONTAGIOUS, 10);
    check(c, cow.getHealth() == full, "Ten contagious hits must not deal damage yet");
    check(
        c,
        cow.getStatusEffect(StatusEffects.POISON).getAmplifier() == 3
            && cow.hasStatusEffect(StatusEffects.BLINDNESS),
        "Contagious gas applies poison IV and blindness");
    ExposureContent.applyGas(cow, PayloadType.CONTAGIOUS, 11);
    check(c, Math.abs(full - 1 - cow.getHealth()) < .01f, "Hit 11 must deal the minimum damage");
    check(
        c,
        cow.getRecentDamageSource() == ExposureContent.CONTAGIOUS_DAMAGE,
        "Contagious damage must use its own damage source");
    var pig = still(c, EntityType.PIG, center.north(2));
    ExposureContent.applyGas(pig, PayloadType.CONTAGIOUS, 30);
    check(c, pig.isRemoved(), "Contagious gas must mutate a pig instead of damaging it");
    var mutants =
        c.getWorld()
            .getEntitiesByType(
                EntityType.ZOMBIFIED_PIGLIN,
                new net.minecraft.util.math.Box(center.north(2)).expand(1),
                e -> true);
    check(c, mutants.size() == 1, "Mutation must leave exactly one zombified piglin");
    mutants.forEach(net.minecraft.entity.Entity::discard);
    cow.discard();
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = BATCH)
  public void colorCloudNeverAffectsEntitiesAndExpiresOnTime(TestContext c) {
    BlockPos center = openCenter(c);
    var pig = still(c, EntityType.PIG, center);
    GasWork gas = new GasWork(center);
    check(
        c,
        !run(c, gas, PayloadType.COLOR, center, 20, 20, 1, 20),
        "A 20-tick cloud must still be live at t=20");
    check(c, gas.contains(center), "Colors still paths a cloud");
    check(
        c,
        gas.hitCount(pig.getUuid()) == 0 && pig.getStatusEffects().isEmpty(),
        "Colors must never affect entities");
    check(
        c,
        run(c, gas, PayloadType.COLOR, center, 20, 20, 21, 21),
        "The run at t=21 exceeds the duration and ends the cloud");
    pig.discard();
    c.complete();
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = BATCH)
  public void gasCloudAndHitsSurviveReload(TestContext c) {
    BlockPos center = openCenter(c);
    var pig = still(c, EntityType.PIG, center);
    GasWork gas = new GasWork(center);
    run(c, gas, PayloadType.DEBILITATION, center, 20, 600, 1, 11);
    GasWork restored = GasWork.read(gas.write(), center);
    check(
        c,
        restored.cellCount() == gas.cellCount()
            && restored.currentRadius() == gas.currentRadius()
            && restored.hitCount(pig.getUuid()) == 3,
        "Cells, radius and cloud-local hits must survive reload");
    run(c, gas, PayloadType.DEBILITATION, center, 20, 600, 12, 30);
    run(c, restored, PayloadType.DEBILITATION, center, 20, 600, 12, 30);
    check(
        c,
        restored.cellCount() == gas.cellCount()
            && restored.contains(center.east(2))
            && !restored.contains(center.east(3)),
        "A reloaded cloud must keep pathing exactly like the original");
    check(
        c,
        restored.hitCount(pig.getUuid()) == 6 && gas.hitCount(pig.getUuid()) == 6,
        "A reloaded cloud must not repeat or skip runs");
    // A whole job, including its cloud, round-trips through the payload queue save format.
    var state = new Payloads.QueueState();
    state.enqueue(new Payloads.Job(PayloadType.COLOR, Vec3d.ofCenter(center), 20), false);
    for (int tick = 0; tick < 16; tick++) state.tick(c.getWorld());
    var job = state.jobs.get(0);
    var reloaded = Payloads.QueueState.read(state.writeNbt(new NbtCompound())).jobs.get(0);
    check(
        c,
        job.gasWork != null
            && reloaded.gasWork != null
            && reloaded.gasWork.cellCount() == job.gasWork.cellCount()
            && reloaded.elapsedTicks == 16,
        "Queued gas work must save its cloud with the job");
    pig.discard();
    c.complete();
  }
}

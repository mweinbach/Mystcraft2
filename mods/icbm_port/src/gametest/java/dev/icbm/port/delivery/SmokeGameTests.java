package dev.icbm.port.delivery;

import dev.icbm.port.IcbmPort;
import dev.icbm.port.entity.BlastEntities;
import dev.icbm.port.entity.PrimedExplosiveEntity;
import dev.icbm.port.entity.SmokeEntity;
import dev.icbm.port.payload.PayloadType;
import dev.icbm.port.payload.Payloads;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Smoke parity with upstream {@code EntitySmoke} spawned by {@code ActionSpawnEntity}: every
 * delivery form leaves one short-lived smoke entity instead of a queued field.
 */
public final class SmokeGameTests implements FabricGameTest {
  private static final String BATCH = "icbmSmoke";

  private static void check(TestContext c, boolean pass, String message) {
    if (!pass) c.throwGameTestException(message);
  }

  private static List<SmokeEntity> smokeNear(TestContext c, Vec3d pos) {
    return c.getWorld().getEntitiesByType(BlastEntities.SMOKE, new Box(pos, pos).expand(2), e -> true);
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 140)
  public void smokeSpawnsOneTimedEntityInsteadOfQueuedWork(TestContext c) {
    Vec3d center = Vec3d.ofCenter(c.getAbsolutePos(new BlockPos(2, 3, 2)));
    int queued = Payloads.queuedCount(c.getWorld());
    Payloads.detonate(c.getWorld(), center, PayloadType.SMOKE, null);
    var smoke = smokeNear(c, center);
    check(c, smoke.size() == 1, "Smoke must spawn exactly one smoke entity");
    check(c, Payloads.queuedCount(c.getWorld()) == queued, "Smoke must not queue payload work");
    SmokeEntity entity = smoke.get(0);
    check(
        c,
        entity.getLifetime() == SmokeEntity.DEFAULT_LIFETIME
            && entity.getPos().squaredDistanceTo(center) < 1e-9,
        "Smoke lives the upstream 100 ticks at the detonation point");
    c.runAtTick(
        50,
        () -> {
          check(c, entity.isAlive() && entity.getLife() >= 45, "Smoke must persist mid-life");
          NbtCompound saved = new NbtCompound();
          entity.writeNbt(saved);
          var restored = new SmokeEntity(BlastEntities.SMOKE, c.getWorld());
          restored.readNbt(saved);
          check(
              c,
              restored.getLifetime() == entity.getLifetime()
                  && restored.getLife() == entity.getLife(),
              "Smoke lifetime and age must survive reload");
        });
    c.runAtTick(
        115,
        () -> {
          check(c, entity.isRemoved(), "Smoke must remove itself after its lifetime");
          c.complete();
        });
  }

  @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, batchId = BATCH)
  public void smokeDeliveriesLeaveSmokeEntities(TestContext c) {
    Vec3d grenadeAt = Vec3d.ofCenter(c.getAbsolutePos(new BlockPos(0, 3, 0)));
    GrenadeEntity grenade = new GrenadeEntity(DeliveryContent.GRENADE, c.getWorld());
    grenade.setPayload(PayloadType.SMOKE);
    grenade.setFuse(1);
    grenade.setNoGravity(true);
    grenade.setPosition(grenadeAt);
    c.getWorld().spawnEntity(grenade);
    // Upstream timing: a fuse of N ticks detonates on tick N + 1.
    grenade.tick();
    grenade.tick();
    check(c, grenade.isRemoved(), "Grenade must detonate");
    check(c, smokeNear(c, grenadeAt).size() == 1, "Smoke grenade must leave one smoke entity");

    Vec3d primedAt = Vec3d.ofCenter(c.getAbsolutePos(new BlockPos(4, 3, 0)));
    var primed = new PrimedExplosiveEntity(IcbmPort.PRIMED_EXPLOSIVE, c.getWorld());
    primed.setPayload(PayloadType.SMOKE);
    primed.setFuse(1);
    primed.setNoGravity(true);
    primed.setPosition(primedAt);
    c.getWorld().spawnEntity(primed);
    primed.tick();
    primed.tick();
    check(c, primed.isRemoved(), "Primed smoke block must detonate");
    check(c, smokeNear(c, primedAt).size() == 1, "Smoke block must leave one smoke entity");

    Vec3d cartAt = Vec3d.ofCenter(c.getAbsolutePos(new BlockPos(2, 3, 4)));
    BombCartEntity cart = new BombCartEntity(DeliveryContent.BOMB_CART, c.getWorld());
    cart.setPayload(PayloadType.SMOKE);
    cart.setPosition(cartAt);
    cart.dropItems(DamageSource.IN_FIRE);
    check(c, cart.isRemoved(), "Burning smoke cart must detonate");
    check(c, smokeNear(c, cartAt).size() == 1, "Smoke cart must leave one smoke entity");

    for (Vec3d pos : List.of(grenadeAt, primedAt, cartAt))
      smokeNear(c, pos).forEach(SmokeEntity::discard);
    c.complete();
  }
}

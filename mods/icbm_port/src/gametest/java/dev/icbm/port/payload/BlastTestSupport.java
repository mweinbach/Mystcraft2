package dev.icbm.port.payload;

import dev.icbm.port.simulation.FlyingBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Drives staged blasts outside the live queue so assertions see deterministic progress. */
final class BlastTestSupport {
  private BlastTestSupport() {}

  static void check(TestContext c, boolean value, String message) {
    if (!value) c.throwGameTestException(message);
  }

  /** A queued job carrying the family's {@link BlastWork}, as {@code Payloads.detonate} makes. */
  static Payloads.Job job(TestContext c, PayloadType type, Vec3d center, int size, Entity source) {
    Payloads.Job job = new Payloads.Job(type, center, size);
    job.source = dev.icbm.port.api.ActionSource.of(source);
    job.work = BlastWork.create(c.getWorld(), center, type, size, source);
    return job;
  }

  /** Ticks the job like the queue does until it finishes; false if it outlives {@code maxTicks}. */
  static boolean run(TestContext c, Payloads.Job job, int maxTicks) {
    for (int tick = 0; tick < maxTicks; tick++) if (job.tick(c.getWorld(), 512)) return true;
    return false;
  }

  static <T extends MobEntity> T still(TestContext c, EntityType<T> type, Vec3d pos) {
    T mob = type.create(c.getWorld());
    mob.setAiDisabled(true);
    mob.setNoGravity(true);
    mob.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0, 0);
    c.getWorld().spawnEntity(mob);
    return mob;
  }

  /** Removes drops and loose flying blocks a blast left around {@code center}. */
  static void clearDebris(TestContext c, Vec3d center, double radius) {
    Box box = new Box(center, center).expand(radius);
    c.getWorld().getEntitiesByClass(ItemEntity.class, box, e -> true).forEach(Entity::discard);
    c.getWorld()
        .getEntitiesByClass(FlyingBlockEntity.class, box, e -> true)
        .forEach(Entity::discard);
  }
}

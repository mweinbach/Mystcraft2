package dev.icbm.port.flight;

import dev.icbm.port.core.GameFlightPath;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Persistent modular warmup/climb/arc/ascent/reentry stages adapted from upstream logic.
 *
 * <p>Mid-flight events follow one table (docs/DELIVERY.md, "Flight state machine"). The two rules
 * that change a flight live here: {@link #nextVelocity} reconciles riders every tick (an ASCENT
 * that now carries a rider virtual flight cannot take re-plans to the physical arc from where it
 * is), and {@link #configure} refuses to retarget a flight that is already under way.
 */
public final class StagedFlight {
  public enum Stage {
    DIRECT,
    WARMUP,
    CLIMB,
    LOCK_HEIGHT,
    ARC,
    ASCENT,
    DESCENT,
    COMPLETE
  }

  private Stage stage = Stage.DIRECT;
  private BlockPos target;
  private Vec3d start = Vec3d.ZERO;
  private int timer, elapsed, duration, generation;
  private int lockHeight = 3;

  public void setLockHeight(int height) {
    lockHeight = Math.max(3, Math.min(64, height));
  }

  public int lockHeight() {
    return lockHeight;
  }

  private double lift;

  public Stage stage() {
    return stage;
  }

  public BlockPos target() {
    return target;
  }

  public boolean targeted() {
    return target != null;
  }

  public boolean arrived() {
    return stage == Stage.COMPLETE;
  }

  public int generation() {
    return generation;
  }

  public void setGeneration(int generation) {
    this.generation = Math.max(0, generation);
  }

  public int stageTicks() {
    return elapsed;
  }

  public void clear() {
    target = null;
    stage = Stage.DIRECT;
  }

  /** True from the first climb tick until the flight ends: the missile has left the pad. */
  public boolean inFlight() {
    return switch (stage) {
      case CLIMB, LOCK_HEIGHT, ARC, ASCENT, DESCENT, COMPLETE -> true;
      default -> false;
    };
  }

  public void configure(World world, Vec3d origin, BlockPos destination) {
    // Retargeting a missile in the air is not an upstream action; re-planning from mid-air would
    // restart the warmup there. Only a missile still on the pad (or untargeted) may be configured.
    if (inFlight()) throw new IllegalArgumentException("Flight already under way");
    if (!finite(origin)
        || origin.distanceTo(Vec3d.ofCenter(destination)) > FlightSettings.current.maxRange
        || world.isOutOfHeightLimit(destination)
        || !world.getWorldBorder().contains(destination))
      throw new IllegalArgumentException("Target outside flight bounds");
    target = destination.toImmutable();
    start = origin;
    stage = Stage.WARMUP;
    timer = FlightProfile.WARMUP_TICKS;
    elapsed = 0;
    double flat =
        Math.hypot(destination.getX() + .5 - origin.x, destination.getZ() + .5 - origin.z);
    duration = Math.max(2, Math.min(10000, (int) Math.ceil(flat * 2)));
    lift = Math.min(1000, (flat > 100 ? 160 : 0) + flat);
  }

  /** The physical arc from {@code from} to the target, sized by the remaining flat distance. */
  private void planArc(Vec3d from) {
    stage = Stage.ARC;
    elapsed = 0;
    start = from;
    double flat = Math.hypot(target.getX() + .5 - from.x, target.getZ() + .5 - from.z);
    duration = Math.max(2, Math.min(10000, (int) Math.ceil(flat * 2)));
    lift = Math.min(1000, (flat > 100 ? 160 : 0) + flat);
  }

  public Vec3d nextVelocity(Entity entity) {
    if (target == null || stage == Stage.DIRECT) return entity.getVelocity();
    // Rider rule, checked every tick so every way of boarding (interaction, seats, nested riders,
    // reloads) is covered: ASCENT exists only to hand the missile to virtual flight, so once it
    // carries a rider capture cannot take (a player), it flies the physical arc from here instead
    // of climbing until its lifetime runs out. ARC stays valid either way; a capturable arc is
    // captured at the exit height.
    if (stage == Stage.ASCENT && !FlightLedger.capturable(entity)) planArc(entity.getPos());
    elapsed++;
    if (stage == Stage.WARMUP) {
      if (--timer <= 0) {
        stage = Stage.CLIMB;
        elapsed = 0;
      }
      return Vec3d.ZERO;
    }
    if (stage == Stage.CLIMB) {
      if (entity.getY() >= start.y + 2) {
        stage = Stage.LOCK_HEIGHT;
        elapsed = 0;
      } else return new Vec3d(0, Math.min(.2, .005 * elapsed), 0);
    }
    if (stage == Stage.LOCK_HEIGHT) {
      if (entity.getY() >= start.y + lockHeight) {
        double flat = Math.hypot(target.getX() + .5 - start.x, target.getZ() + .5 - start.z);
        // Upstream ArcFlightLogic: a missile carrying a player always flies the physical arc. Other
        // riders are carried through virtual flight (FlightLedger.capturable).
        if (flat >= FlightProfile.ARC_DISTANCE_LIMIT && FlightLedger.capturable(entity)) {
          stage = Stage.ASCENT;
          elapsed = 0;
          start = entity.getPos();
        } else planArc(entity.getPos());
      } else return new Vec3d(0, Math.min(2, .1 * Math.max(1, elapsed)), 0);
    }
    if (stage == Stage.ASCENT) return new Vec3d(0, FlightProfile.DIRECT_SPEED, 0);
    if (stage == Stage.ARC) {
      GameFlightPath.Point point =
          GameFlightPath.sample(
              new GameFlightPath.Point(start.x, start.y, start.z),
              new GameFlightPath.Point(target.getX() + .5, target.getY() + .5, target.getZ() + .5),
              lift,
              elapsed / (double) duration);
      if (elapsed >= duration) stage = Stage.COMPLETE;
      return new Vec3d(point.x(), point.y(), point.z()).subtract(entity.getPos());
    }
    if (stage == Stage.DESCENT) {
      Vec3d delta = Vec3d.ofCenter(target).subtract(entity.getPos());
      if (delta.length() <= FlightProfile.REENTRY_SPEED) {
        stage = Stage.COMPLETE;
        return delta;
      }
      return delta.normalize().multiply(FlightProfile.REENTRY_SPEED);
    }
    return Vec3d.ZERO;
  }

  public void prepareReentry(Vec3d position) {
    start = position;
    stage = Stage.DESCENT;
    elapsed = 0;
  }

  public NbtCompound writeNbt() {
    NbtCompound nbt = new NbtCompound();
    nbt.putString("Stage", stage.name());
    if (target != null) nbt.putLong("Target", target.asLong());
    nbt.putDouble("X", start.x);
    nbt.putDouble("Y", start.y);
    nbt.putDouble("Z", start.z);
    nbt.putInt("Timer", timer);
    nbt.putInt("Elapsed", elapsed);
    nbt.putInt("Duration", duration);
    nbt.putDouble("Lift", lift);
    nbt.putInt("Generation", generation);
    nbt.putInt("LockHeight", lockHeight);
    return nbt;
  }

  public void readNbt(World world, NbtCompound nbt) {
    clear();
    generation = Math.max(0, nbt.getInt("Generation"));
    setLockHeight(nbt.contains("LockHeight") ? nbt.getInt("LockHeight") : 3);
    Vec3d origin = new Vec3d(nbt.getDouble("X"), nbt.getDouble("Y"), nbt.getDouble("Z"));
    if (!nbt.contains("Target") || !finite(origin)) return;
    BlockPos destination = BlockPos.fromLong(nbt.getLong("Target"));
    if (world.isOutOfHeightLimit(destination)
        || !world.getWorldBorder().contains(destination)
        || origin.distanceTo(Vec3d.ofCenter(destination)) > FlightSettings.current.maxRange + 2048)
      return;
    try {
      stage = Stage.valueOf(nbt.getString("Stage"));
    } catch (IllegalArgumentException invalid) {
      return;
    }
    target = destination;
    start = origin;
    timer = Math.max(0, Math.min(40, nbt.getInt("Timer")));
    elapsed = Math.max(0, Math.min(12000, nbt.getInt("Elapsed")));
    duration = Math.max(2, Math.min(10000, nbt.getInt("Duration")));
    lift =
        Double.isFinite(nbt.getDouble("Lift"))
            ? Math.max(0, Math.min(1000, nbt.getDouble("Lift")))
            : 0;
  }

  private static boolean finite(Vec3d vector) {
    return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
  }
}

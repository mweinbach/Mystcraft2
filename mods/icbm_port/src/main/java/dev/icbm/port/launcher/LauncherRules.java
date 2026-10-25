package dev.icbm.port.launcher;

/**
 * Pure launch rules adapted from MIT ICBM-Classic {@code content/blocks/launcher/base/
 * LauncherCapability.java}, {@code content/blocks/launcher/cruise/TileCruiseLauncher.java} and
 * {@code config/machines/ConfigLauncher.java}. No world access, so they are unit-tested directly.
 *
 * <p>The maximum range is the launcher block's configured range (upstream {@code
 * ConfigLauncher.RANGE}, 10,000 by default; here {@code FlightSettings.maxRange}, or 4,000 for the
 * legacy one-missile launcher). Both range checks are per axis on X/Z, exactly as upstream.
 */
public final class LauncherRules {
  /** Upstream {@code LauncherCapability#isTargetTooClose}: 10 blocks on both X and Z. */
  public static final int MIN_RANGE = 10;

  /** Upstream {@code TileCruiseLauncher#isTooClose}: 20 blocks, straight-line from the center. */
  public static final int CRUISE_MIN_RANGE = 20;

  /** {@code ConfigLauncher} inaccuracy defaults (meters). */
  public static final double MIN_INACCURACY = 2,
      SCALED_INACCURACY_DISTANCE = 10,
      SCALED_INACCURACY_LAUNCHERS = 1;

  /** Upstream default and port bounds for the launcher's lock height (blocks climbed first). */
  public static final int DEFAULT_LOCK_HEIGHT = 3, MIN_LOCK_HEIGHT = 3, MAX_LOCK_HEIGHT = 64;

  /** Port bound for the firing delay (upstream accepts any int): one minute. */
  public static final int MAX_FIRING_DELAY = 1200;

  private LauncherRules() {}

  /** True when the target is within 10 blocks of the launcher center on both X and Z. */
  public static boolean tooClose(double launcherX, double launcherZ, double targetX, double targetZ) {
    return Math.abs(targetX - launcherX) < MIN_RANGE && Math.abs(targetZ - launcherZ) < MIN_RANGE;
  }

  /** True when the target is beyond {@code range} on X or on Z (a square, not a circle). */
  public static boolean tooFar(
      double launcherX, double launcherZ, double targetX, double targetZ, double range) {
    return Math.abs(targetX - launcherX) > range || Math.abs(targetZ - launcherZ) > range;
  }

  /** Cruise launchers refuse targets closer than 20 blocks in a straight line. */
  public static boolean cruiseTooClose(double distance) {
    return distance < CRUISE_MIN_RANGE;
  }

  /**
   * Upstream {@code LauncherCapability#calculateInaccuracy}: 2 m, plus 10 m scaled by squared
   * distance over squared range, plus 1 m per additional launcher fired together.
   */
  public static double inaccuracy(double distanceSq, double range, int launchers) {
    double inaccuracy = MIN_INACCURACY;
    if (range > 0) inaccuracy += distanceSq / (range * range) * SCALED_INACCURACY_DISTANCE;
    if (launchers > 1) inaccuracy += (launchers - 1) * SCALED_INACCURACY_LAUNCHERS;
    return inaccuracy;
  }

  /**
   * Upstream {@code LauncherCapability#applyInaccuracy}: offset by {@code inaccuracy * fraction}
   * along a horizontal direction of {@code yawDegrees}. Returns {dx, dz}.
   */
  public static double[] offset(double inaccuracy, double fraction, double yawDegrees) {
    double distance = inaccuracy * fraction;
    double yaw = Math.toRadians(yawDegrees);
    return new double[] {-Math.sin(yaw) * distance, Math.cos(yaw) * distance};
  }

  public static int clampLockHeight(int height) {
    return Math.max(MIN_LOCK_HEIGHT, Math.min(MAX_LOCK_HEIGHT, height));
  }

  public static int clampFiringDelay(int delay) {
    return Math.max(0, Math.min(MAX_FIRING_DELAY, delay));
  }
}

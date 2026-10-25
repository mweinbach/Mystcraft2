package dev.icbm.port.delivery;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;

/**
 * Pure ejection geometry for cluster bursts.
 *
 * <p>Adapted from MIT ICBM-Classic {@code content/cluster/action/ActionCluster.java} and {@code
 * lib/transform/RotationHelper.java}: droplets leave in discs of ten, each disc rotated 22.5
 * degrees from the previous one, aimed perpendicular to the carrier heading and tipped back by
 * 22.5 degrees. Each later disc is pushed further out and ejected faster so layers do not strike
 * the same spot. The upstream spawn list is consumed from the end, so entry {@code n - 1} uses the
 * first slot of the first disc.
 */
public final class ClusterBurst {
  public static final int PROJECTILES_PER_LAYER = 10;
  public static final double YAW_STEP = 360.0 / PROJECTILES_PER_LAYER;
  public static final double DISC_YAW_OFFSET = 22.5;
  public static final double MOTION_PITCH_OFFSET = -22.5;
  public static final double MOTION_SCALE = 0.3;
  public static final double MOTION_SCALE_LAYER = 0.1;
  public static final double OFFSET_SCALE = 0.25;
  public static final double STACK_SCALE = 0.1;

  /** One droplet: which manifest entry to spawn, its offset from the burst point and motion. */
  public record Ejection(
      int entry,
      double offsetX,
      double offsetY,
      double offsetZ,
      double motionX,
      double motionY,
      double motionZ) {}

  /** Upstream projectile yaw convention: {@code atan2(motionX, motionZ)} in degrees. */
  public static double headingYaw(double mx, double my, double mz) {
    return Math.toDegrees(Math.atan2(mx, mz));
  }

  /** Upstream projectile pitch convention: {@code atan2(motionY, horizontal)} in degrees. */
  public static double headingPitch(double mx, double my, double mz) {
    return Math.toDegrees(Math.atan2(my, Math.sqrt(mx * mx + mz * mz)));
  }

  /**
   * Computes the ejection list for {@code count} droplets. {@code random} supplies the uniform
   * [0, 1) floats that upstream draws in pairs ({@code nextFloat() - nextFloat()}) for each axis.
   */
  public static List<Ejection> plan(
      int count, double sourceYaw, double sourcePitch, DoubleSupplier random) {
    List<Ejection> out = new ArrayList<>(Math.max(0, count));
    // rotateX/rotateY turn +Z towards -X and +Y for positive angles, the opposite sense of the
    // heading angles from headingYaw/headingPitch (atan2(x, z), atan2(y, horizontal)). Negating
    // both keeps every disc perpendicular to the flight and stacks later discs back along it, for
    // diagonal and pitched headings as well as cardinal ones.
    double rotYaw = -sourceYaw, rotPitch = -sourcePitch;
    // Backward tilt is independent of the carrier heading upstream.
    double[] back = rotateX(0, 1, 0, MOTION_PITCH_OFFSET);
    double[] stack = rotateY(rotateX(0, 0, 1, rotPitch - 180), rotYaw);
    int remaining = count;
    int disc = 0;
    while (remaining > 0) {
      for (int slot = 0; slot < PROJECTILES_PER_LAYER && remaining > 0; slot++) {
        remaining--;
        double yaw = wrapDegrees(YAW_STEP * slot + disc * DISC_YAW_OFFSET);
        double[] offsetYaw = rotateY(new double[] {0, 0, 1}, yaw);
        double[] source = rotateY(rotateX(offsetYaw, rotPitch - 90), rotYaw);
        double dx = source[0] + back[0];
        double dy = source[1] + back[1];
        double dz = source[2] + back[2];
        double scale = MOTION_SCALE + MOTION_SCALE_LAYER * disc;
        double mx = dx * scale + (random.getAsDouble() - random.getAsDouble()) * MOTION_SCALE_LAYER;
        double my = dy * scale + (random.getAsDouble() - random.getAsDouble()) * MOTION_SCALE_LAYER;
        double mz = dz * scale + (random.getAsDouble() - random.getAsDouble()) * MOTION_SCALE_LAYER;
        out.add(
            new Ejection(
                remaining,
                source[0] * OFFSET_SCALE + stack[0] * STACK_SCALE * disc,
                source[1] * OFFSET_SCALE + stack[1] * STACK_SCALE * disc,
                source[2] * OFFSET_SCALE + stack[2] * STACK_SCALE * disc,
                mx,
                my,
                mz));
      }
      disc++;
    }
    return out;
  }

  static double wrapDegrees(double degrees) {
    double d = degrees % 360.0;
    if (d >= 180.0) d -= 360.0;
    if (d < -180.0) d += 360.0;
    return d;
  }

  private static double fixRounding(double value) {
    return Math.abs(value) <= 0.001 ? 0 : value;
  }

  static double[] rotateY(double[] v, double yaw) {
    double rad = Math.toRadians(wrapDegrees(yaw));
    return new double[] {
      fixRounding(v[0] * Math.cos(rad) - v[2] * Math.sin(rad)),
      v[1],
      fixRounding(v[0] * Math.sin(rad) + v[2] * Math.cos(rad))
    };
  }

  static double[] rotateX(double x, double y, double z, double pitch) {
    return rotateX(new double[] {x, y, z}, pitch);
  }

  static double[] rotateX(double[] v, double pitch) {
    double rad = Math.toRadians(wrapDegrees(pitch));
    return new double[] {
      v[0],
      fixRounding(v[1] * Math.cos(rad) - v[2] * Math.sin(rad)),
      fixRounding(v[1] * Math.sin(rad) + v[2] * Math.cos(rad))
    };
  }

  private ClusterBurst() {}
}

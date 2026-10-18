package dev.icbm.port.core;

/**
 * Adapted from ICBM Classic DirectFlightLogic (MIT, Built Broken Modding). Pure game-coordinate
 * math, independent of Minecraft or a particular loader.
 */
public final class DirectFlight {
  private DirectFlight() {}

  public record Velocity(double x, double y, double z) {}

  public static Velocity velocity(double dx, double dy, double dz, double speed) {
    if (!Double.isFinite(dx)
        || !Double.isFinite(dy)
        || !Double.isFinite(dz)
        || !Double.isFinite(speed)
        || speed <= 0) {
      throw new IllegalArgumentException("Flight direction must be finite and speed positive");
    }
    double length = Math.hypot(Math.hypot(dx, dy), dz);
    if (length == 0 || !Double.isFinite(length)) {
      throw new IllegalArgumentException("Flight direction must have a finite nonzero length");
    }
    return new Velocity(dx / length * speed, dy / length * speed, dz / length * speed);
  }
}

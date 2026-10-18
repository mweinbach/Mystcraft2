package dev.icbm.port.core;

/** A bounded animation arc in Minecraft block coordinates, not an upstream flight-logic port. */
public final class GameFlightPath {
  private GameFlightPath() {}

  public record Point(double x, double y, double z) {}

  public static Point sample(Point start, Point end, double lift, double progress) {
    if (!finite(start)
        || !finite(end)
        || !Double.isFinite(lift)
        || lift < 0
        || !Double.isFinite(progress))
      throw new IllegalArgumentException(
          "Flight path requires finite game coordinates and a nonnegative lift");
    double t = Math.max(0, Math.min(1, progress));
    return new Point(
        start.x + (end.x - start.x) * t,
        start.y + (end.y - start.y) * t + 4 * lift * t * (1 - t),
        start.z + (end.z - start.z) * t);
  }

  private static boolean finite(Point point) {
    return point != null
        && Double.isFinite(point.x)
        && Double.isFinite(point.y)
        && Double.isFinite(point.z);
  }
}

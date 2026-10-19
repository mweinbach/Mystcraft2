package ports.jurassicraft.tourrail;

/** Collision-safe height along the line between two adjacent rail centers. */
public final class TourRailPath {
    private TourRailPath() {}

    public static double height(double from, double to, double progress) {
        double t = Math.max(0, Math.min(1, progress));
        // A tour Explorer is 0.75 blocks wide. Lift it before its front reaches
        // the uphill rail's solid support; lower it after its rear clears it.
        double vertical = to > from ? Math.min(1, t * 8)
                : to < from ? Math.max(0, t * 8 - 7) : t;
        return from + (to - from) * vertical;
    }
}

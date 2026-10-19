package ports.jurassicraft.tourrail;

/** Chooses the original straight, curve, diagonal and transition shapes from neighbors. */
public final class TourRailLayout {
    public static final int N=1, S=2, E=4, W=8, NE=16, NW=32, SE=64, SW=128;
    private TourRailLayout() {}
    public static TourRailShape choose(int mask, int rising, TourRailShape fallback) {
        boolean n=(mask&N)!=0, s=(mask&S)!=0, e=(mask&E)!=0, w=(mask&W)!=0;
        boolean ne=(mask&NE)!=0, nw=(mask&NW)!=0, se=(mask&SE)!=0, sw=(mask&SW)!=0;
        TourRailShape result = null;
        if (n && s && !(e && w)) result = TourRailShape.NORTH_SOUTH;
        else if (e && w && !(n && s)) result = TourRailShape.EAST_WEST;
        else if (n && e && !s && !w) result = TourRailShape.NORTH_EAST;
        else if (n && w && !s && !e) result = TourRailShape.NORTH_WEST;
        else if (s && e && !n && !w) result = TourRailShape.SOUTH_EAST;
        else if (s && w && !n && !e) result = TourRailShape.SOUTH_WEST;
        else if (n && !s && !e && !w) result = se ? TourRailShape.VERTICAL_SE : sw ? TourRailShape.VERTICAL_SW : TourRailShape.NORTH_SOUTH;
        else if (s && !n && !e && !w) result = ne ? TourRailShape.VERTICAL_NE : nw ? TourRailShape.VERTICAL_NW : TourRailShape.NORTH_SOUTH;
        else if (e && !n && !s && !w) result = nw ? TourRailShape.HORIZONTAL_NW : sw ? TourRailShape.HORIZONTAL_SW : TourRailShape.EAST_WEST;
        else if (w && !n && !s && !e) result = ne ? TourRailShape.HORIZONTAL_NE : se ? TourRailShape.HORIZONTAL_SE : TourRailShape.EAST_WEST;
        else if (!n && !s && !e && !w) {
            if (ne || sw) result = TourRailShape.DIAGONAL_NE_SW;
            else if (nw || se) result = TourRailShape.DIAGONAL_NW_SE;
        }
        if (result == null) {
            if (fallback == TourRailShape.ASCENDING_NORTH || fallback == TourRailShape.ASCENDING_SOUTH) result = TourRailShape.NORTH_SOUTH;
            else if (fallback == TourRailShape.ASCENDING_EAST || fallback == TourRailShape.ASCENDING_WEST) result = TourRailShape.EAST_WEST;
            else result = fallback;
        }
        if (result == TourRailShape.NORTH_SOUTH) {
            if ((rising & N) != 0) return TourRailShape.ASCENDING_NORTH;
            if ((rising & S) != 0) return TourRailShape.ASCENDING_SOUTH;
        } else if (result == TourRailShape.EAST_WEST) {
            if ((rising & E) != 0) return TourRailShape.ASCENDING_EAST;
            if ((rising & W) != 0) return TourRailShape.ASCENDING_WEST;
        }
        return result;
    }
}

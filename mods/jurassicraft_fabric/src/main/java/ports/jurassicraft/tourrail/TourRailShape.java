package ports.jurassicraft.tourrail;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/** Upstream's twenty track shapes, represented by the two adjacent track centers. */
public enum TourRailShape implements StringRepresentable {
    NORTH_SOUTH(0,0,-1, 0,0,1), EAST_WEST(-1,0,0, 1,0,0),
    ASCENDING_EAST(-1,0,0, 1,1,0), ASCENDING_WEST(-1,1,0, 1,0,0),
    ASCENDING_NORTH(0,1,-1, 0,0,1), ASCENDING_SOUTH(0,0,-1, 0,1,1),
    SOUTH_EAST(0,0,1, 1,0,0), SOUTH_WEST(0,0,1, -1,0,0),
    NORTH_WEST(0,0,-1, -1,0,0), NORTH_EAST(0,0,-1, 1,0,0),
    DIAGONAL_NE_SW(1,0,-1, -1,0,1), DIAGONAL_NW_SE(-1,0,-1, 1,0,1),
    HORIZONTAL_NE(-1,0,0, 1,0,-1), HORIZONTAL_NW(1,0,0, -1,0,-1),
    HORIZONTAL_SE(-1,0,0, 1,0,1), HORIZONTAL_SW(1,0,0, -1,0,1),
    VERTICAL_NE(0,0,1, 1,0,-1), VERTICAL_NW(0,0,1, -1,0,-1),
    VERTICAL_SE(0,0,-1, 1,0,1), VERTICAL_SW(0,0,-1, -1,0,1);

    public record End(int x, int rise, int z) {}
    private final End first, second;
    TourRailShape(int x1, int y1, int z1, int x2, int y2, int z2) {
        first = new End(x1,y1,z1); second = new End(x2,y2,z2);
    }
    public End first() { return first; }
    public End second() { return second; }
    public boolean ascending() { return first.rise() != 0 || second.rise() != 0; }
    @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    private static End rotate(End end, int turns, boolean mirrorX, boolean mirrorZ) {
        int x = mirrorX ? -end.x() : end.x(), z = mirrorZ ? -end.z() : end.z();
        for (int i = 0; i < turns; i++) { int nextX = -z; z = x; x = nextX; }
        return new End(x, end.rise(), z);
    }
    private TourRailShape transformed(int turns, boolean mirrorX, boolean mirrorZ) {
        End a = rotate(first, turns, mirrorX, mirrorZ), b = rotate(second, turns, mirrorX, mirrorZ);
        for (TourRailShape shape : values()) {
            if ((shape.first.equals(a) && shape.second.equals(b)) || (shape.first.equals(b) && shape.second.equals(a))) return shape;
        }
        throw new IllegalStateException("Missing rotated tour rail shape " + this);
    }
    public TourRailShape rotate(Rotation rotation) {
        return transformed(switch (rotation) { case NONE -> 0; case CLOCKWISE_90 -> 1; case CLOCKWISE_180 -> 2; case COUNTERCLOCKWISE_90 -> 3; }, false, false);
    }
    public TourRailShape mirror(Mirror mirror) {
        return transformed(0, mirror == Mirror.FRONT_BACK, mirror == Mirror.LEFT_RIGHT);
    }
}

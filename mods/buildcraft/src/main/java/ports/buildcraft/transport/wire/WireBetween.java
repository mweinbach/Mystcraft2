package ports.buildcraft.transport.wire;

import java.util.Arrays;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/**
 * Upstream {@code EnumWireBetween}: the lengths of wire drawn between two corners, either along an edge of
 * the pipe's centre or across a face to the next pipe.
 */
public enum WireBetween {
    X_UP_SOUTH(Direction.Axis.X, true, true),
    X_UP_NORTH(Direction.Axis.X, true, false),
    X_DOWN_SOUTH(Direction.Axis.X, false, true),
    X_DOWN_NORTH(Direction.Axis.X, false, false),

    Y_SOUTH_EAST(Direction.Axis.Y, true, true),
    Y_SOUTH_WEST(Direction.Axis.Y, true, false),
    Y_NORTH_EAST(Direction.Axis.Y, false, true),
    Y_NORTH_WEST(Direction.Axis.Y, false, false),

    Z_UP_EAST(Direction.Axis.Z, true, true),
    Z_UP_WEST(Direction.Axis.Z, true, false),
    Z_DOWN_EAST(Direction.Axis.Z, false, true),
    Z_DOWN_WEST(Direction.Axis.Z, false, false),

    EAST_UP_SOUTH(Direction.EAST, true, true),
    EAST_UP_NORTH(Direction.EAST, true, false),
    EAST_DOWN_SOUTH(Direction.EAST, false, true),
    EAST_DOWN_NORTH(Direction.EAST, false, false),

    WEST_UP_SOUTH(Direction.WEST, true, true),
    WEST_UP_NORTH(Direction.WEST, true, false),
    WEST_DOWN_SOUTH(Direction.WEST, false, true),
    WEST_DOWN_NORTH(Direction.WEST, false, false),

    UP_SOUTH_EAST(Direction.UP, true, true),
    UP_SOUTH_WEST(Direction.UP, true, false),
    UP_NORTH_EAST(Direction.UP, false, true),
    UP_NORTH_WEST(Direction.UP, false, false),

    DOWN_SOUTH_EAST(Direction.DOWN, true, true),
    DOWN_SOUTH_WEST(Direction.DOWN, true, false),
    DOWN_NORTH_EAST(Direction.DOWN, false, true),
    DOWN_NORTH_WEST(Direction.DOWN, false, false),

    SOUTH_UP_EAST(Direction.SOUTH, true, true),
    SOUTH_UP_WEST(Direction.SOUTH, true, false),
    SOUTH_DOWN_EAST(Direction.SOUTH, false, true),
    SOUTH_DOWN_WEST(Direction.SOUTH, false, false),

    NORTH_UP_EAST(Direction.NORTH, true, true),
    NORTH_UP_WEST(Direction.NORTH, true, false),
    NORTH_DOWN_EAST(Direction.NORTH, false, true),
    NORTH_DOWN_WEST(Direction.NORTH, false, false);

    public static final WireBetween[] VALUES = values();
    public static final WireBetween[] CENTRES = Arrays.copyOfRange(VALUES, 0, 12);
    public static final WireBetween[] CONNECTIONS = Arrays.copyOfRange(VALUES, 12, 36);

    public final Direction.Axis mainAxis;
    /** The face the wire crosses to the next pipe, or null for a length along the centre. */
    @Nullable
    public final Direction to;
    public final boolean xy;
    public final boolean yz;
    public final AABB boundingBox;
    /** The corners at each end; for a crossing, the first is this pipe's. */
    public final WirePart[] parts;

    WireBetween(Direction.Axis mainAxis, boolean xy, boolean yz) {
        this(mainAxis, null, xy, yz, 4, 8);
    }

    WireBetween(Direction to, boolean xy, boolean yz) {
        this(to.getAxis(), to, xy, yz, to.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 13 : 0, 3);
    }

    WireBetween(Direction.Axis mainAxis, @Nullable Direction to, boolean xy, boolean yz, int start, int length) {
        this.mainAxis = mainAxis;
        this.to = to;
        this.xy = xy;
        this.yz = yz;
        int x1 = mainAxis == Direction.Axis.X ? start : (xy ? 12 : 3);
        int y1 = mainAxis == Direction.Axis.Y ? start : ((mainAxis == Direction.Axis.X ? xy : yz) ? 12 : 3);
        int z1 = mainAxis == Direction.Axis.Z ? start : (yz ? 12 : 3);
        int x2 = x1 + (mainAxis == Direction.Axis.X ? length : 1);
        int y2 = y1 + (mainAxis == Direction.Axis.Y ? length : 1);
        int z2 = z1 + (mainAxis == Direction.Axis.Z ? length : 1);
        boundingBox = new AABB(x1 / 16.0, y1 / 16.0, z1 / 16.0, x2 / 16.0, y2 / 16.0, z2 / 16.0);
        parts = new WirePart[] {part(0), part(1)};
    }

    private WirePart part(int end) {
        boolean[] positive = new boolean[3];
        boolean found = false;
        for (Direction.Axis axis : Direction.Axis.values()) {
            int j = axis.ordinal();
            if (axis == mainAxis) {
                positive[j] = to == null ? end == 1 : (end == 0 ? to : to.getOpposite()).getAxisDirection() == Direction.AxisDirection.POSITIVE;
            } else if (!found) {
                positive[j] = xy;
                found = true;
            } else {
                positive[j] = yz;
            }
        }
        return WirePart.get(positive[0], positive[1], positive[2]);
    }
}

package ports.buildcraft.transport.wire;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/** Upstream {@code EnumWirePart}: the eight corners of a pipe a wire can run along, named by the faces they touch. */
public enum WirePart {
    EAST_UP_SOUTH(true, true, true),
    EAST_UP_NORTH(true, true, false),
    EAST_DOWN_SOUTH(true, false, true),
    EAST_DOWN_NORTH(true, false, false),
    WEST_UP_SOUTH(false, true, true),
    WEST_UP_NORTH(false, true, false),
    WEST_DOWN_SOUTH(false, false, true),
    WEST_DOWN_NORTH(false, false, false);

    public static final WirePart[] VALUES = values();

    public final Direction.AxisDirection x, y, z;
    /** The box drawn for a placed wire, which also selects it. */
    public final AABB boundingBox;

    WirePart(boolean x, boolean y, boolean z) {
        this.x = x ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE;
        this.y = y ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE;
        this.z = z ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE;
        double x1 = this.x.getStep() * (5 / 16.0) + 0.5;
        double y1 = this.y.getStep() * (5 / 16.0) + 0.5;
        double z1 = this.z.getStep() * (5 / 16.0) + 0.5;
        double x2 = this.x.getStep() * (4 / 16.0) + 0.5;
        double y2 = this.y.getStep() * (4 / 16.0) + 0.5;
        double z2 = this.z.getStep() * (4 / 16.0) + 0.5;
        boundingBox = new AABB(x1, y1, z1, x2, y2, z2);
    }

    public Direction.AxisDirection getDirection(Direction.Axis axis) {
        return switch (axis) {
            case X -> x;
            case Y -> y;
            case Z -> z;
        };
    }

    /** The corner of a two-by-two grid of corners, where odd coordinates are the positive side. */
    public static WirePart get(int x, int y, int z) {
        return get(Math.floorMod(x, 2) == 1, Math.floorMod(y, 2) == 1, Math.floorMod(z, 2) == 1);
    }

    public static WirePart get(boolean x, boolean y, boolean z) {
        if (x) {
            if (y) return z ? EAST_UP_SOUTH : EAST_UP_NORTH;
            return z ? EAST_DOWN_SOUTH : EAST_DOWN_NORTH;
        }
        if (y) return z ? WEST_UP_SOUTH : WEST_UP_NORTH;
        return z ? WEST_DOWN_SOUTH : WEST_DOWN_NORTH;
    }
}

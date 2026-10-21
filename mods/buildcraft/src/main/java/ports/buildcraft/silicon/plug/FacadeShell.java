package ports.buildcraft.silicon.plug;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The copied-block quads of a facade, in block space, shared by {@code FacadeRenderer} and the shell test.
 * Local Z is depth measured from the face being sampled. The outer face is sampled from the facade's own
 * side and the inner face from the opposite side, and sampling the opposite side flips Z. An inner local Z
 * of {@code 16 - SIZE} is therefore {@code SIZE} pixels in from the outer face, on the back of the slab.
 */
public final class FacadeShell {
    private FacadeShell() {}

    /** The two-pixel slab {@link PluggableFacade} occupies on a face. */
    public static AABB box(Direction side) {
        boolean positive = side.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        double start = positive ? (16 - PluggableFacade.SIZE) / 16.0 : 0;
        double end = positive ? 1 : PluggableFacade.SIZE / 16.0;
        return switch (side.getAxis()) {
            case X -> new AABB(start, 0, 0, end, 1, 1);
            case Y -> new AABB(0, start, 0, 1, end, 1);
            case Z -> new AABB(0, 0, start, 1, 1, end);
        };
    }

    /** Every copied-block quad, full or hollow, before textures are applied. */
    public static List<Quad> quads(Direction side, boolean hollow) {
        int size = PluggableFacade.SIZE;
        int inner = 16 - size;
        List<Quad> quads = new ArrayList<>();
        if (!hollow) {
            quads.add(quad(side, v(0, 16, 0), v(16, 16, 0), v(16, 0, 0), v(0, 0, 0)));
            quads.add(quad(side.getOpposite(), v(size, inner, inner), v(inner, inner, inner), v(inner, size, inner), v(size, size, inner)));
        }
        for (Rotation rotation : Rotation.values()) {
            if (hollow) {
                boolean even = rotation.ordinal() % 2 == 0;
                quads.add(rotated(side, rotation,
                        v(0, even ? 4 : 0, 0), v(4, even ? 4 : 0, 0), v(4, even ? 16 : 12, 0), v(0, even ? 16 : 12, 0)));
            }
            quads.add(rotated(side.getOpposite(), rotation,
                    v(0, 16, 16), v(size, inner, inner), v(size, size, inner), v(0, 0, 16)));
            if (hollow) {
                boolean even = rotation.ordinal() % 2 == 0;
                quads.add(rotated(side.getOpposite(), rotation,
                        v(size, even ? inner : 12, inner), v(4, even ? inner : 12, inner),
                        v(4, even ? 4 : size, inner), v(size, even ? 4 : size, inner)));
            }
        }
        if (hollow) {
            boolean positive = side.getAxisDirection() == Direction.AxisDirection.POSITIVE;
            for (Direction facing : Direction.values()) {
                if (facing.getAxis() == side.getAxis()) continue;
                boolean across = side.getAxis() == Direction.Axis.Z && facing.getAxis() == Direction.Axis.X
                        || side.getAxis() == Direction.Axis.X && facing.getAxis() == Direction.Axis.Y
                        || side.getAxis() == Direction.Axis.Y && facing.getAxis() == Direction.Axis.Z;
                if (across) {
                    quads.add(quad(facing,
                            v(positive ? 16 : size, 4, 12.003), v(positive ? 16 : size, 12, 12.003),
                            v(positive ? inner : 0, 12, 12.003), v(positive ? inner : 0, 4, 12.003)));
                } else {
                    quads.add(quad(facing,
                            v(4, positive ? 16 : size, 12.003), v(4, positive ? inner : 0, 12.003),
                            v(12, positive ? inner : 0, 12.003), v(12, positive ? 16 : size, 12.003)));
                }
            }
        }
        return quads;
    }

    private static Quad rotated(Direction face, Rotation rotation, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3) {
        return quad(face, rotate(p0, rotation), rotate(p1, rotation), rotate(p2, rotation), rotate(p3, rotation));
    }

    private static Quad quad(Direction face, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3) {
        return new Quad(face, p0, p1, p2, p3);
    }

    /** Upstream {@code PlugBakerFacade.rotate}, including its swapped ninety-degree cases. */
    private static Vec3 rotate(Vec3 vec, Rotation rotation) {
        return switch (rotation) {
            case NONE -> vec;
            case CLOCKWISE_90 -> new Vec3(1 - vec.y, 1 - vec.x, vec.z);
            case CLOCKWISE_180 -> new Vec3(1 - vec.x, 1 - vec.y, vec.z);
            case COUNTERCLOCKWISE_90 -> new Vec3(vec.y, vec.x, vec.z);
        };
    }

    /** Upstream {@code PlugBakerFacade} position transform for the face a quad is sampled from. */
    public static Vec3 map(Vec3 pos, Direction side) {
        boolean positive = side.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        return switch (side.getAxis()) {
            case X -> new Vec3(positive ? 1 - pos.z : pos.z, pos.y, pos.x);
            case Y -> new Vec3(pos.x, positive ? 1 - pos.z : pos.z, pos.y);
            case Z -> new Vec3(pos.y, pos.x, positive ? 1 - pos.z : pos.z);
        };
    }

    private static Vec3 v(double x, double y, double z) {
        return new Vec3(x / 16.0, y / 16.0, z / 16.0);
    }

    /** One copied-block quad after it has been moved onto the facade. */
    public record Quad(Direction face, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3) {
        public Vec3[] corners() {
            return new Vec3[] {p0, p1, p2, p3};
        }
    }
}

package ports.buildcraft.builders.filler;

import java.util.BitSet;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Upstream {@code PatternSphere} and {@code PatternSpherePart}: an ellipsoid filling the box, or a half,
 * quarter or eighth of one whose centre sits on the box's flat faces. Hollow spheres keep the first
 * solid block seen along each row from every open side; surrounded ones also fill the rows up to it.
 */
class PatternSphere extends Pattern {
    PatternSphere() {
        super("sphere");
    }

    PatternSphere(String tag) {
        super(tag);
    }

    @Override public int minParameters() { return 1; }
    @Override public int maxParameters() { return 1; }

    @Nullable
    @Override
    public FillerParameter createParameter(int index) {
        return index == 0 ? PatternParameterHollow.FILLED_INNER : null;
    }

    @Override
    protected boolean fillTemplate(FilledTemplate template, FillerParameter[] params) {
        PatternParameterHollow hollow = getParam(0, params, PatternParameterHollow.FILLED_INNER);
        double cx = template.getMax().getX() / 2.0;
        double cy = template.getMax().getY() / 2.0;
        double cz = template.getMax().getZ() / 2.0;
        fill(template, hollow, new Vec3(cx, cy, cz), new Vec3(cx + 0.5, cy + 0.5, cz + 0.5), EnumSet.noneOf(Direction.class));
        return true;
    }

    static void fill(FilledTemplate template, PatternParameterHollow hollow, Vec3 center, Vec3 radius, Set<Direction> innerSides) {
        int maxX = template.getMax().getX();
        int maxY = template.getMax().getY();
        int maxZ = template.getMax().getZ();
        BitSet data = hollow != PatternParameterHollow.FILLED_INNER ? new BitSet(template.dataSize()) : null;
        for (int x = 0; x <= maxX; x++) {
            double dx = Math.abs(x - center.x) / radius.x;
            double dxx = dx * dx;
            for (int y = 0; y <= maxY; y++) {
                double dy = Math.abs(y - center.y) / radius.y;
                double dyy = dy * dy;
                for (int z = 0; z <= maxZ; z++) {
                    double dz = Math.abs(z - center.z) / radius.z;
                    if (dxx + dyy + dz * dz < 1) {
                        if (data != null) data.set(template.posToIndex(x, y, z));
                        else template.set(x, y, z, true);
                    }
                }
            }
        }
        if (data == null) return;
        boolean outerFilled = hollow.outerFilled;
        for (int x = 0; x <= maxX; x++) {
            for (int y = 0; y <= maxY; y++) {
                if (!innerSides.contains(Direction.NORTH)) {
                    for (int z = 0; z <= maxZ; z++) {
                        if (scan(template, data, x, y, z, outerFilled)) break;
                    }
                }
                if (!innerSides.contains(Direction.SOUTH)) {
                    for (int z = maxZ; z >= 0; z--) {
                        if (scan(template, data, x, y, z, outerFilled)) break;
                    }
                }
            }
        }
        for (int x = 0; x <= maxX; x++) {
            for (int z = 0; z <= maxZ; z++) {
                if (!innerSides.contains(Direction.DOWN)) {
                    for (int y = 0; y <= maxY; y++) {
                        if (scan(template, data, x, y, z, outerFilled)) break;
                    }
                }
                if (!innerSides.contains(Direction.UP)) {
                    for (int y = maxY; y >= 0; y--) {
                        if (scan(template, data, x, y, z, outerFilled)) break;
                    }
                }
            }
        }
        for (int y = 0; y <= maxY; y++) {
            for (int z = 0; z <= maxZ; z++) {
                if (!innerSides.contains(Direction.WEST)) {
                    for (int x = 0; x <= maxX; x++) {
                        if (scan(template, data, x, y, z, outerFilled)) break;
                    }
                }
                if (!innerSides.contains(Direction.EAST)) {
                    for (int x = maxX; x >= 0; x--) {
                        if (scan(template, data, x, y, z, outerFilled)) break;
                    }
                }
            }
        }
    }

    /** @return true at the sphere's surface, which ends the row's scan */
    private static boolean scan(FilledTemplate template, BitSet data, int x, int y, int z, boolean outerFilled) {
        if (data.get(template.posToIndex(x, y, z))) {
            template.set(x, y, z, true);
            return true;
        }
        if (outerFilled) template.set(x, y, z, true);
        return false;
    }

    enum SpherePartType {
        EIGHTH(3),
        QUARTER(2),
        HALF(1);

        final String lowerCaseName = name().toLowerCase(Locale.ROOT);
        final int openFaces;

        SpherePartType(int openFaces) {
            this.openFaces = openFaces;
        }
    }

    static final class Part extends PatternSphere {
        private final SpherePartType type;

        Part(SpherePartType type) {
            super("sphere_" + type.lowerCaseName);
            this.type = type;
        }

        @Override public int minParameters() { return type.openFaces == 1 ? 2 : 3; }
        @Override public int maxParameters() { return minParameters(); }

        @Nullable
        @Override
        public FillerParameter createParameter(int index) {
            if (index >= minParameters()) return null;
            return switch (index) {
                case 0 -> PatternParameterHollow.FILLED_INNER;
                case 1 -> PatternParameterFacing.DOWN;
                case 2 -> PatternParameterRotation.NONE;
                default -> null;
            };
        }

        @Override
        protected boolean fillTemplate(FilledTemplate template, FillerParameter[] params) {
            PatternParameterFacing facing = getParam(1, params, PatternParameterFacing.DOWN);
            PatternParameterRotation rotation = getParam(2, params, PatternParameterRotation.NONE);
            PatternParameterHollow hollow = getParam(0, params, PatternParameterHollow.FILLED_INNER);
            Set<Direction> innerSides = EnumSet.noneOf(Direction.class);
            Vec3 center = new Vec3(template.getMax().getX(), template.getMax().getY(), template.getMax().getZ()).scale(0.5);
            Vec3 radius = center.add(0.5, 0.5, 0.5);
            innerSides.add(facing.face);
            Direction.Axis axis = facing.face.getAxis();
            center = offset(center, facing.face, radius.get(axis));
            radius = replace(radius, axis, radius.get(axis) * 2);
            if (type.openFaces > 1) {
                Direction.Axis secondaryAxis = rotation.rotationCount % 2 == 1 ? next(axis) : previous(axis);
                Direction secondaryFace = facing(secondaryAxis, rotation.rotationCount >= 2);
                innerSides.add(secondaryFace);
                center = offset(center, secondaryFace, radius.get(secondaryAxis));
                radius = replace(radius, secondaryAxis, radius.get(secondaryAxis) * 2);
                if (type.openFaces > 2) {
                    int rotationCount = (rotation.rotationCount + 1) & 3;
                    Direction.Axis tertiaryAxis = rotationCount % 2 == 1 ? next(axis) : previous(axis);
                    Direction tertiaryFace = facing(tertiaryAxis, rotationCount >= 2);
                    innerSides.add(tertiaryFace);
                    center = offset(center, tertiaryFace, radius.get(tertiaryAxis));
                    radius = replace(radius, tertiaryAxis, radius.get(tertiaryAxis) * 2);
                }
            }
            fill(template, hollow, center, radius, innerSides);
            return true;
        }

        private static Direction.Axis next(Direction.Axis axis) {
            return axis == Direction.Axis.X ? Direction.Axis.Y : axis == Direction.Axis.Y ? Direction.Axis.Z : Direction.Axis.X;
        }

        private static Direction.Axis previous(Direction.Axis axis) {
            return axis == Direction.Axis.X ? Direction.Axis.Z : axis == Direction.Axis.Y ? Direction.Axis.X : Direction.Axis.Y;
        }

        private static Direction facing(Direction.Axis axis, boolean positive) {
            return Direction.fromAxisAndDirection(axis, positive ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
        }

        private static Vec3 offset(Vec3 vec, Direction face, double by) {
            return vec.add(face.getStepX() * by, face.getStepY() * by, face.getStepZ() * by);
        }

        private static Vec3 replace(Vec3 vec, Direction.Axis axis, double with) {
            return new Vec3(axis == Direction.Axis.X ? with : vec.x, axis == Direction.Axis.Y ? with : vec.y, axis == Direction.Axis.Z ? with : vec.z);
        }
    }
}

package ports.buildcraft.builders.filler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Upstream {@code PatternShape2d} and its shapes: an outline drawn on the plane across the chosen axis,
 * extruded along that axis, optionally rotated, and optionally flood-filled from the shape's fill point.
 */
abstract class PatternShape2d extends Pattern {
    PatternShape2d(String tag) {
        super(tag);
    }

    @Override public int minParameters() { return 3; }
    @Override public int maxParameters() { return 3; }

    @Nullable
    @Override
    public FillerParameter createParameter(int index) {
        return switch (index) {
            case 0 -> PatternParameterAxis.Y;
            case 1 -> PatternParameterHollow.HOLLOW;
            case 2 -> PatternParameterRotation.NONE;
            default -> null;
        };
    }

    @Override
    protected boolean fillTemplate(FilledTemplate template, FillerParameter[] params) {
        PatternParameterAxis axis = getParam(0, params, PatternParameterAxis.Y);
        PatternParameterRotation dir = getParam(2, params, PatternParameterRotation.NONE);
        PathIterator2d iterator = getIterator(template, axis);
        int maxA = axis == PatternParameterAxis.X ? template.getMax().getY() : template.getMax().getX();
        int maxB = axis == PatternParameterAxis.Z ? template.getMax().getY() : template.getMax().getZ();
        int normMaxA = maxA;
        int normMaxB = maxB;
        if (dir.rotationCount % 2 == 1) {
            int maxT = maxA;
            maxA = maxB;
            maxB = maxT;
            int rotatedMaxB = maxB;
            PathIterator2d old = iterator;
            iterator = (a, b) -> old.iterate(rotatedMaxB - b, a);
        }
        if (dir.rotationCount > 1) {
            PathIterator2d old = iterator;
            int rotatedMaxA = maxA;
            int rotatedMaxB = maxB;
            iterator = (a, b) -> old.iterate(rotatedMaxA - a, rotatedMaxB - b);
        }
        LineList list = new LineList(iterator);
        genShape(maxA, maxB, list);
        PatternParameterHollow filled = getParam(1, params, PatternParameterHollow.HOLLOW);
        if (filled.filled) {
            int fillA = list.fillInA;
            int fillB = list.fillInB;
            if (fillA != -1 && fillB != -1) {
                maxA = normMaxA;
                maxB = normMaxB;
                if (dir.rotationCount % 2 == 1) {
                    int fillT = fillA;
                    fillA = maxB - fillB;
                    fillB = fillT;
                }
                if (dir.rotationCount > 1) {
                    fillA = maxA - fillA;
                    fillB = maxB - fillB;
                }
                iterator = getIterator(template, axis);
                PositionGetter getter = getFillGetter(template, axis);
                if (filled.outerFilled) iterator = (a, b) -> {};
                Set<Point> visited = new HashSet<>();
                List<Point> open = new ArrayList<>();
                open.add(new Point(fillA, fillB));
                while (!open.isEmpty()) {
                    List<Point> next = new ArrayList<>();
                    for (Point p : open) {
                        if (p.a < 0 || p.a > maxA) continue;
                        if (p.b < 0 || p.b > maxB) continue;
                        if (!visited.add(p)) continue;
                        if (getter.isFilled(p.a, p.b)) continue;
                        iterator.iterate(p.a, p.b);
                        next.add(new Point(p.a + 1, p.b));
                        next.add(new Point(p.a - 1, p.b));
                        next.add(new Point(p.a, p.b + 1));
                        next.add(new Point(p.a, p.b - 1));
                    }
                    open = next;
                }
                if (filled.outerFilled) {
                    iterator = getIterator(template, axis);
                    for (int a = 0; a <= maxA; a++) {
                        for (int b = 0; b <= maxB; b++) {
                            if (!visited.contains(new Point(a, b))) iterator.iterate(a, b);
                        }
                    }
                }
            }
        }
        return true;
    }

    private static PathIterator2d getIterator(FilledTemplate template, PatternParameterAxis axis) {
        return switch (axis) {
            case X -> (y, z) -> template.setLineX(0, template.getMax().getX(), y, z, true);
            case Y -> (x, z) -> template.setLineY(x, 0, template.getMax().getY(), z, true);
            case Z -> (x, y) -> template.setLineZ(x, y, 0, template.getMax().getZ(), true);
        };
    }

    private static PositionGetter getFillGetter(FilledTemplate template, PatternParameterAxis axis) {
        return switch (axis) {
            case X -> (a, b) -> template.get(0, a, b);
            case Y -> (a, b) -> template.get(a, 0, b);
            case Z -> (a, b) -> template.get(a, b, 0);
        };
    }

    protected abstract void genShape(int maxA, int maxB, LineList list);

    @FunctionalInterface
    interface PathIterator2d {
        void iterate(int a, int b);
    }

    @FunctionalInterface
    private interface PositionGetter {
        boolean isFilled(int a, int b);
    }

    private record Point(int a, int b) {}

    /**
     * Upstream {@code PositionUtil.forAllOnPath2d}: a line of {@code max(|da|, |db|) + 1} blocks, the
     * leftover steps going to the start of the line.
     */
    static void forAllOnPath2d(int a1, int b1, int a2, int b2, PathIterator2d iter) {
        int diffA = a2 - a1;
        int diffB = b2 - b1;
        int sizeA = Math.abs(diffA) + 1;
        int sizeB = Math.abs(diffB) + 1;
        int multA = diffA > 0 ? 1 : -1;
        int multB = diffB > 0 ? 1 : -1;
        boolean reverse = false;
        int multiplier;
        int offset;
        if (sizeA == sizeB) {
            multiplier = 1;
            offset = 0;
        } else {
            if (sizeA > sizeB) {
                int temp = sizeA;
                sizeA = sizeB;
                sizeB = temp;
                reverse = true;
            }
            multiplier = sizeB / sizeA;
            offset = sizeB % sizeA;
        }
        int currentOffsetA = 0;
        int currentOffsetB = 0;
        for (int i = 0; i < sizeA; i++) {
            int length = multiplier;
            if (i < offset) length++;
            for (int l = 0; l < length; l++) {
                if (reverse) iter.iterate(a1 + multA * currentOffsetB, b1 + multB * currentOffsetA);
                else iter.iterate(a1 + multA * currentOffsetA, b1 + multB * currentOffsetB);
                currentOffsetB++;
            }
            currentOffsetA++;
        }
    }

    static final class LineList {
        private final PathIterator2d iterator;
        private int lastA, lastB;
        private int fillInA = -1, fillInB = -1;

        LineList(PathIterator2d iterator) {
            this.iterator = iterator;
        }

        void setFillPoint(int a, int b) {
            fillInA = a;
            fillInB = b;
        }

        void moveTo(int a, int b) {
            lastA = a;
            lastB = b;
        }

        void lineTo(int a, int b) {
            forAllOnPath2d(lastA, lastB, a, b, iterator);
            moveTo(a, b);
        }

        void lineFrom(int a, int b) {
            int a2 = lastA;
            int b2 = lastB;
            moveTo(a, b);
            lineTo(a2, b2);
            moveTo(a, b);
        }

        void arc(int ca, int cb, double ra, double rb) {
            arc(ca, cb, ra, rb, 0, 0, ArcType.ARC);
        }

        /** Upstream's midpoint ellipse, which can miss the ends of steep arcs. */
        void arc(int ca, int cb, double ra, double rb, int da, int db, ArcType type) {
            if (ra <= 0) throw new IllegalArgumentException("'ra' was less than or equal to 0! (Was " + ra + ")");
            if (rb <= 0) throw new IllegalArgumentException("'rb' was less than or equal to 0! (Was " + rb + ")");
            double ra2 = ra * ra;
            double rb2 = rb * rb;
            double sigma = 2 * rb2 + ra2 * (1 - 2 * rb);
            for (int a = 0, b = (int) rb; rb2 * a <= ra2 * b; a++) {
                plot(ca, cb, a, b, da, db, type);
                if (sigma >= 0) {
                    sigma += 4 * ra2 * (1 - b);
                    b--;
                }
                sigma += rb2 * ((4 * a) + 6);
            }
            sigma = 2 * ra2 + rb2 * (1 - 2 * ra);
            for (int a = (int) ra, b = 0; ra2 * b <= rb2 * a; b++) {
                plot(ca, cb, a, b, da, db, type);
                if (sigma >= 0) {
                    sigma += 4 * rb2 * (1 - a);
                    a--;
                }
                sigma += ra2 * ((4 * b) + 6);
            }
        }

        private void plot(int ca, int cb, int a, int b, int da, int db, ArcType type) {
            iterator.iterate(ca - a, cb - b);
            if (type.second) {
                iterator.iterate(ca + a + da, cb - b);
                if (type.all) {
                    iterator.iterate(ca - a, cb + b + db);
                    iterator.iterate(ca + a + da, cb + b + db);
                }
            }
        }
    }

    enum ArcType {
        ARC(false, false),
        SEMI_CIRCLE(true, false),
        FULL_CIRCLE(true, true);

        final boolean second, all;

        ArcType(boolean second, boolean all) {
            this.second = second;
            this.all = all;
        }
    }

    static final class Arc extends PatternShape2d {
        Arc() {
            super("2d_arc");
        }

        @Override
        protected void genShape(int maxA, int maxB, LineList list) {
            if (maxA == 0 || maxB == 0) {
                list.moveTo(0, 0);
                list.lineTo(maxA, maxB);
                return;
            }
            list.setFillPoint(maxA, maxB);
            list.arc(maxA, maxB, maxA, maxB);
        }
    }

    static final class Circle extends PatternShape2d {
        Circle() {
            super("2d_circle");
        }

        @Override
        protected void genShape(int maxA, int maxB, LineList list) {
            if (maxA == 0 || maxB == 0) {
                list.moveTo(0, 0);
                list.lineTo(maxA, maxB);
                return;
            }
            int halfA = maxA / 2;
            int halfB = maxB / 2;
            int halfAUpper = maxA - halfA;
            int halfBUpper = maxB - halfB;
            list.setFillPoint(halfA, halfB);
            list.arc(halfA, halfB, maxA / 2.0, maxB / 2.0, halfAUpper - halfA, halfBUpper - halfB, ArcType.FULL_CIRCLE);
        }
    }

    static final class Hexagon extends PatternShape2d {
        Hexagon() {
            super("2d_hexagon");
        }

        @Override
        protected void genShape(int maxA, int maxB, LineList list) {
            int indent = maxA / 4;
            int halfB = maxB / 2;
            list.moveTo(indent, 0);
            list.lineTo(maxA - indent, 0);
            list.lineFrom(maxA, halfB);
            list.moveTo(maxA, maxB - halfB);
            list.lineTo(maxA - indent, maxB);
            list.lineFrom(indent, maxB);
            list.lineFrom(0, maxB - halfB);
            list.moveTo(0, halfB);
            list.lineTo(indent, 0);
        }
    }

    /** Upstream never drew the octagon, so it leaves the whole box empty. */
    static final class Octagon extends PatternShape2d {
        Octagon() {
            super("2d_octagon");
        }

        @Override public int minParameters() { return 2; }
        @Override public int maxParameters() { return 2; }

        @Override
        protected void genShape(int maxA, int maxB, LineList list) {}
    }

    static final class Pentagon extends PatternShape2d {
        private static final double DIST_HORIZONTAL = StrictMath.sin(Math.toRadians(108 - 90));
        private static final double DIST_VERTICAL = StrictMath.cos(Math.toRadians(108 / 2)) / StrictMath.cos(Math.toRadians(108 - 90));

        Pentagon() {
            super("2d_pentagon");
        }

        @Override
        protected void genShape(int maxA, int maxB, LineList list) {
            int halfA = maxA / 2;
            int indentA = (int) Math.round(maxA * DIST_HORIZONTAL);
            int indentB = (int) Math.round(maxB * DIST_VERTICAL);
            list.moveTo(indentA, 0);
            list.lineTo(maxA - indentA, 0);
            list.lineFrom(maxA, indentB);
            list.lineTo(maxA - halfA, maxB);
            list.moveTo(halfA, maxB);
            list.lineFrom(0, indentB);
            list.lineTo(indentA, 0);
            list.setFillPoint(halfA, maxB / 2);
        }
    }

    static final class SemiCircle extends PatternShape2d {
        SemiCircle() {
            super("2d_semi_circle");
        }

        @Override
        protected void genShape(int maxA, int maxB, LineList list) {
            if (maxA == 0 || maxB == 0) {
                list.moveTo(0, 0);
                list.lineTo(maxA, maxB);
                return;
            }
            int halfA = maxA / 2;
            int halfAUpper = maxA - halfA;
            list.setFillPoint(halfA, maxB);
            list.arc(halfA, maxB, maxA / 2.0, maxB, halfAUpper - halfA, 0, ArcType.SEMI_CIRCLE);
        }
    }

    static final class Square extends PatternShape2d {
        Square() {
            super("2d_square");
        }

        @Override public int minParameters() { return 2; }
        @Override public int maxParameters() { return 2; }

        @Override
        protected void genShape(int maxA, int maxB, LineList list) {
            list.lineTo(maxA, 0);
            list.lineTo(maxA, maxB);
            list.lineTo(0, maxB);
            list.lineTo(0, 0);
            list.setFillPoint(maxA / 2, maxB / 2);
        }
    }

    static final class Triangle extends PatternShape2d {
        Triangle() {
            super("2d_triangle");
        }

        @Override
        protected void genShape(int maxA, int maxB, LineList list) {
            int halfA = maxA / 2;
            list.moveTo(maxA, maxB);
            list.lineTo(0, maxB);
            list.lineTo(halfA, 0);
            list.moveTo(maxA - halfA, 0);
            list.lineFrom(maxA, maxB);
            list.setFillPoint(halfA, maxB / 2);
        }
    }
}

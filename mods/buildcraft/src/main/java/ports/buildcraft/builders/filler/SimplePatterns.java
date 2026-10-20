package ports.buildcraft.builders.filler;

/** Upstream {@code PatternNone}, {@code PatternClear}, {@code PatternFill}, {@code PatternBox} and {@code PatternFrame}. */
final class SimplePatterns {
    private SimplePatterns() {}

    static final class None extends Pattern {
        None() {
            super("none");
        }

        @Override
        protected boolean fillTemplate(FilledTemplate template, FillerParameter[] params) {
            return false;
        }
    }

    static final class Clear extends Pattern {
        Clear() {
            super("clear");
        }

        @Override
        protected boolean fillTemplate(FilledTemplate template, FillerParameter[] params) {
            return true;
        }
    }

    static final class Fill extends Pattern {
        Fill() {
            super("fill");
        }

        @Override
        protected boolean fillTemplate(FilledTemplate template, FillerParameter[] params) {
            template.setAll(true);
            return true;
        }
    }

    static final class Box extends Pattern {
        Box() {
            super("box");
        }

        @Override
        protected boolean fillTemplate(FilledTemplate template, FillerParameter[] params) {
            template.setPlaneYZ(0, true);
            template.setPlaneYZ(template.getMax().getX(), true);
            template.setPlaneXZ(0, true);
            template.setPlaneXZ(template.getMax().getY(), true);
            template.setPlaneXY(0, true);
            template.setPlaneXY(template.getMax().getZ(), true);
            return true;
        }
    }

    static final class Frame extends Pattern {
        Frame() {
            super("frame");
        }

        @Override
        protected boolean fillTemplate(FilledTemplate template, FillerParameter[] params) {
            int maxX = template.getMax().getX();
            int maxY = template.getMax().getY();
            int maxZ = template.getMax().getZ();
            template.setLineX(0, maxX, 0, 0, true);
            template.setLineX(0, maxX, maxY, 0, true);
            template.setLineX(0, maxX, maxY, maxZ, true);
            template.setLineX(0, maxX, 0, maxZ, true);
            template.setLineY(0, 0, maxY, 0, true);
            template.setLineY(maxX, 0, maxY, 0, true);
            template.setLineY(maxX, 0, maxY, maxZ, true);
            template.setLineY(0, 0, maxY, maxZ, true);
            template.setLineZ(0, 0, 0, maxZ, true);
            template.setLineZ(maxX, 0, 0, maxZ, true);
            template.setLineZ(maxX, maxY, 0, maxZ, true);
            template.setLineZ(0, maxY, 0, maxZ, true);
            return true;
        }
    }
}

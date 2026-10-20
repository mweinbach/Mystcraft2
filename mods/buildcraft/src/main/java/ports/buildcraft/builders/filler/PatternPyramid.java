package ports.buildcraft.builders.filler;

import javax.annotation.Nullable;

/** Upstream {@code PatternPyramid}: layers that shrink towards the peak, leaning to the chosen side. */
final class PatternPyramid extends Pattern {
    PatternPyramid() {
        super("pyramid");
    }

    @Override public int maxParameters() { return 2; }
    @Override public int minParameters() { return 2; }

    @Nullable
    @Override
    public FillerParameter createParameter(int index) {
        return switch (index) {
            case 0 -> PatternParameterYDir.UP;
            case 1 -> PatternParameterCenter.CENTER;
            default -> null;
        };
    }

    @Override
    protected boolean fillTemplate(FilledTemplate template, FillerParameter[] params) {
        PatternParameterCenter center = params.length >= 2 && params[1] instanceof PatternParameterCenter c ? c : PatternParameterCenter.CENTER;
        int xLowerDiff = center.offsetX >= 0 ? 1 : 0;
        int xUpperDiff = center.offsetX <= 0 ? -1 : 0;
        int zLowerDiff = center.offsetZ >= 0 ? 1 : 0;
        int zUpperDiff = center.offsetZ <= 0 ? -1 : 0;
        int stepY = params.length >= 1 && params[0] instanceof PatternParameterYDir yDir && !yDir.up ? -1 : 1;
        int y = stepY == 1 ? 0 : template.getMax().getY();
        int xLower = 0;
        int xUpper = template.getMax().getX();
        int zLower = 0;
        int zUpper = template.getMax().getZ();
        while (y >= 0 && y <= template.getMax().getY()) {
            template.setAreaXZ(xLower, xUpper, y, zLower, zUpper, true);
            xLower += xLowerDiff;
            xUpper += xUpperDiff;
            zLower += zLowerDiff;
            zUpper += zUpperDiff;
            y += stepY;
            if (xLower > xUpper || zLower > zUpper) break;
        }
        return true;
    }
}

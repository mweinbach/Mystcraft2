package ports.buildcraft.builders.filler;

/** Upstream {@code PatternStairs}: layers that step in from one side as they rise or fall. */
final class PatternStairs extends Pattern {
    PatternStairs() {
        super("stairs");
    }

    @Override public int maxParameters() { return 2; }
    @Override public int minParameters() { return 2; }

    @Override
    public FillerParameter createParameter(int index) {
        return index == 1 ? PatternParameterXZDir.EAST : PatternParameterYDir.UP;
    }

    @Override
    protected boolean fillTemplate(FilledTemplate template, FillerParameter[] params) {
        PatternParameterYDir yDir = getParam(0, params, PatternParameterYDir.UP);
        PatternParameterXZDir xzDir = getParam(1, params, PatternParameterXZDir.EAST);
        int y = yDir == PatternParameterYDir.UP ? 0 : template.getMax().getY();
        int yStep = yDir == PatternParameterYDir.UP ? 1 : -1;
        int yEnd = yDir == PatternParameterYDir.UP ? template.getMax().getY() + 1 : -1;
        int fx = 0;
        int fz = 0;
        int tx = template.getMax().getX();
        int tz = template.getMax().getZ();
        while (y != yEnd) {
            template.setAreaXZ(fx, tx, y, fz, tz, true);
            fx += xzDir.dir.getStepX() > 0 ? 1 : 0;
            fz += xzDir.dir.getStepZ() > 0 ? 1 : 0;
            tx += xzDir.dir.getStepX() < 0 ? -1 : 0;
            tz += xzDir.dir.getStepZ() < 0 ? -1 : 0;
            y += yStep;
            if (fx > tx) break;
            if (fz > tz) break;
        }
        return true;
    }
}

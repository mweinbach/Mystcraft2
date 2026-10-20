package ports.buildcraft.builders.filler;

import net.minecraft.nbt.CompoundTag;

/** Upstream {@code PatternParameterYDir}: whether pyramids and stairs rise or fall. */
public enum PatternParameterYDir implements FillerParameter {
    UP(true),
    DOWN(false);

    public static final String TAG = "buildcraft:fillerParameterYDir";
    private static final PatternParameterYDir[] POSSIBLE_ORDER = {null, null, UP, null, null, null, DOWN};
    public final boolean up;

    PatternParameterYDir(boolean up) {
        this.up = up;
    }

    public static PatternParameterYDir readFromNbt(CompoundTag tag) {
        return tag.getBoolean("up") ? UP : DOWN;
    }

    @Override public String uniqueTag() { return TAG; }
    @Override public String translationKey() { return "direction." + (up ? "up" : "down"); }
    @Override public String spriteName() { return up ? "stairs_ascend" : "stairs_descend"; }
    @Override public FillerParameter[] allValues() { return values(); }
    @Override public FillerParameter[] getPossible() { return POSSIBLE_ORDER; }
    @Override public boolean isPossibleOrdered() { return true; }

    @Override
    public void writeToNbt(CompoundTag tag) {
        tag.putBoolean("up", up);
    }
}

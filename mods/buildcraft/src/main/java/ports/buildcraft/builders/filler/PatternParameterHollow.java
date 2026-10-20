package ports.buildcraft.builders.filler;

import net.minecraft.nbt.CompoundTag;

/** Upstream {@code PatternParameterHollow}: a shape's outline only, filled, or everything around it. */
public enum PatternParameterHollow implements FillerParameter {
    FILLED_INNER(true, false),
    FILLED_OUTER(true, true),
    HOLLOW(false, false);

    public static final String TAG = "buildcraft:fillerParameterHollow";
    public final boolean filled;
    public final boolean outerFilled;

    PatternParameterHollow(boolean filled, boolean outerFilled) {
        this.filled = filled;
        this.outerFilled = outerFilled;
    }

    public static PatternParameterHollow readFromNbt(CompoundTag tag) {
        if (tag.getBoolean("filled")) return tag.getBoolean("outer") ? FILLED_OUTER : FILLED_INNER;
        return HOLLOW;
    }

    @Override public String uniqueTag() { return TAG; }
    @Override public String translationKey() { return "fillerpattern.parameter." + (filled ? (outerFilled ? "filled_outer" : "filled") : "hollow"); }
    @Override public String spriteName() { return filled ? (outerFilled ? "filled_outer" : "filled_inner") : "hollow"; }
    @Override public FillerParameter[] allValues() { return values(); }

    @Override
    public void writeToNbt(CompoundTag tag) {
        tag.putBoolean("filled", filled);
        if (filled) tag.putBoolean("outer", outerFilled);
    }
}

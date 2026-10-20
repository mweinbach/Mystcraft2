package ports.buildcraft.builders.filler;

import net.minecraft.nbt.CompoundTag;

/** Upstream {@code PatternParameterRotation}: quarter turns of a 2D shape or sphere part. */
public enum PatternParameterRotation implements FillerParameter {
    NONE,
    QUARTER,
    HALF,
    THREE_QUARTERS;

    public static final String TAG = "buildcraft:fillerParameterRotation";
    private static final PatternParameterRotation[] POSSIBLE_ORDER = {null, null, NONE, null, QUARTER, null, HALF, null, THREE_QUARTERS};
    public final int rotationCount = ordinal();

    public static PatternParameterRotation readFromNbt(CompoundTag tag) {
        return values()[tag.getByte("d") & 3];
    }

    @Override public String uniqueTag() { return TAG; }
    @Override public String translationKey() { return "buildcraft.param.rotation." + rotationCount; }
    @Override public String spriteName() { return "rotation_" + rotationCount; }
    @Override public FillerParameter[] allValues() { return values(); }
    @Override public FillerParameter[] getPossible() { return POSSIBLE_ORDER; }
    @Override public boolean isPossibleOrdered() { return true; }

    @Override
    public void writeToNbt(CompoundTag tag) {
        tag.putByte("d", (byte) rotationCount);
    }
}

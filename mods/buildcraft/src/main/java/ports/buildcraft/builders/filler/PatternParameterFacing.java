package ports.buildcraft.builders.filler;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

/** Upstream {@code PatternParameterFacing}: the flat face of a sphere part. */
public enum PatternParameterFacing implements FillerParameter {
    DOWN(Direction.DOWN),
    UP(Direction.UP),
    NORTH(Direction.NORTH),
    SOUTH(Direction.SOUTH),
    WEST(Direction.WEST),
    EAST(Direction.EAST);

    public static final String TAG = "buildcraft:fillerParameterFacing";
    public final Direction face;

    PatternParameterFacing(Direction face) {
        this.face = face;
    }

    /** Upstream clamped to 0..6, one past the last value; a stored 6 reads as the last face here. */
    public static PatternParameterFacing readFromNbt(CompoundTag tag) {
        return values()[Math.max(0, Math.min(values().length - 1, tag.getByte("v")))];
    }

    @Override public String uniqueTag() { return TAG; }
    @Override public String translationKey() { return "buildcraft.param.facing." + face.getName(); }
    @Override public String spriteName() { return "face_" + face.getName(); }
    @Override public FillerParameter[] allValues() { return values(); }

    @Override
    public void writeToNbt(CompoundTag tag) {
        tag.putByte("v", (byte) ordinal());
    }
}

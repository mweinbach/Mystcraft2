package ports.buildcraft.builders.filler;

import net.minecraft.nbt.CompoundTag;

/** Upstream {@code PatternParameterCenter}: which side of the box a pyramid's peak leans to. */
public enum PatternParameterCenter implements FillerParameter {
    NORTH_WEST(-1, -1),
    NORTH(0, -1),
    NORTH_EAST(1, -1),
    WEST(-1, 0),
    CENTER(0, 0),
    EAST(1, 0),
    SOUTH_WEST(-1, 1),
    SOUTH(0, 1),
    SOUTH_EAST(1, 1);

    public static final String TAG = "buildcraft:fillerParameterCenter";
    private static final PatternParameterCenter[] POSSIBLE_ORDER = {
        CENTER, NORTH_WEST, NORTH, NORTH_EAST, EAST, SOUTH_EAST, SOUTH, SOUTH_WEST, WEST
    };
    public final int offsetX, offsetZ;

    PatternParameterCenter(int x, int z) {
        offsetX = x;
        offsetZ = z;
    }

    public static PatternParameterCenter readFromNbt(CompoundTag tag) {
        int ord = tag.getByte("dir");
        if (ord < 0 || ord >= values().length) return CENTER;
        return values()[ord];
    }

    @Override public String uniqueTag() { return TAG; }
    @Override public String translationKey() { return "direction.center." + ordinal(); }
    @Override public String spriteName() { return "center_" + ordinal(); }
    @Override public FillerParameter[] allValues() { return values(); }
    @Override public FillerParameter[] getPossible() { return POSSIBLE_ORDER; }
    @Override public boolean isPossibleOrdered() { return true; }

    @Override
    public void writeToNbt(CompoundTag tag) {
        tag.putByte("dir", (byte) ordinal());
    }
}

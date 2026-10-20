package ports.buildcraft.builders.filler;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Upstream {@code PatternParameterXZDir}: the horizontal direction stairs climb towards. */
public enum PatternParameterXZDir implements FillerParameter {
    WEST(Direction.WEST),
    EAST(Direction.EAST),
    NORTH(Direction.NORTH),
    SOUTH(Direction.SOUTH);

    public static final String TAG = "buildcraft:fillerParameterXZDir";
    private static final PatternParameterXZDir[] POSSIBLE_ORDER = {null, null, NORTH, null, EAST, null, SOUTH, null, WEST};
    public final Direction dir;

    PatternParameterXZDir(Direction dir) {
        this.dir = dir;
    }

    public static PatternParameterXZDir get(Direction face) {
        for (PatternParameterXZDir param : values()) {
            if (param.dir == face) return param;
        }
        throw new IllegalArgumentException("Can only accept horizontal directions (was given " + face + ")");
    }

    /** Older saves kept the direction under "dir", offset by two. */
    public static PatternParameterXZDir readFromNbt(CompoundTag tag) {
        int horizontal = tag.contains("dir", Tag.TAG_ANY_NUMERIC) ? tag.getByte("dir") + 2 : tag.getByte("d");
        return get(Direction.from2DDataValue(horizontal));
    }

    @Override public String uniqueTag() { return TAG; }
    @Override public String translationKey() { return "direction." + dir.getName(); }
    @Override public FillerParameter[] allValues() { return values(); }
    @Override public FillerParameter[] getPossible() { return POSSIBLE_ORDER; }
    @Override public boolean isPossibleOrdered() { return true; }

    @Override
    public String spriteName() {
        return switch (dir) {
            case WEST -> "arrow_left";
            case EAST -> "arrow_right";
            case NORTH -> "arrow_up";
            default -> "arrow_down";
        };
    }

    @Override
    public void writeToNbt(CompoundTag tag) {
        tag.putByte("d", (byte) dir.get2DDataValue());
    }
}

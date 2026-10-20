package ports.buildcraft.builders.filler;

import java.util.Locale;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

/** Upstream {@code PatternParameterAxis}: the axis a 2D shape is extruded along. */
public enum PatternParameterAxis implements FillerParameter {
    X(Direction.Axis.X),
    Y(Direction.Axis.Y),
    Z(Direction.Axis.Z);

    public static final String TAG = "buildcraft:filler_parameter_axis";
    public final Direction.Axis axis;

    PatternParameterAxis(Direction.Axis axis) {
        this.axis = axis;
    }

    public static PatternParameterAxis readFromNbt(CompoundTag tag) {
        byte ord = tag.getByte("a");
        if (ord <= 0) return X;
        if (ord >= 2) return Z;
        return Y;
    }

    @Override public String uniqueTag() { return TAG; }
    @Override public String translationKey() { return "buildcraft.param.axis." + name().toLowerCase(Locale.ROOT); }
    @Override public String spriteName() { return "axis_" + axis.getName(); }
    @Override public FillerParameter[] allValues() { return values(); }

    @Override
    public void writeToNbt(CompoundTag tag) {
        tag.putByte("a", (byte) ordinal());
    }
}

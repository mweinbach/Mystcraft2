package ports.buildcraft.energy;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** Upstream {@code EnumPowerStage}: selects the engine trunk texture and piston speed. */
public enum PowerStage implements StringRepresentable {
    BLUE, GREEN, YELLOW, RED, OVERHEAT, BLACK;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}

package ports.buildcraft.silicon.gate;

import java.util.Locale;

/** Upstream {@code EnumGateModifier}: parameter slots for each trigger and action, paid for with half the pairs. */
public enum EnumGateModifier {
    NO_MODIFIER(0, 0, 1),
    LAPIS(1, 0, 1),
    QUARTZ(1, 1, 2),
    DIAMOND(3, 3, 2);

    public static final EnumGateModifier[] VALUES = values();

    public final int triggerParams, actionParams;
    public final int slotDivisor;
    public final String tag = name().toLowerCase(Locale.ROOT);

    EnumGateModifier(int triggerParams, int actionParams, int slotDivisor) {
        this.triggerParams = triggerParams;
        this.actionParams = actionParams;
        this.slotDivisor = slotDivisor;
    }

    public static EnumGateModifier getByOrdinal(int ordinal) {
        return ordinal < 0 || ordinal >= VALUES.length ? NO_MODIFIER : VALUES[ordinal];
    }
}

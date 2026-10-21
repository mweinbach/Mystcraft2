package ports.buildcraft.silicon.gate;

import java.util.Locale;

/** Upstream {@code EnumGateLogic}: whether a group of connected triggers needs all or any of them on. */
public enum EnumGateLogic {
    AND,
    OR;

    public static final EnumGateLogic[] VALUES = values();

    public final String tag = name().toLowerCase(Locale.ROOT);

    public static EnumGateLogic getByOrdinal(int ordinal) {
        return ordinal < 0 || ordinal >= VALUES.length ? AND : VALUES[ordinal];
    }
}

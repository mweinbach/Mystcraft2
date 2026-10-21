package ports.buildcraft.lib;

import java.util.Locale;

/**
 * Upstream {@code IControllable}: a machine a gate can turn on, off or into a loop. Upstream stored this as a
 * capability on every face; the port has no capability faces, so a machine that implements this accepts control
 * from any side.
 */
public interface Controllable {
    enum Mode {
        ON,
        OFF,
        LOOP;

        public static final Mode[] VALUES = values();
        public final String lowerCaseName = name().toLowerCase(Locale.ROOT);
    }

    Mode getControlMode();

    void setControlMode(Mode mode);

    /** Upstream's default accepts every mode, including a null check the caller already made. */
    default boolean acceptsControlMode(Mode mode) {
        return mode != null;
    }
}

package ports.jurassicraft.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * One setting, defined once for both loaders. Loader backends persist the value in their native
 * format and call {@link #set}; until then {@link #get} returns the default.
 */
public final class ConfigOption<T> implements Supplier<T> {
    /** Configuration files keep the names used by the original Forge and Fabric releases of this port. */
    public enum File {
        COMMON("jurassicraft-common.toml", false),
        VEHICLES("jurassicraft-vehicles.toml", false),
        VEHICLE_CLIENT("jurassicraft-vehicle-client.toml", true);

        private final String fileName;
        private final boolean client;
        File(String fileName, boolean client) { this.fileName = fileName; this.client = client; }
        public String fileName() { return fileName; }
        public boolean client() { return client; }
    }

    private static final List<ConfigOption<?>> ALL = new ArrayList<>();

    private final File file;
    private final String section;
    private final String key;
    private final String comment;
    private final T defaultValue;
    private final int min;
    private final int max;
    private final boolean worldRestart;
    private volatile T value;

    private ConfigOption(File file, String section, String key, String comment, T defaultValue, int min, int max, boolean worldRestart) {
        this.file = file;
        this.section = section;
        this.key = key;
        this.comment = comment;
        this.defaultValue = defaultValue;
        this.min = min;
        this.max = max;
        this.worldRestart = worldRestart;
        this.value = defaultValue;
        synchronized (ALL) { ALL.add(this); }
    }

    public static ConfigOption<Boolean> bool(File file, String section, String key, boolean defaultValue, boolean worldRestart, String comment) {
        return new ConfigOption<>(file, section, key, comment, defaultValue, 0, 0, worldRestart);
    }
    public static ConfigOption<Integer> range(File file, String section, String key, int defaultValue, int min, int max, boolean worldRestart, String comment) {
        return new ConfigOption<>(file, section, key, comment, defaultValue, min, max, worldRestart);
    }

    public static List<ConfigOption<?>> all() {
        JurassicConfig.load();
        synchronized (ALL) { return Collections.unmodifiableList(new ArrayList<>(ALL)); }
    }

    @Override public T get() { return value; }
    public T defaultValue() { return defaultValue; }
    public File file() { return file; }
    /** Null for top-level settings. */
    public String section() { return section; }
    public String key() { return key; }
    public String comment() { return comment; }
    public boolean isInteger() { return defaultValue instanceof Integer; }
    public int min() { return min; }
    public int max() { return max; }
    public boolean worldRestart() { return worldRestart; }

    /** Accepts only values of the default's type and, for integers, within range; anything else keeps the current value. */
    public boolean set(Object candidate) {
        if (defaultValue instanceof Boolean && candidate instanceof Boolean) {
            value = cast(candidate);
            return true;
        }
        if (defaultValue instanceof Integer && candidate instanceof Integer number && number >= min && number <= max) {
            value = cast(candidate);
            return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private T cast(Object candidate) { return (T) candidate; }
}

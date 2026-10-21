/*
 * Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.mekanism.lib.common;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.file.FileConfig;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/**
 * A typed, commented TOML config layout. Values are validated against their definition when a file is loaded;
 * missing or invalid entries are reset to their defaults and the file is rewritten.
 */
public class ForgeConfigSpec {

    private final List<ValueSpec<?>> values;
    private final List<LevelComment> levelComments;
    @Nullable
    private Config childConfig;

    private ForgeConfigSpec(List<ValueSpec<?>> values, List<LevelComment> levelComments) {
        this.values = List.copyOf(values);
        this.levelComments = List.copyOf(levelComments);
    }

    private record LevelComment(List<String> path, String comment) {
    }

    public boolean isLoaded() {
        return childConfig != null;
    }

    public void setConfig(@Nullable CommentedConfig config) {
        this.childConfig = config;
        if (config != null && !isCorrect(config)) {
            correct(config);
            save();
        }
        afterReload();
    }

    public void acceptConfig(@Nullable CommentedConfig config) {
        setConfig(config);
    }

    public void save() {
        if (childConfig instanceof FileConfig fileConfig) {
            fileConfig.save();
        }
    }

    public void afterReload() {
        values.forEach(value -> value.configValue.clearCache());
    }

    public boolean isCorrect(CommentedConfig config) {
        for (ValueSpec<?> value : values) {
            Object raw = config.get(value.path);
            if (raw == null || !value.test(raw)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Resets missing and invalid entries to their defaults and writes every comment. Returns how many entries changed.
     */
    public int correct(CommentedConfig config) {
        int changes = 0;
        for (LevelComment levelComment : levelComments) {
            if (config.get(levelComment.path) instanceof Config) {
                config.setComment(levelComment.path, levelComment.comment);
            }
        }
        for (ValueSpec<?> value : values) {
            Object raw = config.get(value.path);
            Object corrected = raw == null ? null : value.correct(raw);
            if (corrected == null) {
                config.set(value.path, value.serialize(value.defaultSupplier.get()));
                changes++;
            } else if (corrected != raw) {
                config.set(value.path, corrected);
                changes++;
            }
            if (value.comment != null) {
                config.setComment(value.path, value.comment);
            }
        }
        return changes;
    }

    @Nullable
    Config config() {
        return childConfig;
    }

    private static class ValueSpec<T> {

        private final List<String> path;
        private final Supplier<T> defaultSupplier;
        private final Predicate<Object> validator;
        @Nullable
        private final String comment;
        private final Class<?> clazz;
        private ConfigValue<T> configValue;

        ValueSpec(List<String> path, Supplier<T> defaultSupplier, Predicate<Object> validator, @Nullable String comment, Class<?> clazz) {
            this.path = List.copyOf(path);
            this.defaultSupplier = defaultSupplier;
            this.validator = validator;
            this.comment = comment;
            this.clazz = clazz;
        }

        boolean test(Object raw) {
            return validator.test(raw);
        }

        /**
         * The value to store for a loaded entry: itself when valid, a converted copy for numbers of another width, or
         * {@code null} to reset it.
         */
        @Nullable
        Object correct(Object raw) {
            if (validator.test(raw)) {
                return raw;
            }
            if (raw instanceof Number number) {
                Object converted = null;
                if (clazz == Integer.class && number.longValue() == number.intValue()) {
                    converted = number.intValue();
                } else if (clazz == Long.class) {
                    converted = number.longValue();
                } else if (clazz == Double.class) {
                    converted = number.doubleValue();
                }
                if (converted != null && validator.test(converted)) {
                    return converted;
                }
            }
            return null;
        }

        Object serialize(T value) {
            return value instanceof Enum<?> e ? e.name() : value;
        }
    }

    public static class Builder {

        private final List<ValueSpec<?>> values = new ArrayList<>();
        private final List<LevelComment> levelComments = new ArrayList<>();
        private final List<String> currentPath = new ArrayList<>();
        private final List<String> pendingComment = new ArrayList<>();
        private boolean worldRestart;

        public Builder comment(String comment) {
            pendingComment.add(comment);
            return this;
        }

        public Builder comment(String... comment) {
            pendingComment.addAll(Arrays.asList(comment));
            return this;
        }

        public Builder translation(String translationKey) {
            return this;
        }

        public Builder worldRestart() {
            worldRestart = true;
            return this;
        }

        public Builder push(String path) {
            return push(split(path));
        }

        public Builder push(List<String> path) {
            currentPath.addAll(path);
            if (!pendingComment.isEmpty()) {
                levelComments.add(new LevelComment(List.copyOf(currentPath), takeComment()));
            }
            return this;
        }

        public Builder pop() {
            return pop(1);
        }

        public Builder pop(int count) {
            if (count > currentPath.size()) {
                throw new IllegalArgumentException("Attempted to pop " + count + " elements when only " + currentPath.size() + " exist");
            }
            for (int i = 0; i < count; i++) {
                currentPath.remove(currentPath.size() - 1);
            }
            return this;
        }

        private String takeComment() {
            String comment = String.join("\n", pendingComment);
            pendingComment.clear();
            return comment;
        }

        private List<String> fullPath(List<String> path) {
            List<String> full = new ArrayList<>(currentPath);
            full.addAll(path);
            return full;
        }

        private <T, V extends ConfigValue<T>> V add(List<String> path, Supplier<T> defaultSupplier, Predicate<Object> validator, Class<?> clazz,
              Function<ValueSpec<T>, V> factory) {
            if (worldRestart) {
                pendingComment.add("World restart required.");
                worldRestart = false;
            }
            String comment = pendingComment.isEmpty() ? null : takeComment();
            ValueSpec<T> spec = new ValueSpec<>(fullPath(path), defaultSupplier, validator, comment, clazz);
            V value = factory.apply(spec);
            spec.configValue = value;
            values.add(spec);
            return value;
        }

        public <T> ConfigValue<T> define(String path, T defaultValue) {
            return define(split(path), defaultValue);
        }

        public <T> ConfigValue<T> define(List<String> path, T defaultValue) {
            Objects.requireNonNull(defaultValue, "Default value can not be null");
            Class<?> clazz = defaultValue.getClass();
            return define(path, () -> defaultValue, o -> o != null && clazz.isAssignableFrom(o.getClass()));
        }

        public <T> ConfigValue<T> define(String path, T defaultValue, Predicate<Object> validator) {
            return define(split(path), () -> defaultValue, validator);
        }

        public <T> ConfigValue<T> define(String path, Supplier<T> defaultSupplier, Predicate<Object> validator) {
            return define(split(path), defaultSupplier, validator);
        }

        public <T> ConfigValue<T> define(List<String> path, Supplier<T> defaultSupplier, Predicate<Object> validator) {
            Object sample = defaultSupplier.get();
            return add(path, defaultSupplier, validator, sample == null ? Object.class : sample.getClass(), ConfigValue::new);
        }

        public BooleanValue define(String path, boolean defaultValue) {
            return define(split(path), defaultValue);
        }

        public BooleanValue define(List<String> path, boolean defaultValue) {
            return add(path, () -> defaultValue, o -> o instanceof Boolean || o instanceof String s && ("true".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s)),
                  Boolean.class, BooleanValue::new);
        }

        public IntValue defineInRange(String path, int defaultValue, int min, int max) {
            return defineInRange(split(path), defaultValue, min, max);
        }

        public IntValue defineInRange(List<String> path, int defaultValue, int min, int max) {
            return add(path, () -> defaultValue, rangeValidator(Integer.class, min, max), Integer.class, IntValue::new);
        }

        public IntValue defineInRange(String path, Supplier<Integer> defaultSupplier, int min, int max) {
            return add(split(path), defaultSupplier, rangeValidator(Integer.class, min, max), Integer.class, IntValue::new);
        }

        public LongValue defineInRange(String path, long defaultValue, long min, long max) {
            return defineInRange(split(path), defaultValue, min, max);
        }

        public LongValue defineInRange(List<String> path, long defaultValue, long min, long max) {
            return add(path, () -> defaultValue, rangeValidator(Long.class, min, max), Long.class, LongValue::new);
        }

        public LongValue defineInRange(String path, Supplier<Long> defaultSupplier, long min, long max) {
            return add(split(path), defaultSupplier, rangeValidator(Long.class, min, max), Long.class, LongValue::new);
        }

        public DoubleValue defineInRange(String path, double defaultValue, double min, double max) {
            return defineInRange(split(path), defaultValue, min, max);
        }

        public DoubleValue defineInRange(List<String> path, double defaultValue, double min, double max) {
            return add(path, () -> defaultValue, rangeValidator(Double.class, min, max), Double.class, DoubleValue::new);
        }

        public DoubleValue defineInRange(String path, Supplier<Double> defaultSupplier, double min, double max) {
            return add(split(path), defaultSupplier, rangeValidator(Double.class, min, max), Double.class, DoubleValue::new);
        }

        public <V extends Comparable<? super V>> ConfigValue<V> defineInRange(String path, V defaultValue, V min, V max, Class<V> clazz) {
            return add(split(path), () -> defaultValue, rangeValidator(clazz, min, max), clazz, ConfigValue::new);
        }

        private <V extends Comparable<? super V>> Predicate<Object> rangeValidator(Class<V> clazz, V min, V max) {
            pendingComment.add(rangeComment(min, max));
            return o -> clazz.isInstance(o) && clazz.cast(o).compareTo(min) >= 0 && clazz.cast(o).compareTo(max) <= 0;
        }

        private static String rangeComment(Object min, Object max) {
            boolean noMin = min.equals(Integer.MIN_VALUE) || min.equals(Long.MIN_VALUE) || min.equals(-Double.MAX_VALUE);
            boolean noMax = max.equals(Integer.MAX_VALUE) || max.equals(Long.MAX_VALUE) || max.equals(Double.MAX_VALUE);
            if (noMin && noMax) {
                return "Range: any";
            } else if (noMax) {
                return "Range: > " + min;
            } else if (noMin) {
                return "Range: < " + max;
            }
            return "Range: " + min + " ~ " + max;
        }

        public <T> ConfigValue<T> defineInList(String path, T defaultValue, Collection<? extends T> acceptableValues) {
            return define(split(path), () -> defaultValue, acceptableValues::contains);
        }

        public <V extends Enum<V>> EnumValue<V> defineEnum(String path, V defaultValue) {
            return defineEnum(split(path), defaultValue, defaultValue.getDeclaringClass().getEnumConstants());
        }

        @SafeVarargs
        public final <V extends Enum<V>> EnumValue<V> defineEnum(String path, V defaultValue, V... acceptableValues) {
            return defineEnum(split(path), defaultValue, acceptableValues);
        }

        public <V extends Enum<V>> EnumValue<V> defineEnum(String path, V defaultValue, Collection<V> acceptableValues) {
            return defineEnum(split(path), defaultValue, new ArrayList<>(acceptableValues).toArray(
                  (V[]) Array.newInstance(defaultValue.getDeclaringClass(), 0)));
        }

        public <V extends Enum<V>> EnumValue<V> defineEnum(String path, V defaultValue, Predicate<Object> validator) {
            Class<V> clazz = defaultValue.getDeclaringClass();
            return add(split(path), () -> defaultValue, o -> {
                V value = EnumValue.parse(clazz, o);
                return value != null && validator.test(value);
            }, clazz, spec -> new EnumValue<>(spec, clazz));
        }

        @SafeVarargs
        private <V extends Enum<V>> EnumValue<V> defineEnum(List<String> path, V defaultValue, V... acceptableValues) {
            Class<V> clazz = defaultValue.getDeclaringClass();
            List<V> acceptable = Arrays.asList(acceptableValues);
            pendingComment.add("Allowed Values: " + String.join(", ", acceptable.stream().map(Enum::name).toList()));
            return add(path, () -> defaultValue, o -> {
                V value = EnumValue.parse(clazz, o);
                return value != null && acceptable.contains(value);
            }, clazz, spec -> new EnumValue<>(spec, clazz));
        }

        public <T> ConfigValue<List<? extends T>> defineList(String path, List<? extends T> defaultValue, Predicate<Object> elementValidator) {
            return defineList(split(path), () -> defaultValue, elementValidator);
        }

        public <T> ConfigValue<List<? extends T>> defineList(List<String> path, Supplier<List<? extends T>> defaultSupplier, Predicate<Object> elementValidator) {
            return defineList(path, defaultSupplier, elementValidator, false);
        }

        public <T> ConfigValue<List<? extends T>> defineListAllowEmpty(List<String> path, Supplier<List<? extends T>> defaultSupplier,
              Predicate<Object> elementValidator) {
            return defineList(path, defaultSupplier, elementValidator, true);
        }

        private <T> ConfigValue<List<? extends T>> defineList(List<String> path, Supplier<List<? extends T>> defaultSupplier, Predicate<Object> elementValidator,
              boolean allowEmpty) {
            return add(path, defaultSupplier, o -> o instanceof List<?> list && (allowEmpty || !list.isEmpty()) && list.stream().allMatch(elementValidator),
                  List.class, ConfigValue::new);
        }

        public ForgeConfigSpec build() {
            ForgeConfigSpec spec = new ForgeConfigSpec(values, levelComments);
            values.forEach(value -> value.configValue.spec = spec);
            return spec;
        }

        private static List<String> split(String path) {
            return Arrays.asList(path.split("\\."));
        }
    }

    public static class ConfigValue<T> implements Supplier<T> {

        private final ValueSpec<T> valueSpec;
        private ForgeConfigSpec spec;
        @Nullable
        private T cachedValue;

        ConfigValue(ValueSpec<T> valueSpec) {
            this.valueSpec = valueSpec;
        }

        public List<String> getPath() {
            return Collections.unmodifiableList(valueSpec.path);
        }

        @Override
        public T get() {
            Objects.requireNonNull(spec, "Cannot get config value before spec is built");
            Config config = spec.config();
            if (config == null) {
                throw new IllegalStateException("Cannot get config value before config is loaded.");
            }
            if (cachedValue == null) {
                Object raw = config.get(valueSpec.path);
                cachedValue = raw == null ? getDefault() : convert(raw);
            }
            return cachedValue;
        }

        @SuppressWarnings("unchecked")
        protected T convert(Object raw) {
            return (T) raw;
        }

        public T getDefault() {
            return valueSpec.defaultSupplier.get();
        }

        public ForgeConfigSpec.Builder next() {
            throw new UnsupportedOperationException();
        }

        public void save() {
            Objects.requireNonNull(spec, "Cannot save config value before spec is built");
            spec.save();
        }

        public void set(T value) {
            Objects.requireNonNull(spec, "Cannot set config value before spec is built");
            Config config = spec.config();
            if (config == null) {
                throw new IllegalStateException("Cannot set config value before config is loaded.");
            }
            config.set(valueSpec.path, valueSpec.serialize(value));
            cachedValue = value;
        }

        public void clearCache() {
            cachedValue = null;
        }
    }

    public static class BooleanValue extends ConfigValue<Boolean> {

        BooleanValue(ValueSpec<Boolean> valueSpec) {
            super(valueSpec);
        }

        @Override
        protected Boolean convert(Object raw) {
            return raw instanceof String s ? Boolean.parseBoolean(s) : (Boolean) raw;
        }
    }

    public static class IntValue extends ConfigValue<Integer> {

        IntValue(ValueSpec<Integer> valueSpec) {
            super(valueSpec);
        }

        @Override
        protected Integer convert(Object raw) {
            return ((Number) raw).intValue();
        }
    }

    public static class LongValue extends ConfigValue<Long> {

        LongValue(ValueSpec<Long> valueSpec) {
            super(valueSpec);
        }

        @Override
        protected Long convert(Object raw) {
            return ((Number) raw).longValue();
        }
    }

    public static class DoubleValue extends ConfigValue<Double> {

        DoubleValue(ValueSpec<Double> valueSpec) {
            super(valueSpec);
        }

        @Override
        protected Double convert(Object raw) {
            return ((Number) raw).doubleValue();
        }
    }

    public static class EnumValue<T extends Enum<T>> extends ConfigValue<T> {

        private final Class<T> clazz;

        EnumValue(ValueSpec<T> valueSpec, Class<T> clazz) {
            super(valueSpec);
            this.clazz = clazz;
        }

        @Override
        protected T convert(Object raw) {
            T value = parse(clazz, raw);
            return value == null ? getDefault() : value;
        }

        @Nullable
        static <T extends Enum<T>> T parse(Class<T> clazz, Object raw) {
            if (clazz.isInstance(raw)) {
                return clazz.cast(raw);
            } else if (raw instanceof String name) {
                for (T constant : clazz.getEnumConstants()) {
                    if (constant.name().equals(name) || constant.name().equalsIgnoreCase(name.toUpperCase(Locale.ROOT))) {
                        return constant;
                    }
                }
            } else if (raw instanceof Number number) {
                int ordinal = number.intValue();
                T[] constants = clazz.getEnumConstants();
                if (ordinal >= 0 && ordinal < constants.length) {
                    return constants[ordinal];
                }
            }
            return null;
        }
    }
}

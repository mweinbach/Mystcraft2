/*
 * Extreme Reactors 1.19.2 port - corelib
 * Copyright (c) 2026 Extreme Reactors 1.19.2 port contributors. MIT License, see LICENSE.
 */
package ports.extremereactors.corelib.config;

import org.apache.commons.lang3.tuple.Pair;
import ports.extremereactors.corelib.internal.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A typed configuration file, the same on every loader.
 *
 * <p>The file uses a small subset of TOML: one {@code [section]} table per pushed path and
 * {@code key = value} lines with boolean, integer or floating point values. Unknown keys are dropped and missing
 * or invalid values are reset to their default when the file is loaded; the file is then written back so it
 * always lists every setting with its comment.</p>
 */
public final class ConfigSpec {

    public static final class Builder {

        public Builder() {

            this._entries = new LinkedHashMap<>();
            this._path = new ArrayList<>();
            this._pendingComment = List.of();
            this._sectionComments = new LinkedHashMap<>();
        }

        /**
         * Build the settings object and the spec that loads its values.
         */
        public <T> Pair<T, ConfigSpec> configure(final Function<Builder, T> factory) {

            final T settings = factory.apply(this);

            return Pair.of(settings, new ConfigSpec(this._entries, this._sectionComments));
        }

        public Builder comment(final String... lines) {

            this._pendingComment = List.of(lines);
            return this;
        }

        /**
         * Accepted for compatibility with the settings declarations; titles are not used.
         */
        public Builder translation(final String key) {
            return this;
        }

        /**
         * Accepted for compatibility with the settings declarations; values are only read at startup anyway.
         */
        public Builder worldRestart() {
            return this;
        }

        public Builder push(final String name) {

            this._path.add(name);

            final String section = String.join(".", this._path);

            if (!this._pendingComment.isEmpty()) {
                this._sectionComments.put(section, this._pendingComment);
            } else {
                this._sectionComments.putIfAbsent(section, List.of());
            }

            this._pendingComment = List.of();
            return this;
        }

        public Builder pop() {

            if (this._path.isEmpty()) {
                throw new IllegalStateException("pop() without a matching push()");
            }

            this._path.remove(this._path.size() - 1);
            return this;
        }

        public BooleanValue define(final String name, final boolean defaultValue) {
            return new BooleanValue(this.add(name, new Entry<>(defaultValue, Boolean::parseBoolean, v -> true, "")));
        }

        public IntValue defineInRange(final String name, final int defaultValue, final int min, final int max) {
            return new IntValue(this.add(name, new Entry<>(defaultValue, Integer::parseInt, v -> v >= min && v <= max,
                    String.format("Range: %d ~ %d", min, max))));
        }

        public DoubleValue defineInRange(final String name, final double defaultValue, final double min, final double max) {
            return new DoubleValue(this.add(name, new Entry<>(defaultValue, Double::parseDouble, v -> v >= min && v <= max,
                    String.format("Range: %s ~ %s", min, max))));
        }

        //region internals

        private <T> Entry<T> add(final String name, final Entry<T> entry) {

            entry._comment = this._pendingComment;
            this._pendingComment = List.of();

            final String key = this._path.isEmpty() ? name : String.join(".", this._path) + "." + name;
            @SuppressWarnings("unchecked")
            final Entry<T> existing = (Entry<T>) this._entries.get(key);

            if (null != existing) {
                return existing;
            }

            this._entries.put(key, entry);
            return entry;
        }

        private final Map<String, Entry<?>> _entries;
        private final List<String> _path;
        private List<String> _pendingComment;
        private final Map<String, List<String>> _sectionComments;

        //endregion
    }

    public static class ConfigValue<T> {

        public T get() {
            return this._entry._value;
        }

        public T getDefault() {
            return this._entry._default;
        }

        public void set(final T value) {

            if (this._entry._validator.test(value)) {
                this._entry._value = value;
            }
        }

        ConfigValue(final Entry<T> entry) {
            this._entry = entry;
        }

        private final Entry<T> _entry;
    }

    public static final class BooleanValue extends ConfigValue<Boolean> {

        BooleanValue(final Entry<Boolean> entry) {
            super(entry);
        }
    }

    public static final class IntValue extends ConfigValue<Integer> {

        IntValue(final Entry<Integer> entry) {
            super(entry);
        }
    }

    public static final class DoubleValue extends ConfigValue<Double> {

        DoubleValue(final Entry<Double> entry) {
            super(entry);
        }
    }

    /**
     * Read the values from the given file, creating or completing it as needed.
     */
    public void load(final Path file) {

        final Map<String, String> raw = Files.isRegularFile(file) ? read(file) : Collections.emptyMap();

        for (final Map.Entry<String, Entry<?>> entry : this._entries.entrySet()) {

            final String text = raw.get(entry.getKey());

            if (null == text) {
                entry.getValue().reset();
            } else if (!entry.getValue().parse(text)) {

                Log.LOGGER.warn("Invalid value '{}' for {} in {}, using the default", text, entry.getKey(), file);
                entry.getValue().reset();
            }
        }

        this.save(file);
    }

    public void save(final Path file) {

        final StringBuilder out = new StringBuilder();
        String currentSection = null;

        for (final Map.Entry<String, Entry<?>> mapEntry : this._entries.entrySet()) {

            final String key = mapEntry.getKey();
            final int dot = key.lastIndexOf('.');
            final String section = dot < 0 ? "" : key.substring(0, dot);
            final Entry<?> entry = mapEntry.getValue();

            if (!section.equals(currentSection)) {

                if (!section.isEmpty()) {

                    if (out.length() > 0) {
                        out.append('\n');
                    }

                    this._sectionComments.getOrDefault(section, List.of()).forEach(line -> out.append("#").append(line).append('\n'));
                    out.append('[').append(section).append("]\n");
                }

                currentSection = section;
            }

            entry._comment.forEach(line -> out.append("\t#").append(line).append('\n'));

            if (!entry._rangeComment.isEmpty()) {
                out.append("\t#").append(entry._rangeComment).append('\n');
            }

            out.append('\t').append(key.substring(dot + 1)).append(" = ").append(entry._value).append('\n');
        }

        try {

            Files.createDirectories(file.getParent());
            Files.writeString(file, out.toString(), StandardCharsets.UTF_8);

        } catch (IOException ex) {
            Log.LOGGER.error("Unable to write the config file {}", file, ex);
        }
    }

    //region internals

    private static final class Entry<T> {

        Entry(final T defaultValue, final Function<String, T> parser, final Predicate<T> validator,
              final String rangeComment) {

            this._default = defaultValue;
            this._value = defaultValue;
            this._parser = parser;
            this._validator = validator;
            this._rangeComment = rangeComment;
            this._comment = List.of();
        }

        void reset() {
            this._value = this._default;
        }

        boolean parse(final String text) {

            try {

                final T value = this._parser.apply(text);

                if (this._validator.test(value) && (!(this._default instanceof Boolean) || isBooleanLiteral(text))) {

                    this._value = value;
                    return true;
                }

            } catch (NumberFormatException ignored) {
            }

            return false;
        }

        private static boolean isBooleanLiteral(final String text) {
            return "true".equals(text) || "false".equals(text);
        }

        private final T _default;
        private T _value;
        private final Function<String, T> _parser;
        private final Predicate<T> _validator;
        private final String _rangeComment;
        private List<String> _comment;
    }

    private static Map<String, String> read(final Path file) {

        final Map<String, String> values = new LinkedHashMap<>();
        String section = "";

        try {

            for (final String rawLine : Files.readAllLines(file, StandardCharsets.UTF_8)) {

                final String line = stripComment(rawLine).trim();

                if (line.isEmpty()) {
                    continue;
                }

                if (line.startsWith("[") && line.endsWith("]")) {

                    section = line.substring(1, line.length() - 1).trim();
                    continue;
                }

                final int equals = line.indexOf('=');

                if (equals > 0) {

                    final String key = line.substring(0, equals).trim();
                    final String value = line.substring(equals + 1).trim();

                    values.put(section.isEmpty() ? key : section + "." + key, value);
                }
            }

        } catch (IOException ex) {
            Log.LOGGER.error("Unable to read the config file {}, using the defaults", file, ex);
        }

        return values;
    }

    private static String stripComment(final String line) {

        final int hash = line.indexOf('#');

        return hash < 0 ? line : line.substring(0, hash);
    }

    private ConfigSpec(final Map<String, Entry<?>> entries, final Map<String, List<String>> sectionComments) {

        this._entries = entries;
        this._sectionComments = sectionComments;
    }

    private final Map<String, Entry<?>> _entries;
    private final Map<String, List<String>> _sectionComments;

    //endregion
}

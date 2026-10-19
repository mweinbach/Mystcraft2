package com.technovision.advancedgenetics.fabric;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.io.ParsingException;
import com.electronwill.nightconfig.core.io.ParsingMode;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.technovision.advancedgenetics.AdvancedGenetics;
import com.technovision.advancedgenetics.Config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Loads {@code config/AdvancedGenetics.toml} with the corrections ForgeConfigSpec applies, so the
 * same file means the same settings on both loaders: wrong types fall back to the default, numbers
 * are clamped into range, unknown keys are dropped and comments are rewritten. Read once at startup.
 */
final class FabricConfig {

    private FabricConfig() { }

    static void load(Path file) {
        TomlFormat format = TomlFormat.instance();
        CommentedConfig config = CommentedConfig.of(LinkedHashMap::new, format);
        String original = null;
        if (Files.exists(file)) {
            try {
                original = Files.readString(file, StandardCharsets.UTF_8);
                format.createParser().parse(original, config, ParsingMode.REPLACE);
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot read " + file, e);
            } catch (ParsingException e) {
                throw new IllegalStateException("Cannot parse " + file + "; fix or delete it", e);
            }
        }

        correct(config);
        String corrected = format.createWriter().writeToString(config);
        if (!corrected.equals(original)) {
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, corrected, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot write " + file, e);
            }
        }
        AdvancedGenetics.LOGGER.debug("Loaded Advanced Genetics config from {}", file);
    }

    private static void correct(CommentedConfig config) {
        Map<String, Object> root = config.valueMap();
        for (Config.Section section : Config.sections()) {
            if (!(root.get(section.name()) instanceof CommentedConfig)) {
                root.put(section.name(), config.createSubConfig());
            }
            CommentedConfig table = (CommentedConfig) root.get(section.name());
            Map<String, Object> values = table.valueMap();
            for (Config.Value<?> value : section.values()) {
                Object raw = values.get(value.key());
                Object stored = corrected(value, raw);
                values.put(value.key(), stored);
                bind(value, stored);
                String comment = comment(value);
                if (comment == null) {
                    table.removeComment(value.key());
                } else {
                    table.setComment(value.key(), comment);
                }
            }
            Set<String> known = section.values().stream().map(Config.Value::key).collect(Collectors.toSet());
            values.keySet().retainAll(known);
            config.setComment(section.name(), String.join("\n", section.comment()));
        }
        Set<String> sections = Config.sections().stream().map(Config.Section::name).collect(Collectors.toSet());
        root.keySet().retainAll(sections);
    }

    /** The value kept in the file, following ForgeConfigSpec's test-then-correct rules. */
    private static Object corrected(Config.Value<?> value, Object raw) {
        if (value instanceof Config.BoolValue bool) {
            if (raw instanceof Boolean) return raw;
            if (raw instanceof String text && (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("false"))) return raw;
            return bool.defaultValue();
        }
        if (value instanceof Config.IntValue integer) {
            if (!(raw instanceof Number number)) return integer.defaultValue();
            if (number.doubleValue() < integer.min()) return integer.min();
            if (number.doubleValue() > integer.max()) return integer.max();
            return raw;
        }
        if (value instanceof Config.DoubleValue decimal) {
            if (!(raw instanceof Number number)) return decimal.defaultValue();
            if (number.doubleValue() < decimal.min()) return decimal.min();
            if (number.doubleValue() > decimal.max()) return decimal.max();
            return raw;
        }
        throw new IllegalArgumentException("Unsupported config value " + value.key());
    }

    private static void bind(Config.Value<?> value, Object stored) {
        if (value instanceof Config.BoolValue bool) {
            bool.set(stored instanceof String text ? Boolean.parseBoolean(text) : (Boolean) stored);
        } else if (value instanceof Config.IntValue integer) {
            integer.set(((Number) stored).intValue());
        } else if (value instanceof Config.DoubleValue decimal) {
            decimal.set(((Number) stored).doubleValue());
        }
    }

    /** ForgeConfigSpec's comment: the value's lines, then the range for numbers. */
    private static String comment(Config.Value<?> value) {
        String range = null;
        if (value instanceof Config.IntValue integer) {
            range = integer.max() == Integer.MAX_VALUE ? "> " + integer.min()
                    : integer.min() == Integer.MIN_VALUE ? "< " + integer.max()
                    : integer.min() + " ~ " + integer.max();
        } else if (value instanceof Config.DoubleValue decimal) {
            range = decimal.min() + " ~ " + decimal.max();
        }
        String text = String.join("\n", value.comment());
        if (range == null) return text.isEmpty() ? null : text;
        return text.isEmpty() ? "Range: " + range : text + "\nRange: " + range;
    }
}

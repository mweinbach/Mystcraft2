package com.technovision.advancedgenetics.fabric;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.FileWatcher;
import com.electronwill.nightconfig.core.io.ParsingException;
import com.electronwill.nightconfig.core.io.ParsingMode;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.technovision.advancedgenetics.AdvancedGenetics;
import com.technovision.advancedgenetics.Config;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Loads {@code config/AdvancedGenetics.toml} with the corrections ForgeConfigSpec applies, so the
 * same file means the same settings on both loaders: wrong types fall back to the default, numbers
 * are clamped into range, missing keys are added, unknown keys are dropped and comments are
 * rewritten. Like Forge, it watches the file with night-config's {@link FileWatcher} and reloads it
 * when it changes; the new values reach the game on the server thread.
 */
public final class FabricConfig {

    /** A save can fire several modify events, and a half-written file must not be read. */
    private static final long RELOAD_DELAY_MS = 250;
    private static final AtomicLong CHANGES = new AtomicLong();
    private static volatile MinecraftServer server;
    /** The file as last read or written by this class, so its own rewrites do not reload again. */
    private static String lastText;

    private FabricConfig() { }

    /** One corrected read of the file: the text to keep on disk and the values it gives the game. */
    public record Corrected(String text, Map<Config.Value<?>, Object> values) {
        /** Hands every value to the game; run it on the thread that runs gameplay. */
        public void apply() {
            values.forEach(Config.Value::load);
        }
    }

    /** Applies ForgeConfigSpec's corrections to {@code text}, touching neither the game nor the disk. */
    public static Corrected correct(String text) {
        TomlFormat format = TomlFormat.instance();
        CommentedConfig config = CommentedConfig.of(LinkedHashMap::new, format);
        format.createParser().parse(text, config, ParsingMode.REPLACE);
        Map<Config.Value<?>, Object> values = new LinkedHashMap<>();
        Map<String, Object> root = config.valueMap();
        for (Config.Section section : Config.sections()) {
            if (!(root.get(section.name()) instanceof CommentedConfig)) {
                root.put(section.name(), config.createSubConfig());
            }
            CommentedConfig table = (CommentedConfig) root.get(section.name());
            Map<String, Object> stored = table.valueMap();
            for (Config.Value<?> value : section.values()) {
                Object corrected = corrected(value, stored.get(value.key()));
                stored.put(value.key(), corrected);
                values.put(value, typed(value, corrected));
                String comment = comment(value);
                if (comment == null) {
                    table.removeComment(value.key());
                } else {
                    table.setComment(value.key(), comment);
                }
            }
            Set<String> known = section.values().stream().map(Config.Value::key).collect(Collectors.toSet());
            stored.keySet().retainAll(known);
            config.setComment(section.name(), String.join("\n", section.comment()));
        }
        Set<String> sections = Config.sections().stream().map(Config.Section::name).collect(Collectors.toSet());
        root.keySet().retainAll(sections);
        return new Corrected(format.createWriter().writeToString(config), Collections.unmodifiableMap(values));
    }

    /** Startup: reads or creates the file, corrects it and applies it at once. Any error stops the game. */
    static synchronized void load(Path file) {
        String original = null;
        if (Files.exists(file)) {
            try {
                original = Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot read " + file, e);
            }
        }
        Corrected corrected;
        try {
            corrected = correct(original == null ? "" : original);
        } catch (ParsingException e) {
            throw new IllegalStateException("Cannot parse " + file + "; fix or delete it", e);
        }
        if (!corrected.text().equals(original)) {
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, corrected.text(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot write " + file, e);
            }
        }
        lastText = corrected.text();
        corrected.apply();
        AdvancedGenetics.LOGGER.debug("Loaded Advanced Genetics config from {}", file);
    }

    /** Reloads the file whenever it is modified, as Forge's config tracker does. */
    static void watch(Path file) {
        ServerLifecycleEvents.SERVER_STARTING.register(started -> server = started);
        ServerLifecycleEvents.SERVER_STOPPED.register(stopped -> server = null);
        try {
            FileWatcher.defaultInstance().addWatch(file, () -> scheduleReload(file));
        } catch (IOException e) {
            AdvancedGenetics.LOGGER.warn("Cannot watch {}; config changes need a restart", file, e);
        }
    }

    /** Runs on the watcher thread: waits for the burst of events from one save to end, then reloads. */
    private static void scheduleReload(Path file) {
        long change = CHANGES.incrementAndGet();
        CompletableFuture.runAsync(() -> {
            if (CHANGES.get() == change) reload(file, FabricConfig::onGameThread);
        }, CompletableFuture.delayedExecutor(RELOAD_DELAY_MS, TimeUnit.MILLISECONDS)).exceptionally(e -> {
            AdvancedGenetics.LOGGER.error("Cannot reload {}", file, e);
            return null;
        });
    }

    /** The server thread when a server runs; otherwise nothing reads the values concurrently. */
    private static void onGameThread(Runnable task) {
        MinecraftServer current = server;
        if (current != null) {
            current.execute(task);
        } else {
            task.run();
        }
    }

    /**
     * Rereads the file after a change. As on Forge, the corrected file is written back; the new values
     * are handed to {@code gameThread}. A file that cannot be read or parsed is left alone, and the
     * game keeps its current values. Returns whether new values were handed over.
     */
    public static synchronized boolean reload(Path file, Executor gameThread) {
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            AdvancedGenetics.LOGGER.warn("Cannot read {}; keeping the current Advanced Genetics settings", file, e);
            return false;
        }
        if (text.equals(lastText)) return false;
        lastText = text;
        Corrected corrected;
        try {
            corrected = correct(text);
        } catch (ParsingException | IllegalStateException e) {
            AdvancedGenetics.LOGGER.warn("Cannot parse {}; keeping the current Advanced Genetics settings", file, e);
            return false;
        }
        if (!corrected.text().equals(text)) {
            try {
                Files.writeString(file, corrected.text(), StandardCharsets.UTF_8);
                lastText = corrected.text();
            } catch (IOException e) {
                AdvancedGenetics.LOGGER.warn("Cannot write the corrected {}", file, e);
            }
        }
        gameThread.execute(corrected::apply);
        AdvancedGenetics.LOGGER.info("Reloaded Advanced Genetics config from {}", file);
        return true;
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

    /** The setting {@code stored} gives the game, checked here so applying it cannot fail. */
    private static <T> T typed(Config.Value<T> value, Object stored) {
        T converted = value.convert(stored);
        if (!value.isValid(converted)) throw new IllegalStateException(value.key() + " = " + stored + " is out of range");
        return converted;
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

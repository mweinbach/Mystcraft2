/*
 * Mekanism 1.19.2 port - GameTest config seeding. Not loaded as a test.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package mekanism.gametest.forge;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import mekanism.additions.common.config.AdditionsClientConfig;
import mekanism.additions.common.config.AdditionsConfig;
import mekanism.common.config.ClientConfig;
import mekanism.common.config.CommonConfig;
import mekanism.common.config.GearConfig;
import mekanism.common.config.GeneralConfig;
import mekanism.common.config.IMekanismConfig;
import mekanism.common.config.StorageConfig;
import mekanism.common.config.TierConfig;
import mekanism.common.config.UsageConfig;
import mekanism.common.config.WorldConfig;
import mekanism.generators.common.config.GeneratorsConfig;
import mekanism.generators.common.config.GeneratorsGearConfig;
import mekanism.generators.common.config.GeneratorsStorageConfig;
import mekanism.tools.common.config.ToolsClientConfig;
import mekanism.tools.common.config.ToolsConfig;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Writes a complete Mekanism config tree before the Forge GameTest server starts.
 *
 * <p>Forge 43.5.0 watches a config as soon as it is loaded, then {@code ForgeConfigSpec}
 * corrects a missing file and autosaves each inserted key. NightConfig reloads that same
 * file on another thread, so a large correction ({@code additions.toml}, {@code world.toml})
 * intermittently parses a torn write ("table declared twice") or throws
 * {@code ConcurrentModificationException}. A file that already matches the spec is not
 * corrected at startup. Seeding does not stop a later {@code ConfigValue.set}: the walkie
 * test writes {@code voiceServerEnabled} and {@code voicePort}, and that autosave still
 * races the watcher. The GameTest server drops the watch after Forge registers it.
 */
public final class WriteGameTestConfigs {

    private WriteGameTestConfigs() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("expected the Mekanism config directory");
        }
        // Specs for tiers, dyes and entity spawns touch vanilla registries. Forge's patched
        // Bootstrap finishes that work, then opens a network channel that needs ModLauncher
        // transforms this plain process does not have. The registries are already filled.
        SharedConstants.tryDetectVersion();
        bootStrapRegistries();
        Path directory = Path.of(args[0]);
        Files.createDirectories(directory);
        // Build each spec directly. The holder classes also construct every sibling, including client
        // configs whose enums touch item registries, and this writer runs outside the game.
        List<Class<? extends IMekanismConfig>> configs = List.of(
              CommonConfig.class,
              GeneralConfig.class,
              GearConfig.class,
              StorageConfig.class,
              TierConfig.class,
              UsageConfig.class,
              WorldConfig.class,
              GeneratorsConfig.class,
              GeneratorsGearConfig.class,
              GeneratorsStorageConfig.class,
              ToolsConfig.class,
              AdditionsConfig.class,
              ClientConfig.class,
              ToolsClientConfig.class,
              AdditionsClientConfig.class
        );
        for (Class<? extends IMekanismConfig> type : configs) {
            try {
                IMekanismConfig config = create(type);
                write(directory.resolve(config.getFileName() + ".toml"), config.getConfigSpec());
            } catch (RuntimeException | IOException exception) {
                Throwable cause = exception;
                while (cause.getCause() != null) {
                    cause = cause.getCause();
                }
                throw new IllegalStateException(type.getName() + ": " + cause, exception);
            }
        }
    }

    private static void bootStrapRegistries() {
        try {
            Bootstrap.bootStrap();
        } catch (Throwable throwable) {
            if (!isUntransformedNetworkEvent(throwable)) {
                if (throwable instanceof RuntimeException runtime) {
                    throw runtime;
                }
                if (throwable instanceof Error error) {
                    throw error;
                }
                throw new IllegalStateException(throwable);
            }
        }
    }

    private static boolean isUntransformedNetworkEvent(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof NoSuchMethodException missing && missing.getMessage() != null
                  && missing.getMessage().startsWith("net.minecraftforge.network.NetworkEvent.")) {
                return true;
            }
        }
        return false;
    }

    private static IMekanismConfig create(Class<? extends IMekanismConfig> type) {
        try {
            Constructor<? extends IMekanismConfig> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("could not build " + type.getSimpleName(), exception);
        }
    }

    private static void write(Path file, ForgeConfigSpec spec) throws IOException {
        Files.deleteIfExists(file);
        try (CommentedFileConfig writing = open(file)) {
            writing.load();
            spec.correct(writing);
            writing.save();
        }
        // The game assigns the loaded file before asking isCorrect. Some ore-anchor validators
        // read a sibling value, which is only legal after that assignment. Startup then saves
        // and the file watcher loads that save; either step rewriting the file means correction
        // still races the watcher.
        byte[] before = Files.readAllBytes(file);
        try (CommentedFileConfig written = open(file)) {
            written.load();
            spec.setConfig(written);
            written.save();
            written.load();
            spec.setConfig(written);
        }
        if (!Arrays.equals(before, Files.readAllBytes(file))) {
            throw new IllegalStateException(file.getFileName() + " does not round-trip");
        }
    }

    private static CommentedFileConfig open(Path file) {
        return CommentedFileConfig.builder(file)
              .sync()
              .preserveInsertionOrder()
              .autosave()
              .onFileNotFound((newFile, format) -> {
                  Files.createDirectories(newFile.getParent());
                  Files.createFile(newFile);
                  format.initEmptyFile(newFile);
                  return true;
              })
              .writingMode(WritingMode.REPLACE)
              .build();
    }
}

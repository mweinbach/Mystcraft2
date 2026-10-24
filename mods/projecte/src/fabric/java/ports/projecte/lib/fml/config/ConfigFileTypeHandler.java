/*
 * ProjectE 1.19.2 port, adapted from the Mekanism 1.19.2 port - Fabric implementation of the Forge API shapes Mekanism uses.
 * Copyright (c) 2026 Mekanism 1.19.2 port contributors. MIT License, see licenses/PORT-LICENSE.txt.
 */
package ports.projecte.lib.fml.config;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

/**
 * Opens a config's TOML file under a base directory, creating the directory and the file as needed.
 */
public class ConfigFileTypeHandler {

    static final ConfigFileTypeHandler TOML = new ConfigFileTypeHandler();

    public Function<ModConfig, CommentedFileConfig> reader(Path configBasePath) {
        return config -> {
            Path configPath = configBasePath.resolve(config.getFileName());
            try {
                Files.createDirectories(configPath.getParent());
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to create the config directory for " + config.getFileName(), e);
            }
            CommentedFileConfig configData = CommentedFileConfig.builder(configPath)
                  .sync()
                  .preserveInsertionOrder()
                  .writingMode(WritingMode.REPLACE)
                  .build();
            configData.load();
            return configData;
        };
    }

    public void unload(Path configBasePath, ModConfig config) {
        if (config.getConfigData() instanceof CommentedFileConfig fileConfig) {
            fileConfig.close();
        }
    }
}
